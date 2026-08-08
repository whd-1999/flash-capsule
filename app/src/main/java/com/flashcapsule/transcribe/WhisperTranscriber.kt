package com.flashcapsule.transcribe

import android.content.Context
import com.flashcapsule.capture.AudioDecoder

/** 端上 Whisper 转写：解码音频 → Whisper → 文字。 */
class WhisperTranscriber(private val context: Context) : Transcriber {
    override suspend fun transcribe(audioPath: String, lang: String?): String {
        val floats = AudioDecoder.decodeTo16kMonoFloat(audioPath)
        if (floats.isEmpty()) return ""
        val whisper = WhisperModel.ensureContext(context)
        return whisper.transcribeData(floats, printTimestamp = false, language = whisperLang(lang))
            .trim()
            .replace(Regex("\\s+"), " ")
    }

    /** Settings 里的 BCP-47 代码 → whisper 语言代码；空 → 自动检测。 */
    private fun whisperLang(lang: String?): String = when {
        lang.isNullOrBlank() -> "auto"
        lang.startsWith("zh", ignoreCase = true) -> "zh"
        lang.startsWith("ja", ignoreCase = true) -> "ja"
        lang.startsWith("en", ignoreCase = true) -> "en"
        lang.startsWith("ko", ignoreCase = true) -> "ko"
        else -> "auto"
    }
}
