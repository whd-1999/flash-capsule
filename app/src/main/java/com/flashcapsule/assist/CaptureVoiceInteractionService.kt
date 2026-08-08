package com.flashcapsule.assist

import android.service.voice.VoiceInteractionService

/** 让本 App 可被设为"默认数字助理"，从而接管长按电源/侧边键的助理手势。 */
class CaptureVoiceInteractionService : VoiceInteractionService()
