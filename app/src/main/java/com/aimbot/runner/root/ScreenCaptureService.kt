package com.aimbot.runner.root

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Captures the screen via MediaProjection (no root required).
 * The user must grant the capture permission via a system dialog first.
 * Captured frames are handed to ScreenColorScanner for color-based ESP.
 */
class ScreenCaptureService : Service() {

    companion object {
        private const val TAG = "ScreenCapture"
    }

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
        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        wm.defaultDisplay.getMetrics(metrics)
        screenW = metrics.widthPixels
        screenH = metrics.heightPixels
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startCapture(this)
        return START_STICKY
    }

    /** Call from onStartCommand. Reads the MediaProjection result from ScreenCaptureHolder. */
    fun startCapture(context: Context) {
        val mgr = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val resultCode = com.aimbot.runner.ScreenCaptureHolder.resultCode
        val data = com.aimbot.runner.ScreenCaptureHolder.resultData
        if (data == null) {
            Log.e(TAG, "resultData is null")
            return
        }
        projection = mgr.getMediaProjection(resultCode, data)
        if (projection == null) {
            Log.e(TAG, "MediaProjection is null")
            return
        }

        val density = context.resources.displayMetrics.densityDpi
        imageReader = ImageReader.newInstance(screenW, screenH, PixelFormat.RGBA_8888, 2)

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
        val plane = image.planes[0]
        val buffer = plane.buffer
        val rowStride = plane.rowStride
        val pixelStride = plane.pixelStride
        val rowPadding = rowStride - pixelStride * width
        val bitmapWidth = width + rowPadding / pixelStride
        val bitmap = Bitmap.createBitmap(bitmapWidth, height, Bitmap.Config.ARGB_8888)
        bitmap.copyPixelsFromBuffer(buffer)
        return Bitmap.createBitmap(bitmap, 0, 0, width, height)
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

        for (py in 0 until h) {
            for (px in 0 until w) {
                val idx = py * w + px
                if (visited[idx]) continue
                val p = pixels[idx]
                val r = (p shr 16) and 0xFF
                val g = (p shr 8) and 0xFF
                val b = p and 0xFF
                if (Math.abs(r - targetR) <= tolerance && Math.abs(g - targetG) <= tolerance && Math.abs(b - targetB) <= tolerance) {
                    val region = floodFill(pixels, visited, w, h, px, py, targetR, targetG, targetB, tolerance)
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
        var minX = startX
        var maxX = startX
        var minY = startY
        var maxY = startY

        while (stack.isNotEmpty()) {
            val pt = stack.pop()
            val cx = pt[0]
            val cy = pt[1]
            if (cx < 0 || cx >= w || cy < 0 || cy >= h) continue
            val idx = cy * w + cx
            if (visited[idx]) continue
            val px = pixels[idx]
            val r = (px shr 16) and 0xFF
            val g = (px shr 8) and 0xFF
            val b = px and 0xFF
            if (Math.abs(r - tr) > tol || Math.abs(g - tg) > tol || Math.abs(b - tb) > tol) continue
            visited[idx] = true
            if (cx < minX) minX = cx
            if (cx > maxX) maxX = cx
            if (cy < minY) minY = cy
            if (cy > maxY) maxY = cy
            stack.push(intArrayOf(cx + 1, cy))
            stack.push(intArrayOf(cx - 1, cy))
            stack.push(intArrayOf(cx, cy + 1))
            stack.push(intArrayOf(cx, cy - 1))
        }
        return ColorRegion(minX, minY, maxX, maxY)
    }
}
