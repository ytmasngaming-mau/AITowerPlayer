package com.aitowerplayer

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.aitowerplayer.util.ConfigManager
import com.aitowerplayer.util.LogManager

class AITowerApp : Application() {

    companion object {
        const val CHANNEL_ID = "ai_tower_player_channel"
        lateinit var instance: AITowerApp
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        ConfigManager.init(this)
        LogManager.init()
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_desc)
                setShowBadge(false)
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
    }
}
