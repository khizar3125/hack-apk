package com.khizar.camerapulse

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import kotlin.math.max

class WaveformView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val samples = ArrayList<Float>(MAX_SAMPLES)

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFF7597.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 4f * resources.displayMetrics.density
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val glowPaint = Paint(linePaint).apply {
        alpha = 70
        strokeWidth = 9f * resources.displayMetrics.density
    }

    private val baselinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x339AA3C7
        strokeWidth = 1f * resources.displayMetrics.density
    }

    private val path = Path()

    fun addSample(normalized: Float) {
        samples.add(normalized.coerceIn(-1f, 1f))
        while (samples.size > MAX_SAMPLES) samples.removeAt(0)
        postInvalidateOnAnimation()
    }

    fun clear() {
        samples.clear()
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        val midY = h / 2f
        canvas.drawLine(0f, midY, w, midY, baselinePaint)
        if (samples.size < 2) return

        val stepX = w / max(MAX_SAMPLES - 1, 1)
        val firstIndex = MAX_SAMPLES - samples.size
        val amplitude = h * 0.38f

        path.reset()
        for (i in samples.indices) {
            val x = (firstIndex + i) * stepX
            val y = midY - samples[i] * amplitude
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        canvas.drawPath(path, glowPaint)
        canvas.drawPath(path, linePaint)
    }

    companion object {
        private const val MAX_SAMPLES = 300
    }
}
