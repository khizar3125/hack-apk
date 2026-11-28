package com.khizar.camerapulse

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import java.util.ArrayDeque
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Photoplethysmography (PPG) detector.
 *
 * The fingertip over a lit flash acts like a translucent window into blood flow.
 * Every heartbeat pushes a pressure wave through the capillaries, which slightly
 * changes how much red light the camera sees. This class extracts that rhythm
 * with classical DSP only: detrend -> smooth -> Hann window -> Discrete Fourier
 * Transform over the 45..198 BPM band -> dominant peak.
 */
class HeartRateAnalyzer(private val listener: Listener) : ImageAnalysis.Analyzer {

    interface Listener {
        fun onWaveform(normalized: Float)
        fun onBeat()
        fun onBpm(bpm: Int, confidence: Float)
        fun onFingerPresence(present: Boolean)
    }

    private val times = ArrayDeque<Long>()
    private val values = ArrayDeque<Float>()

    private var frameCounter = 0
    private var lastBpmPushAt = 0L
    private var smoothedBpm = 0f
    private val bpmHistory = ArrayDeque<Int>()

    private var lastNormalized = 0f
    private var wasAboveThreshold = false

    @Volatile
    var lastConfidence = 0f
        private set

    @Volatile
    var fingerPresent = false
        private set

    fun reset() {
        synchronized(this) {
            times.clear()
            values.clear()
            bpmHistory.clear()
            smoothedBpm = 0f
            lastConfidence = 0f
            lastNormalized = 0f
            wasAboveThreshold = false
        }
    }

    fun stableBpm(): Int? {
        synchronized(this) {
            if (bpmHistory.size < 3) return null
            val sorted = bpmHistory.sorted()
            return sorted[sorted.size / 2]
        }
    }

    override fun analyze(image: ImageProxy) {
        try {
            val plane = image.planes[0]
            val redMean = meanOfChannel(image)
            val now = System.nanoTime() / 1_000_000L
            process(redMean, now)
        } finally {
            image.close()
        }
    }

    private fun meanOfChannel(image: ImageProxy): Float {
        val plane = image.planes[0]
        val buffer = plane.buffer
        val rowStride = plane.rowStride
        val pixelStride = plane.pixelStride
        val width = image.width
        val height = image.height

        val x0 = width / 3
        val x1 = width * 2 / 3
        val y0 = height / 3
        val y1 = height * 2 / 3

        var sum = 0L
        var count = 0L
        var y = y0
        while (y < y1) {
            var x = x0
            val rowBase = y * rowStride
            while (x < x1) {
                sum += buffer.get(rowBase + x * pixelStride).toInt() and 0xFF
                count++
                x += 2
            }
            y += 2
        }
        return if (count == 0L) 0f else sum.toFloat() / count
    }

    private fun process(redMean: Float, nowMs: Long) {
        synchronized(this) {
            times.addLast(nowMs)
            values.addLast(redMean)
            while (times.isNotEmpty() && nowMs - times.first() > WINDOW_MS) {
                times.removeFirst()
                values.removeFirst()
            }

            frameCounter++

            fingerPresent = redMean > FINGER_RED_MIN
            if (frameCounter % 5 == 0) {
                listener.onFingerPresence(fingerPresent)
            }

            val n = values.size
            if (n < MIN_SAMPLES) {
                return
            }

            val arr = values.toFloatArray()

            var mean = 0f
            for (v in arr) mean += v
            mean /= n

            val detrended = FloatArray(n)
            val half = MA_WINDOW / 2
            for (i in 0 until n) {
                var s = 0f
                var c = 0
                for (j in max(0, i - half)..min(n - 1, i + half)) {
                    s += arr[j]
                    c++
                }
                detrended[i] = arr[i] - s / c
            }

            val smoothed = FloatArray(n)
            var acc = 0f
            for (i in 0 until n) {
                acc += detrended[i]
                if (i >= SMOOTH_WINDOW) acc -= detrended[i - SMOOTH_WINDOW]
                smoothed[i] = acc / min(i + 1, SMOOTH_WINDOW)
            }

            var peak = 0f
            for (v in smoothed) peak = max(peak, kotlin.math.abs(v))
            if (peak < 1e-3f) {
                listener.onWaveform(0f)
                return
            }

            val normalized = smoothed[n - 1] / peak
            lastNormalized = normalized
            listener.onWaveform(normalized)

            val beatThreshold = 0.55f
            if (!wasAboveThreshold && normalized > beatThreshold) {
                listener.onBeat()
                wasAboveThreshold = true
            } else if (wasAboveThreshold && normalized < 0.1f) {
                wasAboveThreshold = false
            }

            if (frameCounter % 3 != 0) return

            val start = max(0, n - DFT_SAMPLES)
            val m = n - start
            if (m < MIN_DFT_SAMPLES) return

            val window = FloatArray(m)
            for (i in 0 until m) {
                val w = 0.5f * (1f - cos(2.0 * PI * i / (m - 1)).toFloat())
                window[i] = smoothed[start + i] * w
            }

            var bestFreq = 0f
            var bestPower = 0f
            var totalPower = 0f
            var freq = FREQ_MIN
            while (freq <= FREQ_MAX) {
                val omega = 2.0 * PI * freq
                var re = 0f
                var im = 0f
                for (i in 0 until m) {
                    val angle = (omega * i).toFloat()
                    re += window[i] * cos(angle)
                    im -= window[i] * sin(angle)
                }
                val power = re * re + im * im
                totalPower += power
                if (power > bestPower) {
                    bestPower = power
                    bestFreq = freq
                }
                freq += FREQ_STEP
            }

            val confidence = if (totalPower > 0f) bestPower / totalPower else 0f
            lastConfidence = confidence
            val bpm = bestFreq * 60f

            if (confidence > CONFIDENCE_MIN) {
                smoothedBpm = if (smoothedBpm == 0f) bpm else smoothedBpm * 0.7f + bpm * 0.3f
                if (nowMs - lastBpmPushAt > BPM_PUSH_INTERVAL_MS) {
                    lastBpmPushAt = nowMs
                    bpmHistory.addLast(smoothedBpm.toInt())
                    if (bpmHistory.size > 10) bpmHistory.removeFirst()
                    listener.onBpm(smoothedBpm.toInt(), confidence)
                }
            }
        }
    }

    companion object {
        private const val WINDOW_MS = 12_000L
        private const val MIN_SAMPLES = 60
        private const val MA_WINDOW = 21
        private const val SMOOTH_WINDOW = 5
        private const val DFT_SAMPLES = 240
        private const val MIN_DFT_SAMPLES = 120
        private const val FREQ_MIN = 0.75f
        private const val FREQ_MAX = 3.3f
        private const val FREQ_STEP = 0.05f
        private const val CONFIDENCE_MIN = 0.15f
        private const val BPM_PUSH_INTERVAL_MS = 900L
        private const val FINGER_RED_MIN = 85f
    }
}
