package com.flashcapsule.capture

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.flashcapsule.FlashCapsuleApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 公开广播入口。示例：
 * adb shell am broadcast -a com.flashcapsule.action.CAPTURE --es text "买滤芯" --es source tasker
 */
class IntentApiReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val raw = IntentApiSource.parse(intent) ?: return
        val app = FlashCapsuleApp.from(context)
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                app.repository.ingest(raw)
            } finally {
                pending.finish()
            }
        }
    }
}
