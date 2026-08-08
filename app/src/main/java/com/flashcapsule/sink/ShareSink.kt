package com.flashcapsule.sink

import android.content.Context
import android.content.Intent
import com.flashcapsule.model.Capsule

/** 把胶囊通过系统分享菜单发出去。 */
class ShareSink(private val context: Context) : Sink {
    override val id = "share"
    override val displayName = "Share…"

    override suspend fun export(capsule: Capsule): Result<Unit> = runCatching {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, capsule.text)
        }
        val chooser = Intent.createChooser(send, "Share capsule").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }
}
