package com.aimbot.runner.root

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.ImageFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.util.Log
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Captures the screen via MediaProjection (no root required).
 * The user must grant the capture permission via a system dialog first.
 * Captured frames are handed to ScreenColorScanner for color-based ESP.
 */
class ScreenCaptureService : Service() {

    private const val TAG = "ScreenCapture"

    @Volatile
    private var projection: MediaProjection? = null
    @Volatile
    private var imageReader: ImageReader? = null
    @Volatile
    private var virtualDisplay: VirtualDisplay? = null
    @Volatile
    private var captureThread: HandlerThread? = null
    @Volatile
    private var captureHandler: Handler? = null

    @Volatile
    private var screenW: Int = 0
    @Volatile
    private var screenH: Int = 0

    /** Latest captured ARGB frame, for scanning. */
    @Volatile
    private var currentFrame: Bitmap? = null

    private val frameListeners = ConcurrentLinkedQueue<(Bitmap) -> Unit>()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val dm = getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        val display = dm.defaultDisplay
        val size = android.graphics.Point()
        display.getRealSize(size)
        screenW = size.x
        screenH = size.y
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startCapture(this)
        return START_STICKY
    }

    /** Call from onStartCommand. Reads the MediaProjection result from ScreenCaptureHolder. */
    fun startCapture(context: Context) {
        val mgr = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val resultCode = ScreenCaptureHolder.resultCode
        val data = ScreenCaptureHolder.resultData
        projection = mgr.getMediaProjection(resultCode, data)
        if (projection == null) {
            Log.e(TAG, "MediaProjection is null")
            return
        }

        val density = context.resources.displayMetrics.densityDpi
        imageReader = ImageReader.newInstance(screenW, screenH, ImageFormat.RGBA_8888, 2)

        captureThread = HandlerThread("ScreenCaptureThread")
        captureThread?.start()
        captureHandler = Handler(captureThread!!.looper)

        virtualDisplay = projection!!.createVirtualDisplay(
            "RunnerCapture",
            screenW, screenH, density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader!!.surface, null, captureHandler
        )

        captureHandler!!.post(object : Runnable {
            override fun run() {
                while (projection != null && imageReader != null) {
                    val img = imageReader!!.acquireLatestImage() ?: continue
                    try {
                        val bitmap = imageToBitmap(img)
                        currentFrame = bitmap
                        for (l in frameListeners) l(bitmap)
                    } catch (e: Exception) {
                        Log.e(TAG, "frame error", e)
                    } finally {
                        img.close()
                    }
                    try { Thread.sleep(33) } catch (_: InterruptedException) { break }
                }
            }
        })

        Log.i(TAG, "screen capture started ${screenW}x$screenH")
    }

    fun stopCapture() {
        captureHandler?.removeCallbacksAndMessages(null)
        captureThread?.quitSafely()
        virtualDisplay?.release()
        projection?.stop()
        projection = null
        virtualDisplay = null
        imageReader = null
        captureHandler = null
        captureThread = null
        currentFrame = null
        Log.i(TAG, "screen capture stopped")
    }

    fun addFrameListener(listener: (Bitmap) -> Unit) {
        frameListeners.add(listener)
    }

    fun removeFrameListener(listener: (Bitmap) -> Unit) {
        frameListeners.remove(listener)
    }

    fun getCurrentFrame(): Bitmap? = currentFrame

    private fun imageToBitmap(image: Image): Bitmap {
        val width = image.width
        val height = image.height
        val buffer = ByteArray(width * height * 4)
        val planes = image.planes
        for (i in planes.indices) {
            val plane = planes[i]
            val bb = plane.buffer
            val rowStride = plane.rowStride
            val pixelStride = plane.pixelStride ?: 1
            for (y in 0 until height) {
                val rowOffset = y * rowStride
                for (x in 0 until width) {
                    val pixelOffset = rowOffset + x * pixelStride
                    val outIdx = (y * width + x) * 4
                    if (pixelStride == 1 && rowStride == width) {
                        buffer[outIdx] = bb.get(pixelOffset)
                        buffer[outIdx + 1] = bb.get(pixelOffset + 1)
                        buffer[outIdx + 2] = bb.get(pixelOffset + 2)
                        buffer[outIdx + 3] = bb.get(pixelOffset + 3)
                    } else {
                        // Handle stride properly
                        bb.position(pixelOffset)
                        buffer[outIdx] = bb.get()
                        buffer[outIdx + 1] = bb.get()
                        buffer[outIdx + 2] = bb.get()
                        buffer[outIdx + 3] = bb.get()
                    }
                }
            }
        }
        return Bitmap.createBitmap(buffer, 0, width, height, Bitmap.Config.ARGB_8888)
    }
}

/** Simple RGB color scanner: finds regions matching a target color. */
object ScreenColorScanner {

    data class ColorRegion(val left: Int, val top: Int, val right: Int, val bottom: Int)

    fun scanForColor(bitmap: Bitmap?, targetR: Int, targetG: Int, targetB: Int, tolerance: Int = 40): List<ColorRegion> {
        if (bitmap == null) return emptyList()
        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)

        val visited = BooleanArray(w * h)
        val regions = mutableListOf<ColorRegion>()

        for (y in 0 until h) {
            for (x in 0 until w) {
                val idx = y * w + x
                if (visited[idx]) continue
                val p = pixels[idx]
                val r = (p shr 16) and 0xFF
                val g = (p shr 8) and 0xFF
                val b = p and 0xFF
                if (Math.abs(r - targetR) <= tolerance && Math.abs(g - targetG) <= tolerance && Math.abs(b - targetB) <= tolerance) {
                    val region = floodFill(pixels, visited, w, h, x, y, targetR, targetG, targetB, tolerance)
                    if (region.right - region.left > 5 && region.bottom - region.top > 5) {
                        regions.add(region)
                    }
                }
            }
        }
        return regions
    }

    private fun floodFill(
        pixels: IntArray,
        visited: BooleanArray,
        w: Int, h: Int,
        startX: Int, startY: Int,
        tr: Int, tg: Int, tb: Int,
        tol: Int
    ): ColorRegion {
        val stack = java.util.Stack<IntArray>()
        stack.push(intArrayOf(startX, startY))
        var minX = startX, maxX = startX, minY = startY, maxY = startY

        while (stack.isNotEmpty()) {
            val p = stack.pop()
            val x = p[0], y = p[1]
            if (x < 0 || x >= w || y < 0 || y >= h) continue
            val idx = y * w + x
            if (visited[idx]) continue
            val px = pixels[idx]
            val r = (px shr 16) and 0xFF
            val g = (px shr 8) and 0xFF
            val b = px and 0xFF
            if (Math.abs(r - tr) > tol || Math.abs(g - tg) > tol || Math.abs(b - tb) > tol) continue
            visited[idx] = true
            if (x < minX) minX = x
            if (x > maxX) maxX = x
            if (y < minY) minY = y
            if (y > maxY) maxY = y
            stack.push(intArrayOf(x + 1, y))
            stack.push(intArrayOf(x - 1, y))
            stack.push(intArrayOf(x, y + 1))
            stack.push(intArrayOf(x, y - 1))
        }
        return ColorRegion(minX, minY, maxX, maxY)
    }
}
