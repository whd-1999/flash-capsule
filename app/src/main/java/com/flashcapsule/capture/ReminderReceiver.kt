package com.flashcapsule.capture

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.flashcapsule.FlashCapsuleApp
import kotlinx.coroutines.runBlocking

/** 胶囊提醒：到点发通知，点开进主界面。 */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra(EXTRA_ID) ?: return
        val title = intent.getStringExtra(EXTRA_TITLE) ?: "闪念胶囊"
        val text = runBlocking {
            FlashCapsuleApp.from(context).repository.let { repo ->
                repo.searchById(id)?.text
            }
        } ?: ""
        val nm = context.getSystemService(NotificationManager::class.java)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "胶囊提醒", NotificationManager.IMPORTANCE_HIGH)
            )
        }
        val openApp = PendingIntent.getActivity(
            context, 0,
            Intent(context, com.flashcapsule.ui.MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notif = NotificationCompat.Builder(context, CHANNEL)
            .setContentTitle(title)
            .setContentText(text.ifBlank { "记着这件事" })
            .setSmallIcon(android.R.drawable.ic_menu_edit)
            .setAutoCancel(true)
            .setContentIntent(openApp)
            .build()
        nm.notify(id.hashCode(), notif)
    }

    companion object {
        const val CHANNEL = "capsule_reminder"
        const val EXTRA_ID = "capsule_id"
        const val EXTRA_TITLE = "capsule_title"
    }
}
