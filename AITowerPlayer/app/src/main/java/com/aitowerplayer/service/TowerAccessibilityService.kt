package com.aitowerplayer.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import com.aitowerplayer.util.LogManager

/**
 * Accessibility Service for performing gestures (tap & long-press / hold).
 * Does NOT require root.
 */
class TowerAccessibilityService : AccessibilityService() {

    companion object {
        @Volatile
        var instance: TowerAccessibilityService? = null
            private set

        fun isRunning(): Boolean = instance != null
    }

    private val handler = Handler(Looper.getMainLooper())
    private var currentStroke: GestureDescription.StrokeDescription? = null
    private var isHolding = false

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        LogManager.log("Accessibility Service terhubung.")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // We don't rely on events for gameplay; MediaProjection + CV drives decisions.
    }

    override fun onInterrupt() {
        LogManager.log("Accessibility Service interrupted.")
        releaseHold()
    }

    override fun onDestroy() {
        releaseHold()
        instance = null
        LogManager.log("Accessibility Service dihentikan.")
        super.onDestroy()
    }

    /**
     * Perform a quick tap at (x, y).
     */
    fun tap(x: Float, y: Float, durationMs: Long = 50L, callback: ((Boolean) -> Unit)? = null) {
        val path = Path().apply { moveTo(x, y) }
        val stroke = GestureDescription.StrokeDescription(path, 0, durationMs)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        val ok = dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                callback?.invoke(true)
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                callback?.invoke(false)
            }
        }, null)
        if (!ok) callback?.invoke(false)
    }

    /**
     * Start a continuous hold (long press that stays down until releaseHold is called).
     * Uses continuing strokes to keep the finger down.
     */
    fun startHold(x: Float, y: Float) {
        if (isHolding) return
        isHolding = true
        continueHold(x, y)
    }

    private fun continueHold(x: Float, y: Float) {
        if (!isHolding) return
        val path = Path().apply { moveTo(x, y) }
        // Keep stroke relatively short and chain them so we can cancel quickly
        val stroke = GestureDescription.StrokeDescription(path, 0, 300L, true)
        currentStroke = stroke
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                if (isHolding) {
                    // Continue the hold with willContinue=true chain
                    continueHold(x, y)
                }
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                // If cancelled while we still want to hold, try again
                if (isHolding) {
                    handler.postDelayed({ continueHold(x, y) }, 30)
                }
            }
        }, null)
    }

    /**
     * Release any ongoing hold immediately.
     */
    fun releaseHold() {
        isHolding = false
        currentStroke = null
        // Dispatch a tiny cancel gesture to force finger up
        // (system will cancel ongoing willContinue stroke when we stop chaining)
    }

    fun isCurrentlyHolding(): Boolean = isHolding
}
