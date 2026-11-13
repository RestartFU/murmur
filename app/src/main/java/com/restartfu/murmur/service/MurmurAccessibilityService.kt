package com.restartfu.murmur.service

import android.Manifest
import android.accessibilityservice.AccessibilityService
import android.content.ComponentCallbacks2
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.HapticFeedbackConstants
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import android.widget.Toast
import com.restartfu.murmur.MainActivity
import com.restartfu.murmur.asr.Transcriber
import com.restartfu.murmur.audio.Recorder
import com.restartfu.murmur.input.TextInserter
import com.restartfu.murmur.model.ModelManager
import com.restartfu.murmur.overlay.MicButtonView.State
import com.restartfu.murmur.overlay.OverlayController
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Watches for a keyboard window, docks the dictation button above it, records on tap,
 * transcribes with Parakeet and types the result into the focused field.
 *
 * Microphone access works without a foreground service: the system binds accessibility
 * services with BIND_INCLUDE_CAPABILITIES, which grants while-in-use access while the
 * screen is on.
 */
class MurmurAccessibilityService : AccessibilityService() {

    private val scope = MainScope()
    private val main = Handler(Looper.getMainLooper())
    private lateinit var overlay: OverlayController
    private var recorder: Recorder? = null
    private var state = State.IDLE
    private var keyboardVisible = false

    private val unloadModel = Runnable {
        if (state == State.IDLE) scope.launch { Transcriber.unload() }
    }

    override fun onServiceConnected() {
        overlay = OverlayController(this, ::onTap)
        updateKeyboard()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOWS_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_VIEW_FOCUSED -> updateKeyboard()
        }
    }

    override fun onInterrupt() = Unit

    private fun updateKeyboard() {
        if (!::overlay.isInitialized) return
        val bounds = windows
            .firstOrNull { it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD }
            ?.let { w -> Rect().also(w::getBoundsInScreen) }
            ?.takeIf { it.height() > 0 }

        if (bounds != null) {
            val field = rootInActiveWindow
                ?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
                ?.let { node -> Rect().also(node::getBoundsInScreen) }
            overlay.showAbove(bounds, field)
            if (!keyboardVisible) onKeyboardShown()
            keyboardVisible = true
        } else {
            keyboardVisible = false
            // Keep the button up while busy so the user can still stop the recording
            // and see the result land.
            if (state == State.IDLE) overlay.hide()
            scheduleUnload()
        }
    }

    private fun onKeyboardShown() {
        main.removeCallbacks(unloadModel)
        if (ModelManager.isInstalled(this) && !Transcriber.isLoaded) {
            scope.launch { runCatching { Transcriber.warmUp(applicationContext) } }
        }
    }

    private fun onTap() {
        when (state) {
            State.IDLE -> startRecording()
            State.RECORDING -> finishRecording()
            State.TRANSCRIBING -> Unit
        }
    }

    private fun startRecording() {
        if (!ModelManager.isInstalled(this)) {
            toast("Open Murmur to download the speech model first.")
            openApp()
            return
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            toast("Murmur needs microphone access.")
            openApp()
            return
        }
        val rec = Recorder(
            onLevel = { level -> main.post { if (state == State.RECORDING) overlay.button.setLevel(level) } },
            onLimit = { main.post { if (state == State.RECORDING) finishRecording() } },
        )
        if (!rec.start()) {
            toast("The microphone is unavailable. Another app may be using it.")
            return
        }
        recorder = rec
        setState(State.RECORDING)
    }

    private fun finishRecording() {
        val recording = recorder?.stop() ?: return
        recorder = null
        if (recording.seconds < MIN_SECONDS) {
            setState(State.IDLE)
            return
        }
        if (recording.peak == 0f) {
            toast("The microphone returned only silence. Another app may be using it.")
            setState(State.IDLE)
            return
        }
        setState(State.TRANSCRIBING)
        scope.launch {
            try {
                val text = Transcriber.transcribe(applicationContext, recording.samples)
                if (text.isBlank()) {
                    haptic(HapticFeedbackConstants.REJECT)
                    toast("Didn't catch that. Try again a little closer to the mic.")
                } else {
                    haptic(HapticFeedbackConstants.CONFIRM)
                    when (TextInserter.insert(this@MurmurAccessibilityService, text)) {
                        TextInserter.Result.INSERTED, TextInserter.Result.PASTED -> Unit
                        TextInserter.Result.COPIED_ONLY -> toast("Copied to clipboard. This field doesn't accept text from Murmur.")
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                haptic(HapticFeedbackConstants.REJECT)
                toast("Transcription failed: ${e.message}")
            } finally {
                setState(State.IDLE)
                if (!keyboardVisible) overlay.hide()
                scheduleUnload()
            }
        }
    }

    private fun setState(new: State) {
        state = new
        overlay.button.state = new
    }

    private fun scheduleUnload() {
        main.removeCallbacks(unloadModel)
        main.postDelayed(unloadModel, IDLE_UNLOAD_MS)
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        @Suppress("DEPRECATION")
        if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW && state == State.IDLE && !keyboardVisible) {
            scope.launch { Transcriber.unload() }
        }
    }

    override fun onDestroy() {
        recorder?.stop()
        recorder = null
        main.removeCallbacksAndMessages(null)
        if (::overlay.isInitialized) overlay.hide()
        scope.launch { Transcriber.unload() }.invokeOnCompletion { scope.cancel() }
        super.onDestroy()
    }

    private fun openApp() {
        startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun haptic(feedback: Int) {
        overlay.button.performHapticFeedback(feedback)
    }

    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_LONG).show()

    companion object {
        private const val MIN_SECONDS = 0.3f
        private const val IDLE_UNLOAD_MS = 5 * 60 * 1000L

        fun isEnabled(context: Context): Boolean {
            val enabled = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            ) ?: return false
            val me = "${context.packageName}/${MurmurAccessibilityService::class.java.name}"
            val meShort = "${context.packageName}/.service.${MurmurAccessibilityService::class.java.simpleName}"
            return enabled.split(':').any { it.equals(me, true) || it.equals(meShort, true) }
        }
    }
}
