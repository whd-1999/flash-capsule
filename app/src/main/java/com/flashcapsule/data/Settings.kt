package com.flashcapsule.data

import android.content.Context

/** 语音识别语言项。code 为空 = 跟随系统。 */
data class SttLang(val code: String, val label: String)

object Languages {
    val AUTO = SttLang("", "跟随系统")
    val list = listOf(
        AUTO,
        SttLang("zh-CN", "中文（普通话）"),
        SttLang("ja-JP", "日本語"),
        SttLang("en-US", "English (US)"),
        SttLang("zh-TW", "中文（繁體）"),
        SttLang("ko-KR", "한국어"),
    )
    fun byCode(code: String): SttLang = list.firstOrNull { it.code == code } ?: AUTO
}

/** 轻量本地设置（SharedPreferences），记忆用户所选语音语言。 */
class Settings(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var sttLanguage: String
        get() = prefs.getString(KEY_LANG, "") ?: ""
        set(value) { prefs.edit().putString(KEY_LANG, value).apply() }

    companion object {
        private const val KEY_LANG = "stt_language"
    }
}
