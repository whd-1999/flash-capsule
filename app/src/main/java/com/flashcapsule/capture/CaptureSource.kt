package com.flashcapsule.capture

import android.content.Intent
import com.flashcapsule.model.ColorTag
import com.flashcapsule.model.RawCapture

/** 输入来源抽象：把平台输入（Intent 等）解析成统一的 RawCapture。 */
interface CaptureSource {
    val id: String
    fun parse(intent: Intent): RawCapture?
}

/** 系统分享目标（"分享到闪念胶囊"）。 */
object ShareCaptureSource : CaptureSource {
    override val id = "share"
    override fun parse(intent: Intent): RawCapture? {
        val text = intent.getStringExtra(Intent.EXTRA_TEXT) ?: return null
        if (text.isBlank()) return null
        return RawCapture(text = text, source = id)
    }
}

/** 公开 Intent API：供 Tasker / 自动化 / adb 直接塞一条胶囊。 */
object IntentApiSource : CaptureSource {
    const val ACTION = "com.flashcapsule.action.CAPTURE"
    override val id = "intent"

    override fun parse(intent: Intent): RawCapture? {
        val text = intent.getStringExtra("text")
        val audio = intent.getStringExtra("audioPath")
        if (text.isNullOrBlank() && audio.isNullOrBlank()) return null
        val color = intent.getStringExtra("colorTag")
            ?.let { runCatching { ColorTag.valueOf(it.uppercase()) }.getOrNull() }
        val tags = intent.getStringArrayExtra("tags")?.toList() ?: emptyList()
        return RawCapture(
            text = text,
            audioPath = audio,
            source = intent.getStringExtra("source") ?: id,
            colorTag = color,
            tags = tags,
        )
    }
}
