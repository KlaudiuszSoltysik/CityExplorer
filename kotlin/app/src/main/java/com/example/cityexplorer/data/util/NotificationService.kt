package com.example.cityexplorer.data.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.net.toUri
import com.example.cityexplorer.MainActivity
import com.example.cityexplorer.R

class NotificationService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val city = intent.getStringExtra(EXTRA_CITY) ?: ""
                startForegroundService(city)
            }
            ACTION_STOP -> stopSelf()
        }
        return START_STICKY
    }

    private fun startForegroundService(city: String) {
        createNotificationChannel()

        val stopIntent = Intent(this, NotificationService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            0,
            stopIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        val contentIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            data = "cityexplorer://map/$city".toUri()
        }
        val contentPendingIntent = PendingIntent.getActivity(
            this,
            1,
            contentIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // 3. Build Notification
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("City Explorer")
            .setContentText("Tracking location in $city")
            .setSmallIcon(R.drawable.baseline_explore_24)
            .setContentIntent(contentPendingIntent)
            .setDeleteIntent(stopPendingIntent)
            .addAction(android.R.drawable.ic_delete, "Stop exploring", stopPendingIntent)
            .setOngoing(true)
            .setAutoCancel(false)
            .setUsesChronometer(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()

        startForeground(NOTIFICATION_ID, notification)
    }

    override fun onDestroy() {
        super.onDestroy()
        val intent = Intent(ACTION_STOPPED_FROM_NOTIFICATION).apply {
            setPackage(packageName)
        }
        sendBroadcast(intent)
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Location Tracking",
            NotificationManager.IMPORTANCE_LOW
        )
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    companion object {
        const val CHANNEL_ID = "location_channel"
        const val NOTIFICATION_ID = 1
        const val ACTION_START = "ACTION_START"
        const val ACTION_STOP = "ACTION_STOP"
        const val ACTION_STOPPED_FROM_NOTIFICATION = "ACTION_STOPPED_FROM_NOTIF"
        const val EXTRA_CITY = "city"
    }
}