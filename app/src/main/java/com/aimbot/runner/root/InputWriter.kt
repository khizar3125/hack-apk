package com.aimbot.runner.root

import android.util.Log
import java.io.File
import java.io.FileOutputStream

/**
 * Raw input injection through the Linux input subsystem.
 *
 * Only root can write to /dev/input/event*, so every write goes through a
 * root shell. Uses multitouch protocol B, which is what modern Android
 * touchscreens expose.
 */
object InputWriter {

    private const val TAG = "InputWriter"

    private const val EV_SYN = 0x00
    private const val EV_KEY = 0x01
    private const val EV_ABS = 0x03

    private const val ABS_MT_SLOT = 0x2F
    private const val ABS_MT_TRACKING_ID = 0x39
    private const val ABS_MT_POSITION_X = 0x35
    private const val ABS_MT_POSITION_Y = 0x36

    private const val BTN_TOUCH = 0x14A

    @Volatile
    private var devicePath: String? = null

    @Volatile
    private var maxX: Int = 0

    @Volatile
    private var maxY: Int = 0

    @Volatile
    private var ready: Boolean = false

    /** Finds a usable absolute-axis touchscreen and caches its geometry. */
    fun probe(): Boolean {
        if (ready) return true
        val devices = RootAccess.runShell("cat /proc/bus/input/devices")
        if (devices.isEmpty()) return false

        val blocks = devices.split("\n\n")
        for (block in blocks) {
            if (!block.contains("ABS_MT_POSITION_X") || !block.contains("ABS_MT_POSITION_Y")) continue
            val handler = Regex("H: Handlers=(\\S+)").find(block)?.groupValues?.get(1) ?: continue
            val path = findEventDevice(handler) ?: continue

            val absBlock = block.substringAfter("A: ", "").substringBefore("\n\n")
            val mx = Regex("0035\\s*:\\s*.*?\\b(\\d+)\\b").find(absBlock)?.groupValues?.get(1)?.toIntOrNull() ?: 0
            val my = Regex("0036\\s*:\\s*.*?\\b(\\d+)\\b").find(absBlock)?.groupValues?.get(1)?.toIntOrNull() ?: 0
            if (mx <= 0 || my <= 0) continue

            devicePath = path
            maxX = mx
            maxY = my
            ready = true
            Log.i(TAG, "touch device $path maxX=$mx maxY=$my")
            return true
        }
        Log.w(TAG, "no absolute touchscreen found")
        return false
    }

    private fun findEventDevice(handler: String): String? {
        val out = RootAccess.runShell("ls /dev/input/")
        for (name in out.split("\n").map { it.trim() }.filter { it.startsWith("event") }) {
            val path = "/dev/input/$name"
            val handlers = RootAccess.runShell("cat /sys/class/input/$name/device/uevent 2>/dev/null")
            if (handlers.contains(handler)) return path
        }
        return null
    }

