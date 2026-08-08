package com.flashcapsule.capture

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

/**
 * 直接收音（不跳系统语音界面）：封装 SpeechRecognizer，在后台开麦识别，
 * 通过回调返回实时/最终文字。需 RECORD_AUDIO 运行时权限。必须在主线程使用。
 */
class SpeechCapture(private val context: Context) {

    private var recognizer: SpeechRecognizer? = null

    val available: Boolean get() = SpeechRecognizer.isRecognitionAvailable(context)

    fun start(
        langCode: String,
        onPartial: (String) -> Unit,
        onFinal: (String) -> Unit,
        onError: (String) -> Unit,
    ) {
        if (!available) { onError("设备不支持语音识别"); return }
        destroy()
        val sr = SpeechRecognizer.createSpeechRecognizer(context)
        recognizer = sr
        sr.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
            override fun onPartialResults(partialResults: Bundle?) {
                partialResults
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                    ?.let(onPartial)
            }
            override fun onResults(results: Bundle?) {
                val text = results
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                    .orEmpty()
                onFinal(text)
                destroy()
            }
            override fun onError(error: Int) {
                onError(errText(error))
                destroy()
            }
        })
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            if (langCode.isNotBlank()) {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, langCode)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, langCode)
            }
        }
        sr.startListening(intent)
    }

    fun stop() { recognizer?.stopListening() }

    fun cancel() {
        recognizer?.cancel()
        destroy()
    }

    fun destroy() {
        recognizer?.destroy()
        recognizer = null
    }

    private fun errText(code: Int): String = when (code) {
        SpeechRecognizer.ERROR_NO_MATCH -> "没听清，再说一遍"
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "没听到说话"
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "缺麦克风权限"
        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "网络问题"
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "识别器忙，稍后再试"
        else -> "识别失败（$code）"
    }
}
