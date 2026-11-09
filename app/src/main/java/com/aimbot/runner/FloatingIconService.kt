package com.aimbot.runner

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.IBinder
import android.view.*
import android.widget.ImageView

class FloatingIconService : Service() {

    private var windowManager: WindowManager? = null
    private var floatingIcon: View? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createFloatingIcon()
        return START_STICKY
    }

    private fun createFloatingIcon() {
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.LEFT
        params.x = 50
        params.y = 200

        floatingIcon = ImageView(this)
        floatingIcon?.setImageResource(android.R.ic_dialog_info)
        floatingIcon?.setBackgroundColor(0xFF66FCF1)
        floatingIcon?.layoutParams = ViewGroup.LayoutParams(80, 80)

        floatingIcon?.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> true
                MotionEvent.ACTION_MOVE -> {
                    params.x = event.rawX.toInt() - 40
                    params.y = event.rawY.toInt() - 80
                    windowManager?.updateViewLayout(floatingIcon, params)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    // Tap to open main app
                    val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
                    launchIntent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    startActivity(launchIntent)
                    true
                }
                else -> false
            }
        }

        windowManager?.addView(floatingIcon, params)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        if (floatingIcon != null) {
            windowManager?.removeView(floatingIcon)
            floatingIcon = null
        }
    }


}
