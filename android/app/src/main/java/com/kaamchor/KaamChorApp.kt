package com.kaamchor

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.google.gson.Gson
import com.kaamchor.data.local.SessionStore
import com.kaamchor.data.remote.GsonProvider
import com.kaamchor.data.remote.UserDto
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class KaamChorApp : Application() {

    override fun onCreate() {
        super.onCreate()
        Graph.init(this)
        createNotificationChannels()

        // Restore the signed-in user id so realtime filters self-messages correctly.
        runBlocking {
            val json = Graph.session.userJson.first()
            if (!json.isNullOrBlank()) {
                val user = runCatching {
                    GsonProvider.gson.fromJson(json, UserDto::class.java)
                }.getOrNull()
                if (user != null) Graph.socket.setSelfUserId(user.id)
            }
        }
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_MESSAGES,
                getString(R.string.notif_channel_messages),
                NotificationManager.IMPORTANCE_HIGH,
            )
        )
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_CALLS,
                getString(R.string.notif_channel_calls),
                NotificationManager.IMPORTANCE_HIGH,
            )
        )
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_SERVICE,
                getString(R.string.notif_channel_service),
                NotificationManager.IMPORTANCE_MIN,
            )
        )
    }

    companion object {
        const val CHANNEL_MESSAGES = "messages"
        const val CHANNEL_CALLS = "calls"
        const val CHANNEL_SERVICE = "service"
    }
}
