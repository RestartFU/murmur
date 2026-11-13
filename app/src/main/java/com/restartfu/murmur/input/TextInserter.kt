package com.restartfu.murmur.input

import android.accessibilityservice.AccessibilityService
import android.content.ClipData
import android.content.ClipboardManager
import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo

/** Inserts transcribed text at the cursor of the focused text field. */
object TextInserter {

    enum class Result { INSERTED, PASTED, COPIED_ONLY }

    fun insert(service: AccessibilityService, transcript: String): Result {
        val node = service.rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        if (node != null && node.isEditable && !node.isPassword && setTextAtCursor(node, transcript)) {
            return Result.INSERTED
        }
        // Fallback for fields that ignore ACTION_SET_TEXT (some WebViews, custom editors).
        val clipboard = service.getSystemService(ClipboardManager::class.java)
        clipboard.setPrimaryClip(ClipData.newPlainText("Murmur", transcript))
        if (node != null && node.performAction(AccessibilityNodeInfo.ACTION_PASTE)) {
            return Result.PASTED
        }
        return Result.COPIED_ONLY
    }

    private fun setTextAtCursor(node: AccessibilityNodeInfo, transcript: String): Boolean {
        val current = if (node.isShowingHintText) "" else node.text?.toString().orEmpty()
        var start = node.textSelectionStart
        var end = node.textSelectionEnd
        if (start !in 0..current.length || end !in 0..current.length) {
            start = current.length
            end = current.length
        }
        val from = minOf(start, end)
        val to = maxOf(start, end)
        val before = current.substring(0, from)
        val after = current.substring(to)
        val insertion = fitToContext(before, transcript, after)

        val args = Bundle().apply {
            putCharSequence(
                AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                before + insertion + after,
            )
        }
        if (!node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)) return false

        val cursor = from + insertion.length
        node.performAction(
            AccessibilityNodeInfo.ACTION_SET_SELECTION,
            Bundle().apply {
                putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, cursor)
                putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, cursor)
            },
        )
        return true
    }

    /** Adds the spaces a person would type around the inserted text. */
    internal fun fitToContext(before: String, transcript: String, after: String): String {
        var text = transcript.trim()
        if (text.isEmpty()) return text
        val prev = before.lastOrNull()
        if (prev != null && !prev.isWhitespace() && text[0] !in ".,!?;:)") text = " $text"
        val next = after.firstOrNull()
        if (next != null && !next.isWhitespace() && next !in ".,!?;:)") text = "$text "
        return text
    }
}
