package com.gtuner.prototype

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioRecordingConfiguration
import android.media.MediaRecorder
import android.net.Uri
import android.os.Process
import android.provider.MediaStore
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.sqrt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

internal data class PitchPoint(val seconds: Float, val hz: Float?)
internal data class TempoPoint(val seconds: Float, val bpm: Int?)
internal data class LiveReadout(
    val seconds: Float = 0f, val tunerHz: Float? = null, val bpm: Int? = null,
    val pitch: List<PitchPoint> = emptyList(), val tempo: List<TempoPoint> = emptyList(),
    val message: String = "", val input: String = ""
)
internal enum class TakePhase { IDLE, STARTING, RECORDING, SAVING }
internal enum class RecordingFormat(val label: String, val extension: String, val mime: String) {
    AAC("AAC", "m4a", "audio/mp4"), WAV("WAV", "wav", "audio/wav")
}
internal data class TakeState(val phase: TakePhase = TakePhase.IDLE, val seconds: Int = 0, val message: String = "")
internal data class SavedRecording(val uri: Uri, val name: String)

internal object LiveAudio {
    private const val RATE = 48_000
    private const val CHUNK = 960
    private const val STANDARD_PITCH_RMS = 0.002
    private const val HIGH_PITCH_RMS = 0.00025
    private val liveState = MutableStateFlow(LiveReadout())
    val live = liveState.asStateFlow()
    private val takeState = MutableStateFlow(TakeState())
    val take = takeState.asStateFlow()
    private val latestRecordingState = MutableStateFlow<SavedRecording?>(null)
    val latestRecording = latestRecordingState.asStateFlow()

    fun forgetRecording(uri: Uri) {
        latestRecordingState.update { if (it?.uri == uri) null else it }
    }
    @Volatile private var wanted = false
    @Volatile private var capture: Thread? = null
    @Volatile private var monitor = false
    @Volatile var highPitchSensitivity = true
    @Volatile private var inputReady = false
    val ready: Boolean get() = inputReady
    private val active = AtomicReference<RecordingTake?>(null)

    @Synchronized fun configure(context: Context, enabled: Boolean, analyze: Boolean) {
        monitor = analyze
        wanted = enabled
        if (!enabled) { inputReady = false; liveState.value = LiveReadout() }
        if (enabled && capture == null) {
            capture = Thread({ captureLoop(context.applicationContext) }, "gitune-mic").also { it.start() }
        }
    }

    fun record(context: Context, format: RecordingFormat) {
        if (!wanted || takeState.value.phase != TakePhase.IDLE) return
        if (!inputReady) {
            takeState.value = TakeState(message = "Microphone is not ready")
            configure(context, true, true)
            return
        }
        takeState.value = TakeState(TakePhase.STARTING)
        val writer = RecordingTake(context.applicationContext, format)
        active.set(writer)
        Thread({ writer.run() }, "gitune-recording").start()
    }

    fun stop(reason: String = "") {
        val writer = active.get() ?: return
        writer.stop(reason)
    }

    @SuppressLint("MissingPermission")
    private fun captureLoop(context: Context) {
        try {
            Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)
            while (wanted) {
                val minimum = AudioRecord.getMinBufferSize(RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
                check(minimum > 0)
                val source = if (context.getSystemService(AudioManager::class.java)
                        .getProperty(AudioManager.PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED) == "true")
                    MediaRecorder.AudioSource.UNPROCESSED else MediaRecorder.AudioSource.DEFAULT
                val recorder = AudioRecord(source, RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
                    maxOf(minimum, CHUNK * 8))
                val queue = ArrayBlockingQueue<Frame>(8)
                val free = ArrayBlockingQueue<Frame>(9).apply { repeat(9) { add(Frame()) } }
                val samples = ShortArray(CHUNK)
                var running = true
                val alive = AtomicBoolean(true)
                val silenced = AtomicBoolean(false)
                val worker = Thread({ analyze(queue, free, alive, silenced) }, "gitune-analysis")
                val silencing = object : AudioManager.AudioRecordingCallback() {
                    override fun onRecordingConfigChanged(configs: MutableList<AudioRecordingConfiguration>) {
                        configs.firstOrNull { it.clientAudioSessionId == recorder.audioSessionId }?.let {
                            if (!alive.get()) return
                            silenced.set(it.isClientSilenced)
                            inputReady = wanted && !it.isClientSilenced
                            if (it.isClientSilenced) stop("Microphone interrupted")
                        }
                    }
                }
                try {
                    check(recorder.state == AudioRecord.STATE_INITIALIZED)
                    recorder.registerAudioRecordingCallback(context.mainExecutor, silencing)
                    liveState.value = LiveReadout()
                    recorder.startRecording()
                    check(recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING)
                    inputReady = wanted && !silenced.get()
                    worker.start()
                    var index = 0L
                    while (wanted) {
                        val count = recorder.read(samples, 0, samples.size, AudioRecord.READ_BLOCKING)
                        if (count <= 0) { running = false; break }
                        if (!wanted) break
                        val end = index + count
                        if (!silenced.get()) {
                            inputReady = true
                            active.get()?.offer(samples, count)
                            if (monitor) {
                                val frame = free.poll() ?: queue.poll()
                                if (frame != null) {
                                    samples.copyInto(frame.samples, endIndex = count)
                                    frame.count = count
                                    frame.end = end
                                    if (!queue.offer(frame)) free.offer(frame)
                                }
                            }
                        } else liveState.value = LiveReadout(message = "Microphone interrupted")
                        index = end
                    }
                } finally {
                    inputReady = false
                    alive.set(false)
                    worker.interrupt()
                    runCatching { recorder.unregisterAudioRecordingCallback(silencing) }
                    runCatching { recorder.stop() }
                    recorder.release()
                    worker.join()
                }
                if (!running) {
                    stop("Microphone interrupted")
                    liveState.value = LiveReadout(message = "Microphone interrupted")
                    if (wanted) Thread.sleep(350)
                }
            }
        } catch (_: Exception) {
            stop("Microphone unavailable")
            liveState.value = LiveReadout(message = "Microphone unavailable")
        } finally {
            synchronized(this) { capture = null }
        }
    }

