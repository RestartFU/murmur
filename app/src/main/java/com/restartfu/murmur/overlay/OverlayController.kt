package com.restartfu.murmur.overlay

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.graphics.Rect
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.WindowManager
import androidx.core.content.edit
import kotlin.math.hypot

/**
 * Owns the accessibility overlay window that holds the dictation button and keeps it docked
 * above the keyboard. The user can drag it anywhere above the keyboard. The position is
 * remembered as distances from the right edge and from the keyboard, so it survives
 * rotation and keyboards of different heights.
 */
class OverlayController(
    private val service: AccessibilityService,
    private val onTap: () -> Unit,
) {
    private val wm = service.getSystemService(WindowManager::class.java)
    private val prefs = service.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val density = service.resources.displayMetrics.density
    private val gap = (4 * density).toInt()
    private val defaultRightMargin = (8 * density).toInt()

    val button = MicButtonView(service)
    private var attached = false
    private var keyboardTop = 0

    private val params = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        setFitInsetsTypes(0)
        title = "Murmur"
    }

    init {
        attachTouchHandling()
    }

    val isShowing get() = attached

    /**
     * Shows (or moves) the button above the keyboard. If the focused text field sits
     * directly on the keyboard, as in most chat apps, the button goes above the field
     * instead, so it doesn't cover the app's send button.
     */
    fun showAbove(keyboardBounds: Rect, focusedField: Rect?) {
        val size = button.measuredWidth.takeIf { it > 0 } ?: run {
            button.measure(0, 0)
            button.measuredWidth
        }
        val screenWidth = wm.currentWindowMetrics.bounds.width()
        val right = prefs.getInt(KEY_RIGHT_MARGIN, defaultRightMargin)
        val x = (screenWidth - size - right).coerceIn(0, (screenWidth - size).coerceAtLeast(0))
        keyboardTop = keyboardBounds.top
        val y = placeY(
            keyboardTop = keyboardBounds.top,
            fieldTop = focusedField?.top,
            fieldBottom = focusedField?.bottom,
            buttonSize = size,
            gap = gap,
            density = density,
            aboveKeyboard = prefs.getInt(KEY_ABOVE_KEYBOARD, -1).takeIf { it >= 0 },
        )
        if (attached && params.x == x && params.y == y) return
        params.x = x
        params.y = y
        if (attached) wm.updateViewLayout(button, params) else {
            wm.addView(button, params)
            attached = true
        }
    }

    fun hide() {
        if (!attached) return
        wm.removeViewImmediate(button)
        attached = false
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun attachTouchHandling() {
        // Clicks also arrive from TalkBack and switch access, not just touch.
        button.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            onTap()
        }
        val slop = ViewConfiguration.get(service).scaledTouchSlop
        var downX = 0f
        var downY = 0f
        var startX = 0
        var startY = 0
        var dragging = false
        button.setOnTouchListener { v, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.rawX
                    downY = e.rawY
                    startX = params.x
                    startY = params.y
                    dragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - downX
                    val dy = e.rawY - downY
                    if (!dragging && hypot(dx, dy) > slop) {
                        dragging = true
                        v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    }
                    if (dragging && attached) {
                        val screenWidth = wm.currentWindowMetrics.bounds.width()
                        params.x = (startX + dx.toInt()).coerceIn(0, (screenWidth - v.width).coerceAtLeast(0))
                        // Anywhere above the keyboard, never on top of it.
                        params.y = (startY + dy.toInt()).coerceIn(0, (keyboardTop - v.height).coerceAtLeast(0))
                        wm.updateViewLayout(button, params)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (dragging) {
                        val screenWidth = wm.currentWindowMetrics.bounds.width()
                        prefs.edit {
                            putInt(KEY_RIGHT_MARGIN, screenWidth - v.width - params.x)
                            putInt(KEY_ABOVE_KEYBOARD, keyboardTop - v.height - params.y)
                        }
                    } else {
                        v.performClick()
                    }
                    true
                }
                MotionEvent.ACTION_CANCEL -> true
                else -> false
            }
        }
    }

    companion object {
        private const val PREFS = "overlay"
        private const val KEY_RIGHT_MARGIN = "right_margin"
        private const val KEY_ABOVE_KEYBOARD = "above_keyboard"

        /** Forgets where the user dragged the button; it docks automatically again. */
        fun resetPosition(context: Context) =
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { clear() }

        fun hasCustomPosition(context: Context) =
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).all.isNotEmpty()

        /**
         * The top edge of the button window, in screen pixels: where the user dragged it
         * ([aboveKeyboard] = gap between button and keyboard), or [dockY] if they never did.
         */
        internal fun placeY(
            keyboardTop: Int,
            fieldTop: Int?,
            fieldBottom: Int?,
            buttonSize: Int,
            gap: Int,
            density: Float,
            aboveKeyboard: Int?,
        ): Int {
            if (aboveKeyboard == null) return dockY(keyboardTop, fieldTop, fieldBottom, buttonSize, gap, density)
            return (keyboardTop - buttonSize - aboveKeyboard).coerceIn(0, (keyboardTop - buttonSize).coerceAtLeast(0))
        }

        /** How far above the keyboard a field may end and still count as "sitting on" it. */
        private const val FIELD_NEAR_KEYBOARD_DP = 96
        /** Taller fields (an email body, a notes page) keep the button on the keyboard. */
        private const val FIELD_MAX_HEIGHT_DP = 160

        /** The top edge of the button window, in screen pixels. */
        internal fun dockY(
            keyboardTop: Int,
            fieldTop: Int?,
            fieldBottom: Int?,
            buttonSize: Int,
            gap: Int,
            density: Float,
        ): Int {
            var anchor = keyboardTop
            if (fieldTop != null && fieldBottom != null) {
                val sitsOnKeyboard = fieldBottom <= keyboardTop + gap &&
                    keyboardTop - fieldBottom <= FIELD_NEAR_KEYBOARD_DP * density
                val compact = fieldBottom - fieldTop <= FIELD_MAX_HEIGHT_DP * density
                if (sitsOnKeyboard && compact) anchor = minOf(anchor, fieldTop)
            }
            return (anchor - buttonSize - gap).coerceAtLeast(0)
        }
    }
}
