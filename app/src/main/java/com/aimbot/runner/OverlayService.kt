package com.aimbot.runner

import android.app.Service
import android.content.Intent
import android.graphics.*
import android.os.IBinder
import android.view.*
import android.widget.LinearLayout
import com.aimbot.runner.script.AimResult

class OverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private var overlayView: OverlayView? = null
    private var params: WindowManager.LayoutParams? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "SHOW" -> showOverlay()
            "HIDE" -> hideOverlay()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        hideOverlay()
        super.onDestroy()
    }

    private fun showOverlay() {
        if (overlayView != null) return
        overlayView = OverlayView(this)
        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )
        params!!.gravity = Gravity.TOP or Gravity.LEFT
        params!!.setTitle("Aimbot Overlay")
        windowManager.addView(overlayView!!, params!!)
    }

    private fun hideOverlay() {
        overlayView?.let { windowManager.removeView(it) }
        overlayView = null
    }

    fun setTarget(x: Float, y: Float, active: Boolean) {
        overlayView?.setTarget(x, y, active)
    }

    private class OverlayView(context: android.content.Context) : android.view.View(context) {
        private val paint = Paint().apply {
            color = Color.RED
            strokeWidth = 4f
            style = Paint.Style.STROKE
            antiAlias = true
        }
        private val snapPaint = Paint().apply {
            color = Color.CYAN
            strokeWidth = 2f
            style = Paint.Style.STROKE
            antiAlias = true
        }
        private var targetX = 0f
        private var targetY = 0f
        private var active = false

        fun setTarget(x: Float, y: Float, active: Boolean) {
            this.targetX = x
            this.targetY = y
            this.active = active
            postInvalidate()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            if (!active) return
            val w = canvas.width.toFloat()
            val h = canvas.height.toFloat()
            paint.color = Color.RED
            canvas.drawLine(targetX - 20f, targetY, targetX + 20f, targetY, paint)
            canvas.drawLine(targetX, targetY - 20f, targetX, targetY + 20f, paint)
            snapPaint.color = Color.CYAN
            canvas.drawLine(w / 2f, h, targetX, targetY, snapPaint)
        }
    }
}
