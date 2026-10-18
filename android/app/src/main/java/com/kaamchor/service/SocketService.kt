package com.kaamchor.service

import android.app.Notification
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.kaamchor.Graph
import com.kaamchor.KaamChorApp
import com.kaamchor.MainActivity
import com.kaamchor.R
import com.kaamchor.data.realtime.RealtimeEvent
import com.kaamchor.ui.call.CallActivity
import kotlinx.coroutines.launch

/**
 * Foreground service that keeps the realtime Socket.IO connection alive and
 * turns server-notification events into system notifications.
 *
 * Limitation (accepted): if the app is force-stopped by the OS/user there is no
 * delivery — reconnects happen when the app is reopened.
 */
class SocketService : LifecycleService() {

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        startAsForeground()
        Graph.socket.connect()
        observeEvents()
        return START_STICKY
    }

    private var observing = false

    private fun observeEvents() {
        if (observing) return
        observing = true
        lifecycleScope.launch {
            Graph.socket.events.collect { event ->
                when (event) {
                    is RealtimeEvent.Notification -> postNotification(event)
                    is RealtimeEvent.IncomingCall -> openCall(event.raw.toString())
                    else -> Unit
                }
            }
        }
    }

    private fun startAsForeground() {
        val notification = buildForegroundNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                FOREGROUND_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            startForeground(FOREGROUND_ID, notification)
        }
    }

    private fun buildForegroundNotification(): Notification {
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, KaamChorApp.CHANNEL_SERVICE)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentTitle("Kaam Chor")
            .setContentText("Connected — ready to receive messages")
            .setContentIntent(open)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
    }

    private fun postNotification(event: RealtimeEvent.Notification) {
        val channel = if (event.type == "call") KaamChorApp.CHANNEL_CALLS else KaamChorApp.CHANNEL_MESSAGES
        val open = PendingIntent.getActivity(
            this,
            event.raw.hashCode(),
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(this, channel)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentTitle(event.title)
            .setContentText(event.message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(event.message))
            .setAutoCancel(true)
            .setContentIntent(open)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        runCatching {
            NotificationManagerCompat.from(this).notify(event.raw.hashCode(), notification)
        }
    }

    private fun openCall(raw: String) {
        val intent = Intent(this, CallActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra("incoming_call", raw)
        }
        startActivity(intent)
    }

    companion object {
        private const val FOREGROUND_ID = 1001
    }
}
