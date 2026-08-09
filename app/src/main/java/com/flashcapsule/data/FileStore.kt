package com.flashcapsule.data

import android.content.Context
import android.net.Uri
import java.io.File

/** 文件存储（app 私有目录）：音频 + 附件。 */
class FileStore(context: Context) {
    private val audioDir: File = File(context.filesDir, "audio").apply { mkdirs() }
    private val attachDir: File = File(context.filesDir, "attachments").apply { mkdirs() }
    private val app = context.applicationContext

    fun newAudioFile(id: String): File = File(audioDir, "$id.m4a")

    /** 从 uri 读显示名（无则 null）。 */
    fun queryDisplayName(uri: Uri): String? = runCatching {
        app.contentResolver.query(uri, null, null, null, null)?.use { c ->
            val idx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && c.moveToFirst()) c.getString(idx) else null
        }
    }.getOrNull()

    /** 把任意 uri 拷贝进附件目录，返回私有 file:// uri。 */
    fun copyToAttachments(name: String, src: Uri): Uri? = runCatching {
        val dest = File(attachDir, "${System.currentTimeMillis()}_${name.sanitize()}")
        app.contentResolver.openInputStream(src)?.use { input ->
            dest.outputStream().use { output -> input.copyTo(output) }
        } ?: return null
        Uri.fromFile(dest)
    }.getOrNull()

    private fun String.sanitize() = replace(Regex("[^a-zA-Z0-9._-]"), "_").take(60)
}
