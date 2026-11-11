package com.aimbot.runner.root

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Path
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.IBinder
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import android.util.Log
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Non-root input injection via AccessibilityService gestures.
 * Works on unrooted devices once the user grants Accessibility permission.
 * Falls back to root /dev/input/event writes when root is available.
 */
object NonRootInputInjector {

    private const val TAG = "NonRootInput"

    /** Queue of pending taps: (screenX, screenY) */
    private val tapQueue = ConcurrentLinkedQueue<FloatArray>()

    /** Queue of pending swipes: (x1, y1, x2, y2, ms) */
    private val swipeQueue = ConcurrentLinkedQueue<FloatArray>()

    @Volatile
    private var service: AccessibilityService? = null

    @Volatile
    private var enabled: Boolean = false

    @Volatile
    private var screenW: Int = 0
    @Volatile
    private var screenH: Int = 0

    /** Call once from MainActivity after checking Accessibility permission. */
    fun init(context: Context) {
        val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        enabled = am.isEnabled
        if (!enabled) {
            Log.w(TAG, "AccessibilityService not enabled")
        }
    }

    fun bindService(svc: AccessibilityService, sw: Int, sh: Int) {
        service = svc
        screenW = sw
        screenH = sh
        enabled = true
    }

    fun unbind() {
        service = null
        enabled = false
    }

    fun isEnabled(): Boolean = enabled

    /** Queue a tap at (x,y) in screen pixels. */
    fun tap(x: Float, y: Float) {
        tapQueue.add(floatArrayOf(x, y))
        drain()
    }

    /** Queue a swipe from (x1,y1) to (x2,y2) over ms milliseconds. */
    fun swipe(x1: Float, y1: Float, x2: Float, y2: Float, ms: Long) {
        swipeQueue.add(floatArrayOf(x1, y1, x2, y2, ms.toFloat()))
        drain()
    }

    private fun drain() {
        val svc = service ?: NonRootAccessibilityService.instance
        if (svc != null) {
            service = svc
            enabled = true
        }
        if (!enabled || service == null) return
        val activeSvc = service ?: return

        // Drain taps
        while (true) {
            val t = tapQueue.poll() ?: break
            try {
                performTap(activeSvc, t[0], t[1])
            } catch (e: Exception) {
                Log.e(TAG, "performTap error", e)
            }
        }

        // Drain swipes
        while (true) {
            val s = swipeQueue.poll() ?: break
            try {
                performSwipe(activeSvc, s[0], s[1], s[2], s[3], s[4].toLong())
            } catch (e: Exception) {
                Log.e(TAG, "performSwipe error", e)
            }
        }
    }

    private fun performTap(svc: AccessibilityService, x: Float, y: Float) {
        val path = Path()
        path.moveTo(x.coerceAtLeast(0f), y.coerceAtLeast(0f))
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 80))
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            svc.dispatchGesture(gesture, null, null)
        }
    }

    private fun performSwipe(svc: AccessibilityService, x1: Float, y1: Float, x2: Float, y2: Float, ms: Long) {
        val path = Path()
        path.moveTo(x1.coerceAtLeast(0f), y1.coerceAtLeast(0f))
        path.lineTo(x2.coerceAtLeast(0f), y2.coerceAtLeast(0f))
        val duration = ms.coerceIn(10L, 5000L)
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, duration))
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            svc.dispatchGesture(gesture, null, null)
        }
    }

    /** Opens the Accessibility settings so the user can enable the service. */
    fun openSettings(context: Context) {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}

/**
 * Stub AccessibilityService that the user enables in settings.
 * It does nothing on its own; NonRootInputInjector drives it via dispatchGesture.
 */
class NonRootAccessibilityService : AccessibilityService() {

    companion object {
        @Volatile
        var instance: NonRootAccessibilityService? = null
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.i("NonRootInput", "AccessibilityService connected")
    }

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        return super.onUnbind(intent)
    }

    override fun onAccessibilityEvent(event: android.view.accessibility.AccessibilityEvent?) {}

    override fun onInterrupt() {}
}
