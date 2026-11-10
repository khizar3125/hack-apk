package com.aimbot.runner

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import com.aimbot.runner.root.MemoryEngine

/**
 * Full-screen transparent overlay that draws the ESP boxes, health bars and
 * the aim snapline. Runs as a foreground service so the system never kills it
 * mid-game.
 */
class OverlayService : Service() {

    companion object {
        @Volatile
        var instance: OverlayService? = null

        private const val CHANNEL_ID = "runner_overlay"
        private const val NOTIF_ID = 4712
    }

    private lateinit var windowManager: WindowManager
    private var overlayView: OverlayView? = null
    private var params: WindowManager.LayoutParams? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIF_ID, buildNotification())

        when (intent?.action) {
            "HIDE" -> hideOverlay()
            else -> showOverlay()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        instance = null
        hideOverlay()
        super.onDestroy()
    }

private fun showOverlay() {
        if (overlayView != null) return

        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Overlay permission denied", Toast.LENGTH_LONG).show()
            stopSelf()
            return
        }

        val view = OverlayView(this)
        val p = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        p.gravity = Gravity.TOP or Gravity.START

        try {
            windowManager.addView(view, p)
            view.setTargets(emptyList())
            overlayView = view
            params = p
        } catch (e: Exception) {
            Toast.makeText(this, "Overlay failed: ${e.message}", Toast.LENGTH_LONG).show()
            stopSelf()
        }
    }

    private fun hideOverlay() {
        overlayView?.let {
            try {
                windowManager.removeView(it)
            } catch (_: Exception) {
            }
        }
        overlayView = null
        params = null
    }

    /** Replaces the entity list the overlay draws. */
    fun setTargets(targets: List<MemoryEngine.Target>) {
        overlayView?.setTargets(targets)
    }

    /** Sets the aim point the crosshair snaps to. */
    fun setTarget(x: Float, y: Float, active: Boolean) {
        overlayView?.setAimPoint(x, y, active)
    }

    private fun overlayType(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val mgr = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            mgr.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Runner Overlay", NotificationManager.IMPORTANCE_LOW)
            )
        }
    }

    private fun buildNotification(): Notification {
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            android.app.Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            android.app.Notification.Builder(this)
        }
        return builder
            .setContentTitle("Runner overlay")
            .setContentText("ESP active")
            .setSmallIcon(android.R.drawable.ic_menu_manage)
            .setOngoing(true)
            .build()
    }

    private class OverlayView(context: Context) : View(context) {

        private val boxPaint = Paint().apply {
            color = Color.parseColor("#66fcf1")
            strokeWidth = 3f
            style = Paint.Style.STROKE
            isAntiAlias = true
        }

        private val healthBackPaint = Paint().apply {
            color = Color.parseColor("#55000000")
            style = Paint.Style.FILL
            isAntiAlias = true
        }

        private val healthPaint = Paint().apply {
            color = Color.parseColor("#00ff88")
            style = Paint.Style.FILL
            isAntiAlias = true
        }

        private val textPaint = Paint().apply {
            color = Color.WHITE
            textSize = 26f
            isAntiAlias = true
        }

        private val crosshairPaint = Paint().apply {
            color = Color.RED
            strokeWidth = 4f
            style = Paint.Style.STROKE
            isAntiAlias = true
        }

        private val snapPaint = Paint().apply {
            color = Color.parseColor("#66fcf1")
            strokeWidth = 2f
            style = Paint.Style.STROKE
            isAntiAlias = true
        }

        private var targets: List<MemoryEngine.Target> = emptyList()
        private var aimX = 0f
        private var aimY = 0f
        private var aimActive = false

        fun setTargets(list: List<MemoryEngine.Target>) {
            targets = list
            postInvalidate()
        }

        fun setAimPoint(x: Float, y: Float, active: Boolean) {
            aimX = x
            aimY = y
            aimActive = active
            postInvalidate()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val w = canvas.width.toFloat()
            val h = canvas.height.toFloat()

            for (t in targets) {
                // Box height scales with the distance so far targets stay small.
                val scale = (t.distance.coerceAtLeast(1f) / 20f).coerceIn(0.35f, 3f)
                val boxH = 70f * scale
                val boxW = 34f * scale
                val left = t.screenX - boxW / 2f
                val top = t.screenY - boxH / 2f
                val right = t.screenX + boxW / 2f
                val bottom = t.screenY + boxH / 2f

                canvas.drawRect(left, top, right, bottom, boxPaint)

                // Health bar on the left edge of the box.
                val hp = (t.health.coerceIn(0, 100)) / 100f
                val barW = 5f * scale
                canvas.drawRect(left - barW - 3f, top, left - 3f, bottom, healthBackPaint)
                healthPaint.color = when {
                    hp > 0.5f -> Color.parseColor("#00ff88")
                    hp > 0.25f -> Color.parseColor("#ffcc00")
                    else -> Color.parseColor("#ff4444")
                }
                canvas.drawRect(
                    left - barW - 3f, bottom - boxH * hp,
                    left - 3f, bottom, healthPaint
                )

                canvas.drawText(
                    "${t.health}  ${t.distance.toInt()}m",
                    left,
                    top - 6f,
                    textPaint
                )
            }

            if (aimActive) {
                canvas.drawLine(aimX - 22f, aimY, aimX + 22f, aimY, crosshairPaint)
                canvas.drawLine(aimX, aimY - 22f, aimX, aimY + 22f, crosshairPaint)
                canvas.drawLine(w / 2f, h, aimX, aimY, snapPaint)
            }
        }
    }
}