    private class Frame { val samples = ShortArray(CHUNK); var count = 0; var end = 0L }

    private fun analyze(queue: ArrayBlockingQueue<Frame>, free: ArrayBlockingQueue<Frame>,
                        alive: AtomicBoolean, silenced: AtomicBoolean) {
        val window = FloatArray(800)
        val linear = FloatArray(window.size)
        val detector = PitchDetector(8_000)
        val tunerSmoother = TunerSmoother()
        val points = ArrayDeque<PitchPoint>()
        val tempos = ArrayDeque<TempoPoint>()
        val tempoDetector = TempoDetector()
        var cursor = 0
        var filled = 0
        var last = 0L
        var tick = 0
        try {
            while (alive.get()) {
                val frame = queue.take()
                if (last != frame.end - frame.count) {
                    filled = 0
                    points.add(PitchPoint(last.toFloat() / RATE, null))
                    tempoDetector.reset()
                    tunerSmoother.reset()
                }
                last = frame.end
                val seconds = last.toFloat() / RATE
                var energy = 0.0
                var pcmSum = 0L
                var pcmSquares = 0L
                for (i in 0 until frame.count step 6) {
                    val stop = minOf(i + 6, frame.count)
                    var sum = 0
                    for (j in i until stop) {
                        val sample = frame.samples[j].toInt()
                        sum += sample
                        pcmSquares += sample * sample
                    }
                    pcmSum += sum
                    val value = sum / ((stop - i) * 32768f)
                    energy += value * value
                    window[cursor] = value
                    cursor = (cursor + 1) % window.size
                    filled = minOf(window.size, filled + 1)
                }
                val loudness = sqrt(energy / ((frame.count + 5) / 6))
                // Use the full-band signal for tempo (claps lose energy when downsampled).
                // Subtract the DC component so a bias in the mic doesn't look like sound.
                val mean = pcmSum.toDouble() / frame.count
                val tempoRms = sqrt((pcmSquares.toDouble() / frame.count - mean * mean).coerceAtLeast(0.0)) / 32768.0
                free.offer(frame)
                val bpm = tempoDetector.update(seconds, tempoRms)
                if (tick++ % 2 != 0) continue
                // The YIN confidence check still rejects nonperiodic input. This gate
                // only controls pitch tracking; it doesn't amplify the capture or recording.
                val pitchGate = if (highPitchSensitivity) HIGH_PITCH_RMS else STANDARD_PITCH_RMS
                val hz = if (filled == window.size && loudness > pitchGate) {
                    for (i in linear.indices) linear[i] = window[(cursor + i) % window.size]
                    detector.detect(linear)
                } else null
                val tunerHz = tunerSmoother.update(hz)
                points.add(PitchPoint(seconds, hz))
                while (points.first().seconds < seconds - 12f) points.removeFirst()
                if (tick % 10 == 1) tempos.add(TempoPoint(seconds, bpm))
                while (tempos.isNotEmpty() && tempos.first().seconds < seconds - 30f) tempos.removeFirst()
                if (wanted && monitor && !silenced.get()) liveState.value = LiveReadout(seconds, tunerHz, bpm, points.toList(), tempos.toList())
            }
        } catch (_: InterruptedException) { /* Capture ended. */ }
    }

