package com.flashcapsule.capture

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import java.io.File

/**
 * 用 MediaRecorder 录音存成 m4a（16k 采样，便于日后 Whisper 转写），
 * 并通过 maxAmplitude 轮询提供实时波形振幅。主线程使用。
 */
class AudioRecorder(private val context: Context) {

    private var recorder: MediaRecorder? = null
    private var output: File? = null

    fun start(file: File) {
        val r = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION") MediaRecorder()
        }
        r.setAudioSource(MediaRecorder.AudioSource.MIC)
        r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
        r.setAudioEncodingBitRate(64000)
        r.setAudioSamplingRate(16000)
        r.setOutputFile(file.absolutePath)
        r.prepare()
        r.start()
        recorder = r
        output = file
    }

    /** 自上次调用以来的最大振幅（0..32767）。 */
    fun amplitude(): Int = try { recorder?.maxAmplitude ?: 0 } catch (e: Exception) { 0 }

    /** 停止并返回文件；异常或过短返回 null。 */
    fun stop(): File? {
        val r = recorder ?: return null
        return try {
            r.stop()
            output
        } catch (e: Exception) {
            output?.delete()
            null
        } finally {
            runCatching { r.release() }
            recorder = null
        }
    }

    fun cancel() {
        val r = recorder ?: return
        runCatching { r.stop() }
        runCatching { r.release() }
        recorder = null
        output?.delete()
        output = null
    }
}
