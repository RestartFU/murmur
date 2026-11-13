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
import kotlin.math.abs

/**
 * Owns the accessibility overlay window that holds the mic button, keeps it docked just
 * above the keyboard, and lets the user drag it sideways. The horizontal position is
 * remembered as a distance from the right edge so it survives rotation.
 */
class OverlayController(
    private val service: AccessibilityService,
    private val onTap: () -> Unit,
) {
    private val wm = service.getSystemService(WindowManager::class.java)
    private val prefs = service.getSharedPreferences("overlay", Context.MODE_PRIVATE)
    private val density = service.resources.displayMetrics.density
    private val gap = (4 * density).toInt()
    private val defaultRightMargin = (8 * density).toInt()

    val button = MicButtonView(service)
    private var attached = false

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

    /** Shows (or moves) the button so it sits just above [keyboardBounds]. */
    fun showAbove(keyboardBounds: Rect) {
        val size = button.measuredWidth.takeIf { it > 0 } ?: run {
            button.measure(0, 0)
            button.measuredWidth
        }
        val screenWidth = wm.currentWindowMetrics.bounds.width()
        val right = prefs.getInt(KEY_RIGHT_MARGIN, defaultRightMargin)
        val x = (screenWidth - size - right).coerceIn(0, (screenWidth - size).coerceAtLeast(0))
        val y = (keyboardBounds.top - size - gap).coerceAtLeast(0)
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
        var downRawX = 0f
        var startX = 0
        var dragging = false
        button.setOnTouchListener { v, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downRawX = e.rawX
                    startX = params.x
                    dragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - downRawX
                    if (!dragging && abs(dx) > slop) dragging = true
                    if (dragging && attached) {
                        val screenWidth = wm.currentWindowMetrics.bounds.width()
                        params.x = (startX + dx.toInt()).coerceIn(0, screenWidth - v.width)
                        wm.updateViewLayout(button, params)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (dragging) {
                        val screenWidth = wm.currentWindowMetrics.bounds.width()
                        prefs.edit { putInt(KEY_RIGHT_MARGIN, screenWidth - v.width - params.x) }
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

    private companion object {
        const val KEY_RIGHT_MARGIN = "right_margin"
    }
}
