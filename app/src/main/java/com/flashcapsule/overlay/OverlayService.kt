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
        val settings = FlashCapsuleApp.from(this@OverlayService).settings
        val left = settings.handleLeft
        val screenW = resources.displayMetrics.widthPixels
        val screenH = resources.displayMetrics.heightPixels
        val handleW = dp(26f).toInt()
        val handleH = dp(112f).toInt()
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
            handleW, handleH,
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT,
        ).apply {
            // 用绝对坐标定位（TOP|START），支持水平实时跟随
            gravity = Gravity.TOP or Gravity.START
            x = if (left) dp(2f).toInt() else screenW - handleW - dp(2f).toInt()
            y = screenH / 2 + settings.handleY // 恢复上次位置（handleY = 相对屏幕中心偏移）
        }
        view.setOnTouchListener(dragAndTap(lp, screenW, screenH, handleW))
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

    private fun dragAndTap(
        lp: WindowManager.LayoutParams,
        screenW: Int,
        screenH: Int,
        handleW: Int,
    ): View.OnTouchListener {
        var startRawX = 0f
        var startRawY = 0f
        var startX = 0
        var startY = 0
        var moved = false
        return View.OnTouchListener { view, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    startRawX = e.rawX; startRawY = e.rawY; startX = lp.x; startY = lp.y; moved = false; true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (e.rawX - startRawX).toInt()
                    val dy = (e.rawY - startRawY).toInt()
                    if (abs(dx) > dp(8f) || abs(dy) > dp(8f)) moved = true
                    if (moved) {
                        // 水平/垂直都实时跟随手指
                        lp.x = startX + dx
                        lp.y = startY + dy
                        windowManager.updateViewLayout(view, lp)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!moved) {
                        openPanel()
                    } else {
                        val settings = FlashCapsuleApp.from(this@OverlayService).settings
                        // 吸附到最近的一边
                        val half = screenW / 2
                        val newLeft = lp.x + handleW / 2 < half
                        val clampedY = lp.y.coerceIn(0, screenH - lp.height)
                        settings.handleY = clampedY - screenH / 2
                        if (newLeft != settings.handleLeft) {
                            // 换边：重建（圆角反向 + 定位）
                            settings.handleLeft = newLeft
                            rebuildHandle()
                        } else {
                            // 同侧：吸附回边缘
                            lp.x = if (newLeft) dp(2f).toInt() else screenW - handleW - dp(2f).toInt()
                            lp.y = clampedY
                            windowManager.updateViewLayout(view, lp)
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
