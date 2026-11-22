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
    @Volatile
    private var isHolding = false
    private var chainBusy = false
    private var holdX = 0f
    private var holdY = 0f
    private val chunkMs = 60L

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
     * Tahan sentuhan terus-menerus. Jari tetap "satu sentuhan" lewat continueStroke,
     * dipecah potongan pendek (60ms) supaya bisa diangkat cepat saat releaseHold().
     */
    fun startHold(x: Float, y: Float) {
        if (isHolding) return
        holdX = x
        holdY = y
        isHolding = true
        handler.post {
            if (isHolding && !chainBusy) sendChunk()
        }
    }

    private fun retryChunk() {
        handler.postDelayed({
            if (isHolding && !chainBusy) sendChunk()
        }, 25)
    }

    private fun sendChunk() {
        val keepDown = isHolding
        val prev = currentStroke
        if (!keepDown && prev == null) return

        val path = Path().apply { moveTo(holdX, holdY) }
        val stroke = try {
            if (prev == null) {
                GestureDescription.StrokeDescription(path, 0, chunkMs, true)
            } else {
                prev.continueStroke(path, 0, if (keepDown) chunkMs else 20L, keepDown)
            }
        } catch (e: Exception) {
            currentStroke = null
            if (isHolding) retryChunk()
            return
        }

        currentStroke = if (keepDown) stroke else null
        chainBusy = true
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        val ok = dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                chainBusy = false
                // Masih menahan -> lanjut. Sudah diminta lepas -> kirim potongan terakhir (jari naik).
                if (isHolding || currentStroke != null) sendChunk()
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                chainBusy = false
                currentStroke = null
                if (isHolding) retryChunk()
            }
        }, handler)
        if (!ok) {
            chainBusy = false
            currentStroke = null
            if (isHolding) retryChunk()
        }
    }

    /**
     * Lepas sentuhan. Jari benar-benar diangkat paling lama sekitar 60-80ms.
     */
    fun releaseHold() {
        isHolding = false
        handler.post {
            if (!chainBusy && currentStroke != null) sendChunk()
        }
    }

    fun isCurrentlyHolding(): Boolean = isHolding
}
