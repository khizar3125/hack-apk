package com.aimbot.runner

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

/**
 * Floating menu that stays on top of the game. Drag to move, tap to expand a
 * small control panel (start/stop script, open app, hide icon).
 */
class FloatingIconService : Service() {

    private var windowManager: WindowManager? = null
    private var rootView: View? = null
    private var iconView: ImageView? = null
    private var panelView: View? = null
    private var isExpanded = false

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Foreground service must post a notification within 5s or the system
        // kills it and the app process dies with it.
        startForeground(NOTIF_ID, buildNotification())

        when (intent?.action) {
            "SHOW" -> showFloatingIcon()
            "HIDE" -> hideFloatingIcon()
            else -> showFloatingIcon()
        }
        return START_STICKY
    }

    private fun showFloatingIcon() {
        if (rootView != null) return

        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Overlay permission denied", Toast.LENGTH_LONG).show()
            stopSelf()
            return
        }

        val wm = windowManager ?: return

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType(),
            // Focusable so the panel buttons receive taps. The collapsed handle
            // only needs FLAG_NOT_TOUCH_MODAL to avoid blocking the game.
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.START
        params.x = 30
        params.y = 220

        val root = FrameLayout(this)

        // Small always-visible handle.
        val handle = ImageView(this).apply {
            setImageResource(android.R.drawable.ic_menu_manage)
            setBackgroundColor(0)
            layoutParams = FrameLayout.LayoutParams(dp(56), dp(56))
        }
        root.addView(handle)

        // Hidden panel shown when the handle is tapped.
        val panel = buildPanel()
        panel.visibility = View.GONE
        root.addView(panel)

        handle.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> true
                MotionEvent.ACTION_MOVE -> {
                    params.x = event.rawX.toInt() - dp(28)
                    params.y = event.rawY.toInt() - dp(28)
                    safeUpdate(root, params)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    togglePanel(panel, handle)
                    true
                }
                else -> false
            }
        }

        try {
            wm.addView(root, params)
            rootView = root
            iconView = handle
            panelView = panel
        } catch (e: Exception) {
            Toast.makeText(this, "Overlay failed: ${e.message}", Toast.LENGTH_LONG).show()
            stopSelf()
        }
    }

    private fun buildPanel(): View {
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xE60B0C10.toInt())
            setPadding(dp(16), dp(12), dp(16), dp(12))
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
        }

        panel.addView(TextView(this).apply {
            text = "Runner"
            setTextColor(0xFF66FCF1.toInt())
            textSize = 16f
        })

        panel.addView(makeButton("Open App") {
            val launch = packageManager.getLaunchIntentForPackage(packageName)
            if (launch != null) {
                launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                startActivity(launch)
            }
        })

        panel.addView(makeButton("Show Overlay") {
            sendService(Intent(this, OverlayService::class.java).setAction("SHOW"))
        })

        panel.addView(makeButton("Hide Overlay") {
            sendService(Intent(this, OverlayService::class.java).setAction("HIDE"))
        })

        panel.addView(makeButton("Stop Script") {
            ScriptService.stopScript(this)
            Toast.makeText(this, "Script stopped", Toast.LENGTH_SHORT).show()
        })

        panel.addView(makeButton("Hide Icon") {
            hideFloatingIcon()
        })

        return panel
    }

    private fun makeButton(label: String, onClick: () -> Unit): View {
        return TextView(this).apply {
            text = label
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 15f
            setPadding(0, dp(10), 0, dp(10))
            setOnClickListener { onClick() }
        }
    }

    private fun togglePanel(panel: View, handle: View) {
        isExpanded = !isExpanded
        panel.visibility = if (isExpanded) View.VISIBLE else View.GONE
        val lp = handle.layoutParams as FrameLayout.LayoutParams
        lp.bottomMargin = if (isExpanded) dp(8) else 0
        handle.layoutParams = lp
    }

    private fun hideFloatingIcon() {
        val root = rootView ?: return
        try {
            windowManager?.removeView(root)
        } catch (_: Exception) {
        }
        rootView = null
        iconView = null
        panelView = null
        isExpanded = false
    }

    private fun safeUpdate(view: View, params: WindowManager.LayoutParams) {
        try {
            windowManager?.updateViewLayout(view, params)
        } catch (_: Exception) {
        }
    }

    private fun sendService(intent: Intent) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
        } catch (_: Exception) {
        }
    }

    private fun overlayType(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val mgr = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            mgr.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Runner Menu", NotificationManager.IMPORTANCE_LOW)
            )
        }
    }

    private fun buildNotification(): Notification {
        val open = PendingIntent.getActivity(
            this, 0,
            packageManager.getLaunchIntentForPackage(packageName),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            android.app.Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            android.app.Notification.Builder(this)
        }
        return builder
            .setContentTitle("Runner active")
            .setContentText("Tap the floating icon for controls")
            .setSmallIcon(android.R.drawable.ic_menu_manage)
            .setContentIntent(open)
            .setOngoing(true)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        hideFloatingIcon()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_ID = "runner_menu"
        private const val NOTIF_ID = 4711
    }
}