    /**
     * Drags the touch pointer from (x1,y1) to (x2,y2) in device coordinates.
     * Screen pixels are scaled into the device's absolute range.
     */
    fun drag(screenW: Int, screenH: Int, x1: Float, y1: Float, x2: Float, y2: Float, steps: Int = 8) {
        val path = devicePath ?: return
        if (!ready) probe()
        if (!ready) return

        val sx = (x1 / screenW * maxX).toInt()
        val sy = (y1 / screenH * maxY).toInt()
        val ex = (x2 / screenW * maxX).toInt()
        val ey = (y2 / screenH * maxY).toInt()

        val script = StringBuilder()
        script.append("exec 3>/dev/input/").append(path.substringAfterLast('/')).append("\n")
        script.append("printf ").append(event(EV_ABS, ABS_MT_SLOT, 0)).append(" >&$3\n")
        script.append("printf ").append(event(EV_ABS, ABS_MT_TRACKING_ID, 1)).append(" >&$3\n")
        script.append("printf ").append(event(EV_ABS, ABS_MT_POSITION_X, sx)).append(" >&$3\n")
        script.append("printf ").append(event(EV_ABS, ABS_MT_POSITION_Y, sy)).append(" >&$3\n")
        script.append("printf ").append(event(EV_KEY, BTN_TOUCH, 1)).append(" >&$3\n")
        script.append("printf ").append(event(EV_SYN, 0, 0)).append(" >&$3\n")

        for (i in 1..steps) {
            val t = i.toFloat() / steps
            val cx = sx + ((ex - sx) * t).toInt()
            val cy = sy + ((ey - sy) * t).toInt()
            script.append("printf ").append(event(EV_ABS, ABS_MT_POSITION_X, cx)).append(" >&$3\n")
            script.append("printf ").append(event(EV_ABS, ABS_MT_POSITION_Y, cy)).append(" >&$3\n")
            script.append("printf ").append(event(EV_SYN, 0, 0)).append(" >&$3\n")
        }

        script.append("printf ").append(event(EV_KEY, BTN_TOUCH, 0)).append(" >&$3\n")
        script.append("printf ").append(event(EV_ABS, ABS_MT_TRACKING_ID, -1)).append(" >&$3\n")
        script.append("printf ").append(event(EV_SYN, 0, 0)).append(" >&$3\n")
        script.append("exec 3>&-\n")

        RootAccess.runShell(script.toString())
    }

    /** Taps at (x,y) in screen pixels. */
    fun tap(screenW: Int, screenH: Int, x: Float, y: Float) {
        val path = devicePath ?: return
        if (!ready) probe()
        if (!ready) return

        val sx = (x / screenW * maxX).toInt()
        val sy = (y / screenH * maxY).toInt()

        val script = StringBuilder()
        script.append("exec 3>/dev/input/").append(path.substringAfterLast('/')).append("\n")
        script.append("printf ").append(event(EV_ABS, ABS_MT_SLOT, 0)).append(" >&$3\n")
        script.append("printf ").append(event(EV_ABS, ABS_MT_TRACKING_ID, 1)).append(" >&$3\n")
        script.append("printf ").append(event(EV_ABS, ABS_MT_POSITION_X, sx)).append(" >&$3\n")
        script.append("printf ").append(event(EV_ABS, ABS_MT_POSITION_Y, sy)).append(" >&$3\n")
        script.append("printf ").append(event(EV_KEY, BTN_TOUCH, 1)).append(" >&$3\n")
        script.append("printf ").append(event(EV_SYN, 0, 0)).append(" >&$3\n")
        script.append("printf ").append(event(EV_KEY, BTN_TOUCH, 0)).append(" >&$3\n")
        script.append("printf ").append(event(EV_ABS, ABS_MT_TRACKING_ID, -1)).append(" >&$3\n")
        script.append("printf ").append(event(EV_SYN, 0, 0)).append(" >&$3\n")
        script.append("exec 3>&-\n")

        RootAccess.runShell(script.toString())
    }

    /** Builds a single struct input_event as a printf escape sequence. */
    private fun event(type: Int, code: Int, value: Int): String {
        val ts = 0
        val us = 0
        val bytes = ByteArray(24)
        writeInt(bytes, 0, ts)
        writeInt(bytes, 4, us)
        writeInt(bytes, 8, type)
        writeInt(bytes, 12, code)
        writeInt(bytes, 16, value)
        writeInt(bytes, 20, 0)
        return bytes.joinToString("") { "\\$(printf '%03o' ${it.toInt() and 0xFF})" }
    }

    private fun writeInt(buf: ByteArray, offset: Int, value: Int) {
        buf[offset] = (value and 0xFF).toByte()
        buf[offset + 1] = ((value shr 8) and 0xFF).toByte()
        buf[offset + 2] = ((value shr 16) and 0xFF).toByte()
        buf[offset + 3] = ((value shr 24) and 0xFF).toByte()
    }

    /** True when an event device node exists and is writable. */
    fun devicePresent(): Boolean {
        val path = devicePath ?: return false
        return File(path).exists()
    }
}
