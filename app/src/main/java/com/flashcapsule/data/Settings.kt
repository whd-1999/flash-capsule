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

    /** 侧边把手是否开启（用户意愿；实际是否显示还取决于悬浮窗权限）。 */
    var overlayEnabled: Boolean
        get() = prefs.getBoolean(KEY_OVERLAY, false)
        set(value) { prefs.edit().putBoolean(KEY_OVERLAY, value).apply() }

    /** 把手竖直位置偏移（相对屏幕中心），记忆用户拖动后的位置。 */
    var handleY: Int
        get() = prefs.getInt(KEY_HANDLE_Y, 0)
        set(value) { prefs.edit().putInt(KEY_HANDLE_Y, value).apply() }

    /** DeepSeek API Key（用户自己填，不硬编码进 APK）。空 = 不启用 AI 标题/分类。 */
    var apiKey: String
        get() = prefs.getString(KEY_API_KEY, "") ?: ""
        set(value) { prefs.edit().putString(KEY_API_KEY, value).apply() }

    /** 上次回收站清理时间戳，用于每日节流。 */
    var lastTrashPurgeAt: Long
        get() = prefs.getLong(KEY_LAST_PURGE, 0L)
        set(value) { prefs.edit().putLong(KEY_LAST_PURGE, value).apply() }

    companion object {
        private const val KEY_LANG = "stt_language"
        private const val KEY_OVERLAY = "overlay_enabled"
        private const val KEY_HANDLE_Y = "handle_y"
        private const val KEY_API_KEY = "api_key"
        private const val KEY_LAST_PURGE = "last_trash_purge"
    }
}
