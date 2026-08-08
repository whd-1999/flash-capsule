package com.flashcapsule.capture

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** 把音频文件解码成 16kHz 单声道 float PCM（whisper 输入格式）。失败返回空数组。 */
object AudioDecoder {

    fun decodeTo16kMonoFloat(path: String): FloatArray {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(path)
            var track = -1
            var format: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val f = extractor.getTrackFormat(i)
                val mime = f.getString(MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("audio/")) { track = i; format = f; break }
            }
            if (track < 0 || format == null) return FloatArray(0)
            extractor.selectTrack(track)

            val srcRate = if (format.containsKey(MediaFormat.KEY_SAMPLE_RATE))
                format.getInteger(MediaFormat.KEY_SAMPLE_RATE) else 16000
            val channels = if (format.containsKey(MediaFormat.KEY_CHANNEL_COUNT))
                format.getInteger(MediaFormat.KEY_CHANNEL_COUNT) else 1
            val mime = format.getString(MediaFormat.KEY_MIME)!!

            val codec = MediaCodec.createDecoderByType(mime)
            codec.configure(format, null, null, 0)
            codec.start()

            val pcm = ByteArrayOutputStream()
            val info = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false
            while (!outputDone) {
                if (!inputDone) {
                    val inIdx = codec.dequeueInputBuffer(10_000)
                    if (inIdx >= 0) {
                        val inBuf = codec.getInputBuffer(inIdx)!!
                        val sz = extractor.readSampleData(inBuf, 0)
                        if (sz < 0) {
                            codec.queueInputBuffer(inIdx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            codec.queueInputBuffer(inIdx, 0, sz, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }
                val outIdx = codec.dequeueOutputBuffer(info, 10_000)
                if (outIdx >= 0) {
                    val outBuf = codec.getOutputBuffer(outIdx)!!
                    if (info.size > 0) {
                        val chunk = ByteArray(info.size)
                        outBuf.position(info.offset)
                        outBuf.get(chunk, 0, info.size)
                        pcm.write(chunk)
                    }
                    outBuf.clear()
                    codec.releaseOutputBuffer(outIdx, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                }
            }
            codec.stop(); codec.release()

            val bytes = pcm.toByteArray()
            if (bytes.size < 2) return FloatArray(0)
            val shorts = ShortArray(bytes.size / 2)
            ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(shorts)

            val mono: FloatArray = if (channels <= 1) {
                FloatArray(shorts.size) { shorts[it] / 32768f }
            } else {
                val n = shorts.size / channels
                FloatArray(n) { i ->
                    var s = 0f
                    for (c in 0 until channels) s += shorts[i * channels + c]
                    (s / channels) / 32768f
                }
            }
            return if (srcRate == 16000) mono else resample(mono, srcRate, 16000)
        } catch (e: Exception) {
            return FloatArray(0)
        } finally {
            runCatching { extractor.release() }
        }
    }

    private fun resample(input: FloatArray, from: Int, to: Int): FloatArray {
        if (input.isEmpty() || from == to) return input
        val ratio = to.toDouble() / from
        val outN = (input.size * ratio).toInt()
        val out = FloatArray(outN)
        for (i in 0 until outN) {
            val srcPos = i / ratio
            val i0 = srcPos.toInt()
            val i1 = (i0 + 1).coerceAtMost(input.size - 1)
            val frac = (srcPos - i0).toFloat()
            out[i] = input[i0] * (1 - frac) + input[i1] * frac
        }
        return out
    }
}
