package com.khizar.camerapulse

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.exp

class PulseView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val heartPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFF4D6D.toInt()
        style = Paint.Style.FILL
    }

    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        alpha = 60
    }

    private val heartPath = Path()
    private var beatPhase = 0f
    private var bpm = 64f

    private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 1000L
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener {
            beatPhase = it.animatedValue as Float
            postInvalidateOnAnimation()
        }
    }

    init {
        restartAnimation()
    }

    fun setBpm(value: Int) {
        bpm = value.coerceIn(40, 200).toFloat()
        restartAnimation()
    }

    fun start() {
        if (!animator.isRunning) animator.start()
    }

    fun stop() {
        animator.cancel()
    }

    private fun restartAnimation() {
        val wasRunning = animator.isRunning
        animator.duration = (60_000f / bpm).toLong()
        if (wasRunning || !animator.isStarted) animator.start()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        buildHeartPath(w.toFloat(), h.toFloat())
        glowPaint.shader = RadialGradient(
            w / 2f, h / 2f, w / 1.4f,
            0x66FF4D6D, Color.TRANSPARENT, Shader.TileMode.CLAMP
        )
    }

    private fun buildHeartPath(w: Float, h: Float) {
        heartPath.reset()
        val cx = w / 2f
        val cy = h / 2f
        val s = w.coerceAtMost(h) * 0.36f
        heartPath.moveTo(cx, cy + s)
        heartPath.cubicTo(cx - 1.6f * s, cy - 0.1f * s, cx - 0.85f * s, cy - 1.1f * s, cx, cy - 0.35f * s)
        heartPath.cubicTo(cx + 0.85f * s, cy - 1.1f * s, cx + 1.6f * s, cy - 0.1f * s, cx, cy + s)
        heartPath.close()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val bump1 = exp(-((beatPhase - 0.12f) / 0.07f).let { it * it } * 100f)
        val bump2 = 0.45f * exp(-((beatPhase - 0.38f) / 0.07f).let { it * it } * 100f)
        val scale = 1f + 0.16f * (bump1 + bump2)

        canvas.save()
        canvas.scale(scale, scale, width / 2f, height / 2f)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), glowPaint)
        canvas.drawPath(heartPath, heartPaint)
        canvas.restore()
    }

    override fun onDetachedFromWindow() {
        animator.cancel()
        super.onDetachedFromWindow()
    }
}
