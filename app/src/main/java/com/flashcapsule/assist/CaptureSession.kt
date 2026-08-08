package com.flashcapsule.assist

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.service.voice.VoiceInteractionSession
import com.flashcapsule.ui.CaptureActivity

/** 助理手势触发时，直接拉起语音捕获（voice-first），随即隐藏会话。 */
class CaptureSession(context: Context) : VoiceInteractionSession(context) {
    override fun onShow(args: Bundle?, showFlags: Int) {
        super.onShow(args, showFlags)
        val intent = Intent(context, CaptureActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra(CaptureActivity.EXTRA_VOICE, true)
        }
        startAssistantActivity(intent)
        hide()
    }
}