    private class RecordingTake(val context: Context, val format: RecordingFormat) {
        private val queue = ArrayBlockingQueue<Frame>(75)
        private val free = ArrayBlockingQueue<Frame>(76).apply { repeat(76) { add(Frame()) } }
        @Volatile private var accepting = false
        @Volatile private var stopped = false
        @Volatile private var reason = ""
        private var offered = 0L
        private var written = 0L
        private val maxFrames = 3L * 3600 * RATE

        @Synchronized fun stop(message: String) {
            if (stopped || active.get() !== this) return
            reason = message
            accepting = false
            takeState.value = takeState.value.copy(phase = TakePhase.SAVING)
            stopped = true
        }

        @Synchronized fun offer(samples: ShortArray, count: Int) {
            if (!accepting) return
            val frame = free.poll()
            if (frame == null) { LiveAudio.stop("Recording buffer filled"); return }
            val accepted = minOf(count.toLong(), maxFrames - offered).toInt()
            samples.copyInto(frame.samples, endIndex = accepted)
            frame.count = accepted
            if (accepted > 0 && queue.offer(frame)) {
                offered += accepted
            } else {
                free.offer(frame)
                if (accepted > 0) { LiveAudio.stop("Recording buffer filled"); return }
            }
            if (offered >= maxFrames) LiveAudio.stop("Three-hour limit reached")
        }

        fun run() {
            val resolver = context.contentResolver
            val name = "Gitune ${SimpleDateFormat("yyyy-MM-dd HH-mm-ss-SSS", Locale.ROOT).format(Date())}.${format.extension}"
            var uri: Uri? = null
            var finalized = false
            try {
                uri = resolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, ContentValues().apply {
                    put(MediaStore.Audio.Media.DISPLAY_NAME, name)
                    put(MediaStore.Audio.Media.MIME_TYPE, format.mime)
                    put(MediaStore.Audio.Media.RELATIVE_PATH, "Music/")
                    put(MediaStore.Audio.Media.IS_PENDING, 1)
                }) ?: error("Could not create file")
                resolver.openFileDescriptor(uri, "rw")!!.use { descriptor ->
                    when (format) {
                        RecordingFormat.AAC -> AacWriter.encode(descriptor.fileDescriptor, RATE) { encoder ->
                            drainFrames(encoder::write)
                            if (written > 0) encoder.finish()
                        }
                        RecordingFormat.WAV -> FileOutputStream(descriptor.fileDescriptor).use { output ->
                            output.write(wavHeader(0))
                            val bytes = ByteArray(CHUNK * 2)
                            drainFrames { samples, count ->
                                for (i in 0 until count) {
                                    val value = samples[i].toInt()
                                    bytes[i * 2] = value.toByte()
                                    bytes[i * 2 + 1] = (value shr 8).toByte()
                                }
                                output.write(bytes, 0, count * 2)
                            }
                            output.channel.position(0)
                            output.write(wavHeader((written * 2).toInt()))
                            output.flush()
                        }
                    }
                }
                finalized = true
            } catch (_: Exception) {
                reason = if (format == RecordingFormat.AAC) "Could not save AAC recording" else "Could not save WAV recording"
            } finally {
                accepting = false
                val file = uri
                val saved = file != null && written > 0 && finalized && runCatching {
                    resolver.update(file, ContentValues().apply { put(MediaStore.Audio.Media.IS_PENDING, 0) }, null, null) > 0
                }.getOrDefault(false)
                if (file != null && !saved) runCatching { resolver.delete(file, null, null) }
                synchronized(this) {
                    if (saved) latestRecordingState.value = SavedRecording(file, name)
                    active.compareAndSet(this, null)
                    takeState.value = TakeState(message = if (saved) reason else reason.ifEmpty { "No audio saved" })
                }
            }
        }

        private fun drainFrames(write: (ShortArray, Int) -> Unit) {
            synchronized(this) {
                if (!stopped) { takeState.value = TakeState(TakePhase.RECORDING); accepting = true }
            }
            while (!stopped || queue.isNotEmpty()) {
                val frame = queue.poll(40, java.util.concurrent.TimeUnit.MILLISECONDS) ?: continue
                write(frame.samples, frame.count)
                written += frame.count
                free.offer(frame)
                val seconds = (written / RATE).toInt()
                synchronized(this) {
                    if (!stopped && takeState.value.seconds != seconds) takeState.value = TakeState(TakePhase.RECORDING, seconds)
                }
            }
        }

        private fun wavHeader(bytes: Int) = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray()); putInt(bytes + 36); put("WAVEfmt ".toByteArray())
            putInt(16); putShort(1); putShort(1); putInt(RATE); putInt(RATE * 2)
            putShort(2); putShort(16); put("data".toByteArray()); putInt(bytes)
        }.array()
    }
}
