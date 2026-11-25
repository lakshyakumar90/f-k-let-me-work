package com.lakshyakumar.flmw

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.lakshyakumar.flmw.data.FocusRepository

class FlmwApplication : Application() {

    lateinit var repository: FocusRepository
        private set

    override fun onCreate() {
        super.onCreate()
        repository = FocusRepository(applicationContext)
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_FOCUS_ID,
                "Active Focus Session",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows while flmw is actively blocking distracting websites and apps."
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    companion object {
        const val CHANNEL_FOCUS_ID = "flmw_focus_channel"
    }
}
