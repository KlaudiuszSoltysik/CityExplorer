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
import android.location.Location
import android.util.Log
import com.example.cityexplorer.CityExplorerApp
import com.example.cityexplorer.data.dtos.PostLocationBatchDto
import com.example.cityexplorer.data.dtos.toDto
import com.example.cityexplorer.data.repositories.HexagonRepository
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

const val MAX_SPEED_KMH = 15.0f
const val MIN_ACCURACY_METERS = 20.0f
const val SEND_BATCH_INTERVAL_MS = 60_000L

class LocationTrackingService : Service() {
    private val hexagonRepository: HexagonRepository by lazy {
        (applicationContext as CityExplorerApp).hexagonRepository
    }
    private val tokenService: TokenService by lazy {
        (applicationContext as CityExplorerApp).tokenService
    }
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val locationBuffer = mutableListOf<Location>()
    private val bufferMutex = Mutex()
    private lateinit var locationClient: FusedLocationProviderClient
    private var lastLocation: Location? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        locationClient = LocationServices.getFusedLocationProviderClient(this)
    }

    // Initializes service
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        isRunning = true

        when (intent?.action) {
            ACTION_START -> {
                val city = intent.getStringExtra(EXTRA_CITY) ?: ""
                activeCity = city

                startForegroundService(city)
                startTrackingLogic()
            }
            ACTION_STOP -> stopSelf()
        }
        return START_STICKY
    }

    // Manages service lifecycle
    override fun onDestroy() {
        isRunning = false
        activeCity = null

        runBlocking {
            saveAll()
        }

        serviceScope.cancel()

        super.onDestroy()

        val intent = Intent(ACTION_STOPPED_FROM_NOTIFICATION).apply {
            setPackage(packageName)
        }
        sendBroadcast(intent)
    }

    // Stops service when app is removed from recent apps
    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        runBlocking {
            saveAll()
        }
        stopSelf()
    }

    // Checks if used isn't moving too quick
    private fun calculateMaxDistance(intervalMs: Long): Float {
        val speedMetersPerSecond = MAX_SPEED_KMH / 3.6f
        val timeSeconds = intervalMs / 1000.0f
        return speedMetersPerSecond * timeSeconds
    }

    // Triggers localization stream
    private fun startTrackingLogic() {
        serviceScope.launch {
            // TODO: wrzucić z cache do buforu
            locationClient.getLocationFlow()
                .collect { location ->
                    bufferMutex.withLock {
                        if (location.hasAccuracy() && location.accuracy > MIN_ACCURACY_METERS) {
                            return@withLock
                        }

                        val currentLastLocation = lastLocation

                        if (currentLastLocation != null) {
                            val timeDeltaMs = location.time - currentLastLocation.time

                            if (timeDeltaMs > 0) {
                                val maxAllowedDistance = calculateMaxDistance(timeDeltaMs)
                                val actualDistance = location.distanceTo(currentLastLocation)

                                if (actualDistance <= maxAllowedDistance) {
                                    locationBuffer.add(location)
                                    lastLocation = location
                                } else {
                                    Log.d("ServiceLogs", "Odrzucono punkt: ${actualDistance}m w ${timeDeltaMs}ms (za szybko)")
                                }
                            }
                        } else {
                            locationBuffer.add(location)
                            lastLocation = location
                        }
                    }
                    Log.d("ServiceLogs", "New location buffered. Count: ${locationBuffer.size}")
                }
        }

        serviceScope.launch {
            while (isActive) {
                delay(SEND_BATCH_INTERVAL_MS)
                sendBatchData()
            }
        }
    }

    // Sends data to backend periodically
    private suspend fun sendBatchData() {
        val pointsToSend = bufferMutex.withLock {
            if (locationBuffer.isEmpty()) return@withLock emptyList<Location>()
            ArrayList(locationBuffer)
        }

        val token = tokenService.getToken()
        if (pointsToSend.isNotEmpty() && token != null) {
            try {
                val locations = pointsToSend.map { it.toDto() }
                Log.d("ServiceLogs", "Sending batch of ${pointsToSend.size} points...")
                val success = hexagonRepository.postLocationBatch(
                    PostLocationBatchDto(
                        token,
                        locations
                    )
                )

                if (success) {
                    bufferMutex.withLock {
                        locationBuffer.removeAll(pointsToSend)
                    }
                } else {
                    Log.e("ServiceLogs", "Failed to sync batch")
                }
            } catch (e: Exception) {
                Log.e("ServiceLogs", "Failed to sync batch: ${e.message}")
            }
        }
    }

    // Defines and starts notification
    private fun startForegroundService(city: String) {
        createNotificationChannel()

        val stopIntent = Intent(this, LocationTrackingService::class.java).apply {
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

    // Saves all data when service is stopped
    private suspend fun saveAll() {
        val pointsToSend = bufferMutex.withLock {
            if (locationBuffer.isEmpty()) return@withLock emptyList<Location>()
            ArrayList(locationBuffer)
        }

        val token = tokenService.getToken()
        if (pointsToSend.isNotEmpty() && token != null) {
            try {
                val locations = pointsToSend.map { it.toDto() }
                Log.d("ServiceLogs", "Sending batch of ${pointsToSend.size} points...")
                val success = hexagonRepository.postLocationBatch(
                    PostLocationBatchDto(
                        token,
                        locations
                    )
                )

                if (success) {
                    bufferMutex.withLock {
                        locationBuffer.removeAll(pointsToSend)
                    }
                } else {
                    // TODO: Save to cache
                    Log.e("ServiceLogs", "Failed to exit save")
                }
            } catch (e: Exception) {
                // TODO: Save to cache
                Log.e("ServiceLogs", "Failed to exit save: ${e.message}")
            }
        }
    }

    // Creates notification channel
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
        var isRunning = false
        var activeCity: String? = null
            private set
        const val CHANNEL_ID = "location_channel"
        const val NOTIFICATION_ID = 1
        const val ACTION_START = "ACTION_START"
        const val ACTION_STOP = "ACTION_STOP"
        const val ACTION_STOPPED_FROM_NOTIFICATION = "ACTION_STOPPED_FROM_NOTIF"
        const val EXTRA_CITY = "city"
    }
}
