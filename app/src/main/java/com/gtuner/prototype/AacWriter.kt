package com.gtuner.prototype

import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.os.SystemClock
import java.io.FileDescriptor
import java.nio.ByteOrder

/** Encodes mono PCM directly to an M4A container on the recording worker. */
internal class AacWriter private constructor(
    private val codec: MediaCodec, private val muxer: MediaMuxer, private val rate: Int,
) {
    private val info = MediaCodec.BufferInfo()
    private var samplesWritten = 0L
    private var track = -1
    private var firstPts: Long? = null

    private fun inputIndex(): Int {
        val deadline = SystemClock.elapsedRealtime() + 3_000
        while (SystemClock.elapsedRealtime() < deadline) {
            drain(0)
            val index = codec.dequeueInputBuffer(10_000)
            if (index >= 0) return index
        }
        error("AAC encoder is not accepting audio")
    }

    fun write(pcm: ShortArray, count: Int) {
        var offset = 0
        while (offset < count) {
            val index = inputIndex()
            val buffer = checkNotNull(codec.getInputBuffer(index)).apply { clear(); order(ByteOrder.LITTLE_ENDIAN) }
            val length = minOf(count - offset, buffer.remaining() / 2)
            check(length > 0)
            buffer.asShortBuffer().put(pcm, offset, length)
            codec.queueInputBuffer(index, 0, length * 2, samplesWritten * 1_000_000 / rate, 0)
            samplesWritten += length
            offset += length
        }
        drain(0)
    }

    fun finish() {
        codec.queueInputBuffer(inputIndex(), 0, 0, samplesWritten * 1_000_000 / rate, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
        val deadline = SystemClock.elapsedRealtime() + 5_000
        while (!drain(10_000)) {
            check(SystemClock.elapsedRealtime() < deadline) { "AAC encoder did not finish" }
        }
        check(firstPts != null) { "AAC encoder produced no audio" }
        muxer.stop()
    }

    private fun drain(timeoutUs: Long): Boolean {
        while (true) {
            val index = codec.dequeueOutputBuffer(info, timeoutUs)
            when {
                index == MediaCodec.INFO_TRY_AGAIN_LATER -> return false
                index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    check(track == -1)
                    track = muxer.addTrack(codec.outputFormat)
                    muxer.start()
                }
                index >= 0 -> {
                    val eos = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                    try {
                        if (info.size > 0 && info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0) {
                            check(track >= 0)
                            val data = checkNotNull(codec.getOutputBuffer(index))
                            val origin = firstPts ?: info.presentationTimeUs.also { firstPts = it }
                            info.presentationTimeUs -= origin
                            muxer.writeSampleData(track, data, info)
                        }
                    } finally {
                        codec.releaseOutputBuffer(index, false)
                    }
                    if (eos) return true
                }
            }
        }
    }

    companion object {
        fun encode(fd: FileDescriptor, rate: Int, block: (AacWriter) -> Unit) {
            val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
            try {
                codec.configure(MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, rate, 1).apply {
                    setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
                    setInteger(MediaFormat.KEY_BIT_RATE, 128_000)
                    setInteger(MediaFormat.KEY_PCM_ENCODING, AudioFormat.ENCODING_PCM_16BIT)
                }, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                codec.start()
                val muxer = MediaMuxer(fd, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
                try {
                    block(AacWriter(codec, muxer, rate))
                } finally {
                    muxer.release()
                }
            } finally {
                codec.release()
            }
        }
    }
}
