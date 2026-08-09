package com.flashcapsule.overlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import com.flashcapsule.FlashCapsuleApp
import kotlin.math.abs

/**
 * 侧边把手：在屏幕右缘常驻一个半透明小条。
 * 点 → 拉出边缘面板（PanelActivity）；上下拖 → 调整高度。
 * 极低优先级前台服务，保证跨应用可见。
 */
class OverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private var handle: View? = null
    private var panel: OverlayPanel? = null

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
        val left = FlashCapsuleApp.from(this@OverlayService).settings.handleLeft
        val view = View(this).apply {
            background = GradientDrawable().apply {
                // 右缘：左圆右方；左缘：右圆左方
                cornerRadii = if (left) {
                    floatArrayOf(0f, 0f, dp(14f), dp(14f), dp(14f), dp(14f), 0f, 0f)
                } else {
                    floatArrayOf(dp(14f), dp(14f), 0f, 0f, 0f, 0f, dp(14f), dp(14f))
                }
                setColor(0xF06C4AB6.toInt())
            }
        }
        val lp = WindowManager.LayoutParams(
            dp(26f).toInt(),
            dp(112f).toInt(),
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = (if (left) Gravity.START else Gravity.END) or Gravity.CENTER_VERTICAL
            x = dp(2f).toInt() // 略往里挪，避开最边缘的返回手势区
            y = FlashCapsuleApp.from(this@OverlayService).settings.handleY // 恢复上次位置
        }
        view.setOnTouchListener(dragAndTap(lp))
        windowManager.addView(view, lp)
        handle = view
        // 把把手区域排除出系统返回/侧滑手势，让触摸落到把手上
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            view.post {
                runCatching {
                    view.systemGestureExclusionRects =
                        listOf(Rect(0, 0, view.width, view.height))
                }
            }
        }
    }

    private fun dragAndTap(lp: WindowManager.LayoutParams): View.OnTouchListener {
        var startRawY = 0f
        var startRawX = 0f
        var startY = 0
        var moved = false
        return View.OnTouchListener { view, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    startRawY = e.rawY; startRawX = e.rawX; startY = lp.y; moved = false; true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dy = (e.rawY - startRawY).toInt()
                    val dx = (e.rawX - startRawX).toInt()
                    if (abs(dy) > dp(8f) || abs(dx) > dp(8f)) moved = true
                    lp.y = startY + dy
                    windowManager.updateViewLayout(view, lp); true
                }
                MotionEvent.ACTION_UP -> {
                    if (!moved) openPanel()
                    else {
                        val settings = FlashCapsuleApp.from(this@OverlayService).settings
                        settings.handleY = lp.y
                        // 水平拖动：落在屏幕左半 → 吸附左缘，右半 → 右缘
                        val half = resources.displayMetrics.widthPixels / 2
                        val newLeft = e.rawX < half
                        if (newLeft != settings.handleLeft) {
                            settings.handleLeft = newLeft
                            rebuildHandle() // 换边重建（gravity + 圆角）
                        }
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun rebuildHandle() {
        handle?.let { runCatching { windowManager.removeView(it) } }
        handle = null
        addHandle()
    }

    private fun openPanel() {
        val current = panel
        if (current != null) {
            current.dismiss()
            return
        }
        val app = FlashCapsuleApp.from(this)
        panel = OverlayPanel(
            context = this,
            repo = app.repository,
            langCode = app.settings.sttLanguage,
            onDismiss = { panel = null },
        ).also { it.show() }
    }

    override fun onDestroy() {
        panel?.dismiss()
        panel = null
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
