package com.restartfu.murmur.overlay

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.min

/** The floating mic button. Drawn by hand so the overlay needs no theme or inflation. */
@SuppressLint("ViewConstructor")
class MicButtonView(context: Context) : View(context) {

    enum class State { IDLE, RECORDING, TRANSCRIBING }

    private val density = resources.displayMetrics.density
    private fun dp(v: Float) = v * density

    private val buttonRadius = dp(22f)
    private val haloMax = dp(10f)
    private val viewSize = ((buttonRadius + haloMax) * 2).toInt()

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val halo = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glyph = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt() }
    private val glyphStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt()
        style = Paint.Style.STROKE
        strokeWidth = dp(1.8f)
        strokeCap = Paint.Cap.ROUND
    }
    private val spinner = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFC9B8FF.toInt()
        style = Paint.Style.STROKE
        strokeWidth = dp(2.5f)
        strokeCap = Paint.Cap.ROUND
    }
    private val rect = RectF()

    var state = State.IDLE
        set(value) {
            if (field == value) return
            field = value
            level = 0f
            if (value == State.TRANSCRIBING) spin.start() else spin.cancel()
            contentDescription = when (value) {
                State.IDLE -> "Start dictation"
                State.RECORDING -> "Stop dictation"
                State.TRANSCRIBING -> "Transcribing"
            }
            invalidate()
        }

    private var level = 0f
    private var angle = 0f
    private val spin = ValueAnimator.ofFloat(0f, 360f).apply {
        duration = 900
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener { angle = it.animatedValue as Float; invalidate() }
    }

    init {
        contentDescription = "Start dictation"
        isClickable = true
    }

    /** Feeds a microphone RMS level (0..1); smoothed so the halo breathes rather than flickers. */
    fun setLevel(rms: Float) {
        val target = min(1f, rms * 12f)
        level += (target - level) * if (target > level) 0.5f else 0.15f
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) =
        setMeasuredDimension(viewSize, viewSize)

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f

        if (state == State.RECORDING) {
            halo.color = 0x55E5484D
            canvas.drawCircle(cx, cy, buttonRadius + dp(3f) + level * (haloMax - dp(3f)), halo)
        }

        fill.color = when (state) {
            State.RECORDING -> 0xFFE5484D.toInt()
            else -> 0xF01C1924.toInt()
        }
        canvas.drawCircle(cx, cy, buttonRadius, fill)

        if (state == State.TRANSCRIBING) {
            val r = buttonRadius - dp(6f)
            rect.set(cx - r, cy - r, cx + r, cy + r)
            canvas.drawArc(rect, angle, 100f, false, spinner)
            canvas.drawArc(rect, angle + 180f, 100f, false, spinner)
        } else {
            drawMic(canvas, cx, cy)
        }
    }

    private fun drawMic(canvas: Canvas, cx: Float, cy: Float) {
        // Capsule
        rect.set(cx - dp(4f), cy - dp(9f), cx + dp(4f), cy + dp(3f))
        canvas.drawRoundRect(rect, dp(4f), dp(4f), glyph)
        // Cradle
        rect.set(cx - dp(7f), cy - dp(5f), cx + dp(7f), cy + dp(6f))
        canvas.drawArc(rect, 0f, 180f, false, glyphStroke)
        // Stem
        canvas.drawLine(cx, cy + dp(6f), cx, cy + dp(9.5f), glyphStroke)
    }

    override fun onDetachedFromWindow() {
        spin.cancel()
        super.onDetachedFromWindow()
    }
}
