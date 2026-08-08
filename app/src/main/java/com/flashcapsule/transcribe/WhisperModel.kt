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
 * 模型：base 多语种 q5_1（~57MB，中日英还行）。
 * 选 base 而非 small：转写约快 3 倍，手机 CPU 上体验远好于 small，对"随手记"足够；
 * 若之后需要更高准确率可再切回 ggml-small-q5_1.bin。
 */
object WhisperModel {
    private const val MODEL_NAME = "ggml-base-q5_1.bin"
    private const val MODEL_URL =
        "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-base-q5_1.bin"

    private val lock = Mutex()
    private var context: WhisperContext? = null

    fun modelFile(ctx: Context): File = File(ctx.filesDir, MODEL_NAME)
    fun isDownloaded(ctx: Context): Boolean = modelFile(ctx).exists()

    /** 删掉历史版本的旧模型文件（升级换模型时释放空间）。 */
    fun cleanupLegacyModels(ctx: Context) {
        val old = File(ctx.filesDir, "ggml-small-q5_1.bin")
        if (old.exists()) old.delete()
    }

    suspend fun ensureContext(ctx: Context, onProgress: (Int) -> Unit = {}): WhisperContext =
        lock.withLock {
            context?.let { return it }
            cleanupLegacyModels(ctx)
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
