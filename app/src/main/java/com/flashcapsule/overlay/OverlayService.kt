package com.flashcapsule.overlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import com.flashcapsule.ui.PanelActivity
import kotlin.math.abs

/**
 * 侧边把手：在屏幕右缘常驻一个半透明小条。
 * 点 → 拉出边缘面板（PanelActivity）；上下拖 → 调整高度。
 * 极低优先级前台服务，保证跨应用可见。
 */
class OverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private var handle: View? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        goForeground()
        addHandle()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    private fun goForeground() {
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "侧边把手", NotificationManager.IMPORTANCE_MIN)
            )
        }
        val notif: Notification = NotificationCompat.Builder(this, CHANNEL)
            .setContentTitle("闪念胶囊")
            .setContentText("侧边把手已开启")
            .setSmallIcon(android.R.drawable.ic_menu_edit)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIF_ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIF_ID, notif)
        }
    }

    private fun addHandle() {
        windowManager = getSystemService(WindowManager::class.java)
        val view = View(this).apply {
            background = GradientDrawable().apply {
                cornerRadii = floatArrayOf(dp(6f), dp(6f), 0f, 0f, 0f, 0f, dp(6f), dp(6f))
                setColor(0xCC6C4AB6.toInt())
            }
        }
        val lp = WindowManager.LayoutParams(
            dp(7f).toInt(),
            dp(72f).toInt(),
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
        }
        view.setOnTouchListener(dragAndTap(lp))
        windowManager.addView(view, lp)
        handle = view
    }

    private fun dragAndTap(lp: WindowManager.LayoutParams): View.OnTouchListener {
        var startRawY = 0f
        var startY = 0
        var moved = false
        return View.OnTouchListener { view, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    startRawY = e.rawY; startY = lp.y; moved = false; true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dy = (e.rawY - startRawY).toInt()
                    if (abs(dy) > dp(8f)) moved = true
                    lp.y = startY + dy
                    windowManager.updateViewLayout(view, lp); true
                }
                MotionEvent.ACTION_UP -> {
                    if (!moved) openPanel()
                    true
                }
                else -> false
            }
        }
    }

    private fun openPanel() {
        startActivity(
            Intent(this, PanelActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    override fun onDestroy() {
        handle?.let { runCatching { windowManager.removeView(it) } }
        handle = null
        super.onDestroy()
    }

    private fun dp(v: Float): Float = resources.displayMetrics.density * v

    private fun overlayType(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

    companion object {
        private const val CHANNEL = "overlay"
        private const val NOTIF_ID = 1

        fun start(context: Context) {
            val i = Intent(context, OverlayService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(i)
            } else {
                context.startService(i)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, OverlayService::class.java))
        }
    }
}
