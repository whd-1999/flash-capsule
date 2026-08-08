package com.flashcapsule.capture

import android.content.Intent
import android.speech.RecognizerIntent
import java.util.Locale

/** 按所选语言构造系统语音识别 Intent。langCode 为空 = 跟随系统。 */
fun speechIntent(langCode: String): Intent =
    Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(
            RecognizerIntent.EXTRA_LANGUAGE_MODEL,
            RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
        )
        if (langCode.isNotBlank()) {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, langCode)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, langCode)
            putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, langCode)
        } else {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
        }
    }

/** 从语音识别结果 Intent 中取出第一条文本。 */
fun Intent?.firstSpeechResult(): String? =
    this?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
