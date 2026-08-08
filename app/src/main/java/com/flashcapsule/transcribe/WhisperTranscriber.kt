package com.flashcapsule.transcribe

import android.content.Context
import com.flashcapsule.capture.AudioDecoder

/** 端上 Whisper 转写：解码音频 → Whisper → 文字。 */
class WhisperTranscriber(private val context: Context) : Transcriber {
    override suspend fun transcribe(audioPath: String, lang: String?): String {
        val floats = AudioDecoder.decodeTo16kMonoFloat(audioPath)
        if (floats.isEmpty()) return ""
        val whisper = WhisperModel.ensureContext(context)
        return whisper.transcribeData(floats, printTimestamp = false)
            .trim()
            .replace(Regex("\\s+"), " ")
    }
}
