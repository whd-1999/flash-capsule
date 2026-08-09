package com.flashcapsule.sink

import android.content.Context
import android.net.Uri
import com.flashcapsule.data.Settings
import com.flashcapsule.model.Capsule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 把胶囊写成 .md 落进导出目录，供 Obsidian（手机端/同步）拉取。
 * 优先用 SAF 选择的 vault 目录（[Settings.vaultUri]，用户任意指定，可持久化）；
 * 未选择时回退到 app 外部目录 /Android/data/com.flashcapsule/files/obsidian_export/。
 */
class ObsidianSink(
    context: Context,
    private val settings: Settings,
    override val auto: Boolean = false,
) : Sink {
    override val id = "obsidian"
    override val displayName = "Obsidian Vault"

    private val app = context.applicationContext
    private val fallbackDir: File =
        File(app.getExternalFilesDir(null), "obsidian_export").apply { mkdirs() }

    override suspend fun export(capsule: Capsule): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val stamp = SimpleDateFormat("yyyy-MM-dd_HHmmss", Locale.US).format(Date(capsule.createdAt))
            val content = buildString {
                appendLine("---")
                appendLine("created: ${iso(capsule.createdAt)}")
                appendLine("source: ${capsule.source}")
                if (capsule.tags.isNotEmpty()) appendLine("tags: [${capsule.tags.joinToString(", ")}]")
                capsule.colorTag?.let { appendLine("color: ${it.name.lowercase()}") }
                appendLine("---")
                appendLine()
                appendLine(capsule.text)
            }
            val vaultUri = settings.vaultUri
            if (vaultUri.isBlank()) {
                // 回退：app 私有外部目录
                File(fallbackDir, "capsule_$stamp.md").writeText(content)
            } else {
                val dirUri = Uri.parse(vaultUri)
                val doc = findOrCreateDoc(dirUri, "capsule_$stamp.md")
                app.contentResolver.openOutputStream(doc, "wt")?.use { it.write(content.toByteArray()) }
                    ?: throw IllegalStateException("无法写入 $doc")
            }
            Unit
        }
    }

    /** 在 SAF 目录里找文件，不存在则创建（通过 DocumentsContract 的 createDocument）。 */
    private fun findOrCreateDoc(dirUri: Uri, name: String): Uri {
        val existing = app.contentResolver.query(
            dirUri,
            arrayOf(
                android.provider.DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                android.provider.DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            ),
            null, null, null,
        )?.use { c ->
            while (c.moveToNext()) {
                val display = c.getString(c.getColumnIndexOrThrow(
                    android.provider.DocumentsContract.Document.COLUMN_DISPLAY_NAME))
                if (display == name) {
                    return@use c.getString(c.getColumnIndexOrThrow(
                        android.provider.DocumentsContract.Document.COLUMN_DOCUMENT_ID))
                }
            }
            null
        }
        if (existing != null) {
            return android.provider.DocumentsContract.buildDocumentUriUsingTree(dirUri, existing)
        }
        val created = android.provider.DocumentsContract.createDocument(
            app.contentResolver,
            dirUri,
            "text/markdown",
            name,
        ) ?: throw IllegalStateException("无法创建文件 $name")
        return created
    }

    private fun iso(t: Long) =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).format(Date(t))
}
