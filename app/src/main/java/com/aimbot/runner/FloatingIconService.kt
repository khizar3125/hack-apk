package com.aimbot.runner

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.CompoundButton
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import com.aimbot.runner.model.GameConfig
import com.aimbot.runner.model.ScriptConfig
import com.aimbot.runner.script.ScriptExecutor
import org.json.JSONObject

/**
 * Floating menu that stays on top of the game. Drag to move, tap to expand a
 * control panel with per-feature ON/OFF toggles.
 *
 * The overlay uses FLAG_NOT_FOCUSABLE so it never steals input focus — the
 * game keyboard and touch keep working while the menu is visible.
 */
class FloatingIconService : Service() {

    private var windowManager: WindowManager? = null
    private var rootView: View? = null
    private var handleView: ImageView? = null
    private var panelView: View? = null
    private var isExpanded = false

    /** Currently selected game + script (set by MainActivity before launching). */
    private var currentGame: GameConfig? = null
    private var currentScript: ScriptConfig? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIF_ID, buildNotification())

        // Game / script target hamesha padho, chahe action SHOW ho ya SET_TARGET —
        // warna floating panel mein feature toggle dikhenge hi nahi.
        intent?.getStringExtra("game_name")?.let { name ->
            intent.getStringExtra("game_package")?.let { pkg ->
                intent.getStringExtra("script_name")?.let { sname ->
                    currentGame = GameConfig(name, pkg, intent.getStringExtra("game_module") ?: "", null, null, null, null, null)
                    currentScript = ScriptConfig(sname, intent.getStringExtra("script_type") ?: "misc", name, null, null)
                }
            }
        }

        when (intent?.action) {
            "SHOW" -> showFloatingIcon()
            "HIDE" -> hideFloatingIcon()
            "SET_TARGET" -> showFloatingIcon()
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
            // FLAG_NOT_FOCUSABLE: the overlay never steals focus, so the game
            // keyboard and touch input keep working behind it.
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.START
        params.x = 24
        params.y = 200

        val root = FrameLayout(this)

        // Small always-visible drag handle.
        val handle = ImageView(this).apply {
            setImageResource(android.R.drawable.ic_menu_manage)
            setColorFilter(Color.parseColor("#00F0FF"))
            setBackgroundResource(R.drawable.bg_icon)
            layoutParams = FrameLayout.LayoutParams(dp(52), dp(52))
        }
        root.addView(handle)

        // Expandable panel with feature toggles.
        val panel = buildPanel()
        panel.visibility = View.GONE
        root.addView(panel)

        var lastX = 0f
        var lastY = 0f
        var isDrag = false

        handle.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    lastX = event.rawX
                    lastY = event.rawY
                    isDrag = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = Math.abs(event.rawX - lastX)
                    val dy = Math.abs(event.rawY - lastY)
                    if (dx > 8 || dy > 8) isDrag = true
                    params.x = (event.rawX - dp(26)).toInt()
                    params.y = (event.rawY - dp(26)).toInt()
                    safeUpdate(root, params)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!isDrag) {
                        togglePanel(panel, handle)
                    }
                    true
                }
                else -> false
            }
        }

        try {
            wm.addView(root, params)
            rootView = root
            handleView = handle
            panelView = panel
        } catch (e: Exception) {
            Toast.makeText(this, "Overlay failed: ${e.message}", Toast.LENGTH_LONG).show()
            stopSelf()
        }
    }

    private fun buildPanel(): View {
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF0B0C10.toInt())
            setPadding(dp(16), dp(14), dp(16), dp(14))
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
            elevation = 8f
        }

        // Title bar.
        panel.addView(TextView(this).apply {
            text = "RUNNER MENU"
            setTextColor(Color.parseColor("#00F0FF"))
            textSize = 15f
            setTypeface(android.graphics.Typeface.defaultFromStyle(android.graphics.Typeface.BOLD))
            setPadding(0, 0, 0, dp(10))
        })

        // Open app button.
        panel.addView(makeButton("Open App") {
            val launch = packageManager.getLaunchIntentForPackage(packageName)
            if (launch != null) {
                launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                startActivity(launch)
            }
        })

        // Feature toggles section.
        if (currentGame != null && currentScript != null) {
            panel.addView(TextView(this).apply {
                text = "FEATURES"
                setTextColor(Color.parseColor("#8B949E"))
                textSize = 12f
                setPadding(0, dp(10), 0, dp(6))
            })

            val scriptType = currentScript!!.type.lowercase()
            when (scriptType) {
                "aimbot" -> {
                    addToggle(panel, "Aimbot", "aimbot", true)
                    addToggle(panel, "Auto Fire", "autoFire", false)
                    addToggle(panel, "Team Check", "teamCheck", false)
                }
                "esp" -> {
                    addToggle(panel, "Box ESP", "box", true)
                    addToggle(panel, "Skeleton", "skeleton", true)
                    addToggle(panel, "Distance", "distance", true)
                    addToggle(panel, "Health", "health", true)
                    addToggle(panel, "Snapline", "snapline", true)
                }
                "misc" -> {
                    addToggle(panel, "Fly", "fly", false)
                    addToggle(panel, "Speed", "speed", false)
                    addToggle(panel, "No Recoil", "nocrecoil", false)
                    addToggle(panel, "Auto Play", "autoPlay", false)
                    addToggle(panel, "Auto Tap", "autoTap", false)
                    addToggle(panel, "Auto Fire", "autoFire", false)
                }
            }
        }

        panel.addView(TextView(this).apply {
            text = "Tap a toggle to turn a feature ON / OFF."
            setTextColor(Color.parseColor("#586069"))
            textSize = 11f
            setPadding(0, dp(8), 0, 0)
        })

        panel.addView(makeButton("Stop Script") {
            ScriptService.stopScript(this)
            Toast.makeText(this, "Script stopped", Toast.LENGTH_SHORT).show()
        })

        panel.addView(makeButton("Hide Menu") {
            hideFloatingIcon()
        })

        return panel
    }

    private fun makeButton(label: String, onClick: () -> Unit): View {
        return TextView(this).apply {
            text = label
            setTextColor(Color.WHITE)
            textSize = 14f
            setPadding(0, dp(10), 0, dp(10))
            setBackgroundResource(R.drawable.bg_chip)
            setOnClickListener { onClick() }
        }
    }

    private fun addToggle(panel: LinearLayout, label: String, feature: String, defaultOn: Boolean) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setPadding(0, dp(4), 0, dp(4))
        }

        val tv = TextView(this).apply {
            text = label
            setTextColor(Color.WHITE)
            textSize = 14f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val sw = Switch(this).apply {
            val gameName = currentGame?.name ?: ""
            val scriptName = currentScript?.name ?: ""
            isChecked = FeatureController.isOn(gameName, scriptName, feature)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        sw.setOnCheckedChangeListener { _, checked ->
            val gameName = currentGame?.name ?: ""
            val scriptName = currentScript?.name ?: ""
            FeatureController.set(gameName, scriptName, feature, checked)
        }

        row.addView(tv)
        row.addView(sw)
        panel.addView(row)
    }

    private fun togglePanel(panel: View, handle: ImageView) {
        isExpanded = !isExpanded
        panel.visibility = if (isExpanded) View.VISIBLE else View.GONE
        handle.setColorFilter(
            if (isExpanded) Color.parseColor("#00E676") else Color.parseColor("#00F0FF")
        )
    }

    private fun hideFloatingIcon() {
        val root = rootView ?: return
        try {
            windowManager?.removeView(root)
        } catch (_: Exception) {
        }
        rootView = null
        handleView = null
        panelView = null
        isExpanded = false
    }

    private fun safeUpdate(view: View, params: WindowManager.LayoutParams) {
        try {
            windowManager?.updateViewLayout(view, params)
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
            .setContentTitle("Runner Menu active")
            .setContentText("Tap the floating icon for feature toggles")
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
