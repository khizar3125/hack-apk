package com.aimbot.runner

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Build
import android.view.accessibility.AccessibilityNodeInfo

class InputInjector : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        return super.onUnbind(intent)
    }

    override fun onAccessibilityEvent(event: android.view.accessibility.AccessibilityEvent?) {}

    override fun onInterrupt() {}

    /** Simulate a swipe/touch from (x1,y1) to (x2,y2) over ms milliseconds. */
    fun swipe(x1: Float, y1: Float, x2: Float, y2: Float, ms: Long) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return
        val path = Path().apply { moveTo(x1, y1); lineTo(x2, y2) }
        val stroke = GestureDescription.StrokeDescription(path, 0, ms)
        val gesture = GestureDescription.Builder().add(stroke).build()
        dispatchGesture(gesture, null, null)
    }

    /** Tap at (x,y). */
    fun tap(x: Float, y: Float) {
        swipe(x, y, x, y, 100)
    }

    /** Send a raw accessibility action to a node if reachable. */
    fun clickNode(node: AccessibilityNodeInfo?, action: Int) {
        node?.performAction(action)
    }
}
