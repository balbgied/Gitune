package com.gtuner.prototype

import android.content.ContentValues
import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.MediaStore
import java.io.FileNotFoundException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive

internal object ReviewAudio {
    /** Use only the exact document originally selected through Android's file picker. */
    fun deleteOriginal(context: Context, uri: Uri): Boolean = try {
        DocumentsContract.deleteDocument(context.contentResolver, uri)
    } catch (_: FileNotFoundException) {
        true // It has already been removed.
    } catch (_: Exception) {
        false
    }

    /** Reuse an existing Music copy so reopening a file keeps the same bookmark key. */
    fun savedCopy(context: Context, uri: Uri?): Uri? {
        if (uri == null) return null
        return runCatching { context.contentResolver.openFileDescriptor(uri, "r")?.use { uri } }.getOrNull()
    }

    suspend fun copy(context: Context, source: Uri, name: String, progress: (Long) -> Unit): Uri {
        val resolver = context.contentResolver
        val mime = when (name.substringAfterLast('.').lowercase()) {
            "mp3" -> "audio/mpeg"
            "flac" -> "audio/flac"
            "wav" -> "audio/wav"
            "m4a", "mp4" -> "audio/mp4"
            "aac", "adts" -> "audio/aac"
            else -> error("Choose an AAC (.m4a or .aac), MP3, FLAC, or WAV file.")
        }
        val destination = resolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, name.substringAfterLast('/'))
            put(MediaStore.Audio.Media.MIME_TYPE, mime)
            put(MediaStore.Audio.Media.RELATIVE_PATH, "Music/")
            put(MediaStore.Audio.Media.IS_PENDING, 1)
        }) ?: error("Could not create the Music copy.")
        var published = false
        try {
            resolver.openInputStream(source)!!.use { input ->
                resolver.openOutputStream(destination, "w")!!.use { output ->
                    val buffer = ByteArray(128 * 1024)
                    var bytes = 0L
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        bytes += count
                        if (bytes > 2L * 1024 * 1024 * 1024) error("This file exceeds the 2 GiB import limit.")
                        output.write(buffer, 0, count)
                        progress(bytes)
                    }
                }
            }
            val extractor = MediaExtractor()
            try {
                extractor.setDataSource(context, destination, null)
                audioTrack(extractor)
            } finally { extractor.release() }
            currentCoroutineContext().ensureActive()
            check(resolver.update(destination, ContentValues().apply { put(MediaStore.Audio.Media.IS_PENDING, 0) }, null, null) > 0)
            published = true
            return destination
        } finally {
            if (!published) runCatching { resolver.delete(destination, null, null) }
        }
    }

    private fun audioTrack(extractor: MediaExtractor): Int = (0 until extractor.trackCount).firstOrNull {
        extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
    } ?: error("No supported audio track was found.")

    /** Decode independently of the audio output device, pacing analysis against the player clock. */
    suspend fun plot(context: Context, uri: Uri, start: Float, end: Float?, playhead: () -> Float,
                     publish: (List<PitchPoint>, List<TempoPoint>) -> Unit) {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(context, uri, null)
            val track = audioTrack(extractor)
            val format = extractor.getTrackFormat(track)
            extractor.selectTrack(track)
            extractor.seekTo((start * 1_000_000).toLong(), MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
            val pcm = PcmPlot(start)
            suspend fun consume(buffer: ByteBuffer, timestamp: Long, output: MediaFormat): Boolean {
                val seconds = timestamp / 1_000_000f
                if (end != null && seconds > end) return false
                if (end == null) while (seconds > playhead() + 0.20f) delay(30)
                val (pitch, tempo) = pcm.read(buffer.order(ByteOrder.LITTLE_ENDIAN), seconds, output, end)
                if (pitch.isNotEmpty() || tempo.isNotEmpty()) publish(pitch, tempo)
                return true
            }
            val mime = format.getString(MediaFormat.KEY_MIME) ?: error("Missing audio format")
            if (mime == "audio/raw") {
                val bytes = ByteBuffer.allocate(1024 * 1024)
                while (true) {
                    currentCoroutineContext().ensureActive()
                    bytes.clear()
                    val size = extractor.readSampleData(bytes, 0)
                    if (size < 0) break
                    bytes.position(0).limit(size)
                    if (!consume(bytes, extractor.sampleTime, format)) break
                    extractor.advance()
                }
            } else {
                val decoder = MediaCodec.createDecoderByType(mime)
                codec = decoder
                decoder.configure(format, null, null, 0)
                decoder.start()
                val info = MediaCodec.BufferInfo()
                var inputDone = false
                while (true) {
                    currentCoroutineContext().ensureActive()
                    if (!inputDone) {
                        val input = decoder.dequeueInputBuffer(0)
                        if (input >= 0) {
                            val bytes = decoder.getInputBuffer(input)!!.apply { clear() }
                            val size = extractor.readSampleData(bytes, 0)
                            if (size < 0) {
                                decoder.queueInputBuffer(input, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                inputDone = true
                            } else {
                                decoder.queueInputBuffer(input, 0, size, extractor.sampleTime, 0)
                                extractor.advance()
                            }
                        }
                    }
                    val index = decoder.dequeueOutputBuffer(info, 10_000)
                    if (index >= 0) {
                        var more = true
                        if (info.size > 0 && info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0) {
                            val bytes = decoder.getOutputBuffer(index)!!
                            bytes.limit(info.offset + info.size).position(info.offset)
                            more = consume(bytes, info.presentationTimeUs, decoder.outputFormat)
                        }
                        decoder.releaseOutputBuffer(index, false)
                        if (!more || info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
                    }
                }
            }
        } finally {
            try { codec?.release() } finally { extractor.release() }
        }
    }

    private class PcmPlot(private val start: Float) {
        private val samples = FloatArray(800)
        private val window = FloatArray(800)
        private val detector = PitchDetector(8000)
        private var cursor = 0
        private var count = 0
        private var accumulator = 0
        private var sum = 0f
        private var frames = 0
        private val tempoDetector = TempoDetector()
        private var tempoFrames = 0
        private var tempoSum = 0.0
        private var tempoSquares = 0.0

        fun read(bytes: ByteBuffer, seconds: Float, format: MediaFormat, end: Float?): Pair<List<PitchPoint>, List<TempoPoint>> {
            val rate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            val channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            require(rate >= 8000 && channels in 1..32)
            val encoding = if (format.containsKey(MediaFormat.KEY_PCM_ENCODING))
                format.getInteger(MediaFormat.KEY_PCM_ENCODING) else AudioFormat.ENCODING_PCM_16BIT
            val width = when (encoding) {
                AudioFormat.ENCODING_PCM_8BIT -> 1
                AudioFormat.ENCODING_PCM_16BIT -> 2
                AudioFormat.ENCODING_PCM_24BIT_PACKED -> 3
                AudioFormat.ENCODING_PCM_FLOAT, AudioFormat.ENCODING_PCM_32BIT -> 4
                else -> error("Unsupported decoded PCM format")
            }
            val result = ArrayList<PitchPoint>()
            val tempos = ArrayList<TempoPoint>()
            var i = 0
            while (bytes.remaining() >= width * channels) {
                var sample = 0f
                repeat(channels) {
                    sample += when (encoding) {
                        AudioFormat.ENCODING_PCM_8BIT -> ((bytes.get().toInt() and 255) - 128) / 128f
                        AudioFormat.ENCODING_PCM_16BIT -> bytes.short / 32768f
                        AudioFormat.ENCODING_PCM_24BIT_PACKED -> ((bytes.get().toInt() and 255) or
                            ((bytes.get().toInt() and 255) shl 8) or (bytes.get().toInt() shl 16)) / 8388608f
                        AudioFormat.ENCODING_PCM_32BIT -> bytes.int / 2147483648f
                        else -> bytes.float
                    }
                }
                val time = seconds + i++.toFloat() / rate
                if (time < start) continue
                if (end != null && time > end) break
                val mono = sample / channels
                tempoSum += mono
                tempoSquares += mono.toDouble() * mono
                if (++tempoFrames >= rate / 50) {
                    val mean = tempoSum / tempoFrames
                    val rms = sqrt((tempoSquares / tempoFrames - mean * mean).coerceAtLeast(0.0))
                    tempos.add(TempoPoint(time, tempoDetector.update(time, rms)))
                    tempoFrames = 0
                    tempoSum = 0.0
                    tempoSquares = 0.0
                }
                sum += mono
                frames++
                accumulator += 8000
                if (accumulator < rate) continue
                accumulator -= rate
                samples[cursor] = sum / frames
                sum = 0f
                frames = 0
                cursor = (cursor + 1) % samples.size
                count++
                if (count < samples.size || count % 400 != 0) continue
                var power = 0f
                for (j in window.indices) {
                    val value = samples[(cursor + j) % samples.size]
                    window[j] = value
                    power += value * value
                }
                result.add(PitchPoint(time, if (power / window.size > 0.000004f) detector.detect(window) else null))
            }
            return result to tempos
        }
    }
}
