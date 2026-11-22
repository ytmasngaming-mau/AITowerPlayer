package com.aitowerplayer.util

import android.content.Context
import android.content.SharedPreferences
import com.aitowerplayer.model.AppConfig
import com.aitowerplayer.model.TaskItem
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

object ConfigManager {

    private const val PREF_NAME = "ai_tower_config"
    private lateinit var prefs: SharedPreferences
    private val gson = Gson()

    var config: AppConfig = AppConfig()
        private set

    var tasks: MutableList<TaskItem> = mutableListOf()
        private set

    var levelsCompleted: Int = 0
        private set
    var wins: Int = 0
        private set
    var fails: Int = 0
        private set

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        load()
    }

    fun load() {
        config = AppConfig(
            detectionLinePercent = prefs.getFloat("detectionLinePercent", 35f),
            blackThreshold = prefs.getInt("blackThreshold", 40),
            pixelThresholdPercent = prefs.getFloat("pixelThresholdPercent", 25f),
            bandHeightPx = prefs.getInt("bandHeightPx", 40),
            analysisFps = prefs.getInt("analysisFps", 15),
            animationWaitMs = prefs.getLong("animationWaitMs", 1500L),
            touchXPercent = prefs.getFloat("touchXPercent", 50f),
            touchYPercent = prefs.getFloat("touchYPercent", 70f),
            autoLevelEnabled = prefs.getBoolean("autoLevelEnabled", true),
            useOcr = prefs.getBoolean("useOcr", false)
        )
        levelsCompleted = prefs.getInt("levelsCompleted", 0)
        wins = prefs.getInt("wins", 0)
        fails = prefs.getInt("fails", 0)

        val tasksJson = prefs.getString("tasks", null)
        tasks = if (tasksJson != null) {
            val type = object : TypeToken<MutableList<TaskItem>>() {}.type
            gson.fromJson(tasksJson, type) ?: mutableListOf()
        } else {
            // Default sesuai layout screenshot Tower Smash (persen layar)
            // Level XX: tombol oranye di bawah layar kemenangan
            // SMASH!: tombol oranye di tengah popup ROYAL DECREE
            mutableListOf(
                TaskItem(
                    name = "Tombol Level (TOWER SMASHED)",
                    x = 50, y = 78,
                    delayMs = 1400, order = 1,
                    trigger = "AFTER_WIN",
                    usePercent = true
                ),
                TaskItem(
                    name = "Tombol SMASH! (popup)",
                    x = 50, y = 52,
                    delayMs = 900, order = 1,
                    trigger = "AFTER_POPUP",
                    usePercent = true
                )
            )
        }
    }

    fun saveConfig(newConfig: AppConfig) {
        config = newConfig
        prefs.edit().apply {
            putFloat("detectionLinePercent", config.detectionLinePercent)
            putInt("blackThreshold", config.blackThreshold)
            putFloat("pixelThresholdPercent", config.pixelThresholdPercent)
            putInt("bandHeightPx", config.bandHeightPx)
            putInt("analysisFps", config.analysisFps)
            putLong("animationWaitMs", config.animationWaitMs)
            putFloat("touchXPercent", config.touchXPercent)
            putFloat("touchYPercent", config.touchYPercent)
            putBoolean("autoLevelEnabled", config.autoLevelEnabled)
            putBoolean("useOcr", config.useOcr)
            apply()
        }
    }

    fun saveTasks() {
        prefs.edit().putString("tasks", gson.toJson(tasks)).apply()
    }

    fun addTask(task: TaskItem) {
        tasks.add(task)
        tasks.sortBy { it.order }
        saveTasks()
    }

    fun updateTask(task: TaskItem) {
        val idx = tasks.indexOfFirst { it.id == task.id }
        if (idx >= 0) {
            tasks[idx] = task
            tasks.sortBy { it.order }
            saveTasks()
        }
    }

    fun removeTask(id: String) {
        tasks.removeAll { it.id == id }
        saveTasks()
    }

    fun incrementWin() {
        wins++
        levelsCompleted++
        prefs.edit()
            .putInt("wins", wins)
            .putInt("levelsCompleted", levelsCompleted)
            .apply()
    }

    fun incrementFail() {
        fails++
        prefs.edit().putInt("fails", fails).apply()
    }

    fun resetStats() {
        levelsCompleted = 0
        wins = 0
        fails = 0
        prefs.edit()
            .putInt("levelsCompleted", 0)
            .putInt("wins", 0)
            .putInt("fails", 0)
            .apply()
    }
}
