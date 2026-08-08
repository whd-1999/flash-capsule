package com.flashcapsule.transcribe

/** 转写后端抽象。可换：系统 STT / whisper.cpp（v2）/ 云端。 */
interface Transcriber {
    suspend fun transcribe(audioPath: String, lang: String? = null): String
}

/**
 * v1 占位实现：语音输入走 RecognizerIntent 直接返回文字，暂不需要音频→文字。
 * v2 在此接入 whisper.cpp 或 SpeechRecognizer。
 */
class NoopTranscriber : Transcriber {
    override suspend fun transcribe(audioPath: String, lang: String?): String = ""
}
