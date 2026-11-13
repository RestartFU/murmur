package com.restartfu.murmur.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.view.View
import android.os.SystemClock
import androidx.core.graphics.withSave
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sin

/**
 * The floating dictation button, showing the Murmur mark: a lowercase "m" whose arches
 * fade from left to right. The mark reacts to the state:
 *
 * - idle: the mark at rest, lavender on dark;
 * - recording: red, and the quieter arches rise and brighten as you speak;
 * - transcribing: a lavender ring circles the button and a wave runs through the arches.
 *
 * Drawn by hand so the overlay needs no theme or inflation. The transcribing animation is
 * driven by the frame clock rather than an Animator. Animators freeze when the system's
 * animation scale is 0 ("Remove animations", battery saver), and this one is the only sign
 * that Murmur is working.
 */
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
    private val mark = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = MurmurMark.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(3f)
        strokeCap = Paint.Cap.ROUND
    }
    private val path = Path()
    private val oval = RectF()
    private val heights = FloatArray(MurmurMark.arches.size)

    var state = State.IDLE
        set(value) {
            if (field == value) return
            field = value
            level = 0f
            if (value == State.TRANSCRIBING) transcribingSince = SystemClock.uptimeMillis()
            contentDescription = when (value) {
                State.IDLE -> "Start dictation"
                State.RECORDING -> "Stop dictation"
                State.TRANSCRIBING -> "Transcribing"
            }
            invalidate()
        }

    private var level = 0f
    private var transcribingSince = 0L

    init {
        contentDescription = "Start dictation"
        isClickable = true
    }

    /** Feeds a microphone RMS level (0..1); smoothed so the mark breathes rather than flickers. */
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
            State.IDLE -> 0xF01C1924.toInt()
            State.RECORDING -> 0xFFE5484D.toInt()
            State.TRANSCRIBING -> 0xF8352B52.toInt()
        }
        canvas.drawCircle(cx, cy, buttonRadius, fill)

        // 0..1 through one ring revolution.
        val t = if (state == State.TRANSCRIBING) {
            ((SystemClock.uptimeMillis() - transcribingSince) % RING_PERIOD_MS) / RING_PERIOD_MS.toFloat()
        } else 0f
        val phase = (t * 2 * PI).toFloat()
        if (state == State.TRANSCRIBING) {
            val r = buttonRadius + dp(5f)
            oval.set(cx - r, cy - r, cx + r, cy + r)
            ring.color = 0x40C9B8FF
            canvas.drawCircle(cx, cy, r, ring)
            ring.color = MurmurMark.COLOR
            // The arc breathes between short and long while it circles.
            val sweep = 70f + 90f * (0.5f + 0.5f * sin(phase))
            canvas.drawArc(oval, t * 360f - 90f, sweep, false, ring)
        }

        val tallest = MurmurMark.arches[0].height
        MurmurMark.arches.forEachIndexed { i, arch ->
            heights[i] = when (state) {
                State.IDLE -> arch.height
                State.RECORDING -> arch.height + (tallest - arch.height) * level
                State.TRANSCRIBING -> arch.height * (1f + 0.45f * sin(2 * phase - i * 1.1f))
            }
        }
        val color = if (state == State.RECORDING) 0xFFFFFFFF.toInt() else MurmurMark.COLOR
        val fade = when (state) {
            State.IDLE -> MurmurMark.FADE
            State.RECORDING -> MurmurMark.FADE + (1f - MurmurMark.FADE) * level
            State.TRANSCRIBING -> 0.8f
        }
        mark.shader = MurmurMark.gradient(color, fade)

        val scale = dp(19f) / MurmurMark.WIDTH
        canvas.withSave {
            translate(cx, cy)
            scale(scale, scale)
            translate(-MurmurMark.CENTER_X, -MurmurMark.CENTER_Y)
            MurmurMark.buildPath(path, oval, heights)
            drawPath(path, mark)
        }

        if (state == State.TRANSCRIBING) postInvalidateOnAnimation()
    }

    private companion object {
        const val RING_PERIOD_MS = 1200L
    }
}

/** Geometry of the Murmur mark, in the 108-unit space of res/drawable/ic_launcher_foreground.xml. */
internal object MurmurMark {
    /** [left] edge, [width] of the arch, and [height] of its stems above the baseline. */
    class Arch(val left: Float, val width: Float, val height: Float)

    const val BASELINE = 72f
    const val STROKE = 7f
    const val COLOR = 0xFFC9B8FF.toInt()
    /** Opacity of the rightmost arch; the mark fades to it from full on the left. */
    const val FADE = 0.45f
    val arches = listOf(Arch(32f, 16f, 26f), Arch(48f, 16f, 18f), Arch(64f, 12f, 10f))
    const val WIDTH = 44f
    const val CENTER_X = 54f
    const val CENTER_Y = 55f

    fun gradient(color: Int, endAlpha: Float): Shader {
        val end = (color and 0x00FFFFFF) or ((endAlpha * 255).toInt() shl 24)
        return LinearGradient(arches.first().left, 0f, arches.last().let { it.left + it.width }, 0f, color, end, Shader.TileMode.CLAMP)
    }

    fun buildPath(path: Path, oval: RectF, heights: FloatArray) {
        path.reset()
        arches.forEachIndexed { i, arch ->
            val r = arch.width / 2
            val top = BASELINE - heights[i]
            path.moveTo(arch.left, BASELINE)
            path.lineTo(arch.left, top)
            oval.set(arch.left, top - r, arch.left + arch.width, top + r)
            path.arcTo(oval, 180f, 180f)
            path.lineTo(arch.left + arch.width, BASELINE)
        }
    }
}
