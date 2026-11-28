package com.khizar.camerapulse

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.SweepGradient
import android.util.AttributeSet
import android.view.View
import kotlin.math.min

class ProgressRingView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 10f * resources.displayMetrics.density
        color = 0x22FF7597
        strokeCap = Paint.Cap.ROUND
    }

    private val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 10f * resources.displayMetrics.density
        strokeCap = Paint.Cap.ROUND
    }

    private val rect = RectF()
    private var progress = 0f

    fun setProgress(value: Float) {
        progress = value.coerceIn(0f, 1f)
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val inset = trackPaint.strokeWidth
        rect.set(inset, inset, w - inset, h - inset)
        progressPaint.shader = SweepGradient(
            w / 2f, h / 2f,
            0xFFFF4D6D, 0xFFFF7597
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val sweep = 360f * progress
        canvas.drawArc(rect, 0f, 360f, false, trackPaint)
        if (sweep > 2f) {
            canvas.drawArc(rect, -90f, sweep, false, progressPaint)
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val size = min(
            MeasureSpec.getSize(widthMeasureSpec),
            MeasureSpec.getSize(heightMeasureSpec)
        )
        setMeasuredDimension(size, size)
    }
}
