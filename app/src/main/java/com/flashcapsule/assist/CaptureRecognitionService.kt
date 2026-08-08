package com.flashcapsule.assist

import android.content.Intent
import android.speech.RecognitionService

/**
 * 最小占位 RecognitionService —— 仅为满足"数字助理"注册所需的组件声明。
 * 实际语音识别走系统 RecognizerIntent；此处不实现识别逻辑。
 */
class CaptureRecognitionService : RecognitionService() {
    override fun onStartListening(recognizerIntent: Intent?, listener: Callback?) {}
    override fun onCancel(listener: Callback?) {}
    override fun onStopListening(listener: Callback?) {}
}
