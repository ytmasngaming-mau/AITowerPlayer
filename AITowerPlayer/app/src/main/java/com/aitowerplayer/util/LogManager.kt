package com.aitowerplayer.util

import android.os.Handler
import android.os.Looper
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList

object LogManager {

    private val logs = CopyOnWriteArrayList<String>()
    private val listeners = CopyOnWriteArrayList<(String) -> Unit>()
    private val handler = Handler(Looper.getMainLooper())
    private val timeFmt = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    private const val MAX_LINES = 200

    fun init() {
        log("LogManager siap.")
    }

    fun log(message: String) {
        val line = "[${timeFmt.format(Date())}] $message"
        logs.add(line)
        if (logs.size > MAX_LINES) {
            logs.removeAt(0)
        }
        handler.post {
            listeners.forEach { it(line) }
        }
    }

    fun getAll(): String = logs.joinToString("\n")

    fun clear() {
        logs.clear()
        handler.post {
            listeners.forEach { it("") }
        }
    }

    fun addListener(listener: (String) -> Unit) {
        listeners.add(listener)
    }

    fun removeListener(listener: (String) -> Unit) {
        listeners.remove(listener)
    }
}
