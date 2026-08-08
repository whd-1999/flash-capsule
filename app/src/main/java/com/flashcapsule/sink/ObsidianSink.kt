package com.flashcapsule.sink

import android.content.Context
import com.flashcapsule.model.Capsule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 把胶囊写成 .md 落进导出目录，供 Obsidian（手机端/同步）拉取。
 * v1 写到 app 外部文件目录 /Android/data/com.flashcapsule/files/obsidian_export/，
 * v2 改为 SAF 选择的 vault 目录（用户任意指定）。
 */
class ObsidianSink(
    context: Context,
    override val auto: Boolean = false,
) : Sink {
    override val id = "obsidian"
    override val displayName = "Obsidian Vault"

    private val dir: File =
        File(context.getExternalFilesDir(null), "obsidian_export").apply { mkdirs() }

    override suspend fun export(capsule: Capsule): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val stamp = SimpleDateFormat("yyyy-MM-dd_HHmmss", Locale.US).format(Date(capsule.createdAt))
            val file = File(dir, "capsule_$stamp.md")
            file.writeText(buildString {
                appendLine("---")
                appendLine("created: ${iso(capsule.createdAt)}")
                appendLine("source: ${capsule.source}")
                if (capsule.tags.isNotEmpty()) appendLine("tags: [${capsule.tags.joinToString(", ")}]")
                capsule.colorTag?.let { appendLine("color: ${it.name.lowercase()}") }
                appendLine("---")
                appendLine()
                appendLine(capsule.text)
            })
            Unit
        }
    }

    private fun iso(t: Long) =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).format(Date(t))
}
