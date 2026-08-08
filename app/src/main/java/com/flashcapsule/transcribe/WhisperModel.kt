package com.flashcapsule.transcribe

import android.content.Context
import com.whispercpp.whisper.WhisperContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * 管理端上 Whisper 模型：首次使用时下载 ggml 模型到私有目录，缓存 WhisperContext。
 * 模型：small 多语种 q5_1（~190MB，中日英都还行）。
 */
object WhisperModel {
    private const val MODEL_NAME = "ggml-small-q5_1.bin"
    private const val MODEL_URL =
        "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-small-q5_1.bin"

    private val lock = Mutex()
    private var context: WhisperContext? = null

    fun modelFile(ctx: Context): File = File(ctx.filesDir, MODEL_NAME)
    fun isDownloaded(ctx: Context): Boolean = modelFile(ctx).exists()

    suspend fun ensureContext(ctx: Context, onProgress: (Int) -> Unit = {}): WhisperContext =
        lock.withLock {
            context?.let { return it }
            val f = modelFile(ctx)
            if (!f.exists()) download(f, onProgress)
            val c = WhisperContext.createContextFromFile(f.absolutePath)
            context = c
            c
        }

    private fun download(dest: File, onProgress: (Int) -> Unit) {
        val tmp = File(dest.absolutePath + ".part")
        TranscriptionState.modelDownload.value = 0
        try {
            val conn = (URL(MODEL_URL).openConnection() as HttpURLConnection).apply {
                connectTimeout = 30_000
                readTimeout = 60_000
                instanceFollowRedirects = true
            }
            conn.connect()
            val total = conn.contentLengthLong
            conn.inputStream.use { input ->
                tmp.outputStream().use { output ->
                    val buf = ByteArray(1 shl 16)
                    var read = 0L
                    var n: Int
                    while (input.read(buf).also { n = it } >= 0) {
                        output.write(buf, 0, n)
                        read += n
                        if (total > 0) {
                            val pct = ((read * 100) / total).toInt()
                            onProgress(pct)
                            TranscriptionState.modelDownload.value = pct
                        }
                    }
                }
            }
            conn.disconnect()
            if (!tmp.renameTo(dest)) {
                tmp.copyTo(dest, overwrite = true); tmp.delete()
            }
        } finally {
            TranscriptionState.modelDownload.value = null
        }
    }
}
