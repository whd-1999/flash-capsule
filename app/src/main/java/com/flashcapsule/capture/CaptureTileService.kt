package com.flashcapsule.capture

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.TileService
import com.flashcapsule.ui.CaptureActivity

/** 快捷设置磁贴：下拉一点即进捕获。后台零成本。 */
class CaptureTileService : TileService() {
    override fun onClick() {
        super.onClick()
        val intent = Intent(this, CaptureActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra(CaptureActivity.EXTRA_VOICE, true) // 磁贴 = 语音优先
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val pi = PendingIntent.getActivity(
                this, 0, intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            startActivityAndCollapse(pi)
        } else {
            @Suppress("DEPRECATION", "StartActivityAndCollapseDeprecated")
            startActivityAndCollapse(intent)
        }
    }
}
