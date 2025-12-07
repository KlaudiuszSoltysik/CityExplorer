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
import com.example.cityexplorer.data.dtos.SimpleLocation
import com.example.cityexplorer.data.repositories.HexagonRepository
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

const val MAX_SPEED_KMH = 15.0f
const val MIN_ACCURACY_METERS = 20.0f
const val SEND_BATCH_INTERVAL_MS = 15_000L

class LocationTrackingService : Service() {
    private val hexagonRepository: HexagonRepository by lazy {
        (applicationContext as CityExplorerApp).hexagonRepository
    }
    private val cacheService: CacheService by lazy {
        (applicationContext as CityExplorerApp).cacheService
    }
    private val tokenService: TokenService by lazy {
        (applicationContext as CityExplorerApp).tokenService
    }
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val locationBuffer = mutableListOf<SimpleLocation>()
    private val bufferMutex = Mutex()
    private lateinit var locationClient: FusedLocationProviderClient
    private var lastLocation: Location? = null

    override fun onBind(intent: Intent?): IBinder? = null

    // Initializes service
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
            ACTION_STOP -> {
                val stopIntent = Intent(ACTION_STOPPED_FROM_NOTIFICATION)

                stopIntent.setPackage(packageName)

                sendBroadcast(stopIntent)

                stopSelf()
            }
        }
        return START_STICKY
    }

    // Manages service lifecycle
    override fun onDestroy() {
        isRunning = false
        activeCity = null

        runBlocking {
            saveAndStop()
        }

        serviceScope.cancel()
        super.onDestroy()
    }

    // Manages service lifecycle
    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        runBlocking {
            saveAndStop()
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
            val typeToken = object : TypeToken<List<SimpleLocation>>() {}.type
            val cachedData = cacheService.getCachedData<List<SimpleLocation>>("location-buffer", typeToken)

            if (cachedData != null && cachedData.isNotEmpty()) {
                bufferMutex.withLock {
                    locationBuffer.addAll(cachedData)
                }
                Log.d("ServiceLogs", "Restored ${cachedData.size} points from cache")
                cacheService.saveToCache("location-buffer", "0", emptyList<SimpleLocation>())
            }

            locationClient.getLocationFlow()
                .collect { androidLocation ->
                    if (!androidLocation.hasAccuracy() || androidLocation.accuracy > MIN_ACCURACY_METERS) {
                        return@collect
                    }

                    bufferMutex.withLock {
                        val currentLast = lastLocation

                        if (currentLast != null) {
                            val timeDeltaMs = androidLocation.time - currentLast.time
                            if (timeDeltaMs > 0) {
                                val maxDist = calculateMaxDistance(timeDeltaMs)
                                val actualDist = androidLocation.distanceTo(currentLast)

                                if (actualDist <= maxDist) {
                                    val simpleLoc = SimpleLocation(
                                        androidLocation.latitude,
                                        androidLocation.longitude,
                                        androidLocation.time
                                    )
                                    locationBuffer.add(simpleLoc)
                                    lastLocation = androidLocation
                                    Log.d("ServiceLogs", "Point added ${locationBuffer.size}")
                                } else {
                                    Log.d("ServiceLogs", "Point rejected: too fast")
                                }
                            }
                        } else {
                            val simpleLoc = SimpleLocation(
                                androidLocation.latitude,
                                androidLocation.longitude,
                                androidLocation.time
                            )
                            locationBuffer.add(simpleLoc)
                            lastLocation = androidLocation
                        }
                    }
                }
        }

        serviceScope.launch {
            while (isActive) {
                delay(SEND_BATCH_INTERVAL_MS)
                sendBatchData()
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

    // Saves data during exploring
    private suspend fun sendBatchData() {
        val pointsToSend = bufferMutex.withLock {
            if (locationBuffer.isEmpty()) return@withLock emptyList<SimpleLocation>()
            ArrayList(locationBuffer)
        }

        if (pointsToSend.isEmpty()) return

        val token = tokenService.getToken()
        var uploadSuccess = false

        if (token != null) {
            try {
                val locationsDto = pointsToSend.map { it.toDto() }
                Log.d("ServiceLogs", "Loop: Sending batch of ${pointsToSend.size} points...")

                uploadSuccess = hexagonRepository.postLocationBatch(
                    PostLocationBatchDto(token, locationsDto)
                )
            } catch (e: Exception) {
                Log.e("ServiceLogs", "Loop: Upload failed: ${e.message}")
            }
        }

        if (uploadSuccess) {
            bufferMutex.withLock {
                locationBuffer.removeAll(pointsToSend)
            }
            Log.d("ServiceLogs", "Loop: Upload success, buffer cleared (RAM)")
        } else {
            Log.d("ServiceLogs", "Loop: Upload failed, keeping data in RAM for next try")
        }
    }

    // Saves all data when service is stopped
    private suspend fun saveAndStop() {
        Log.d("ServiceLogs", "Stopping service: starting final save procedure...")

        sendBatchData()

        val remainingPoints = bufferMutex.withLock {
            if (locationBuffer.isEmpty()) return@withLock emptyList<SimpleLocation>()
            ArrayList(locationBuffer)
        }

        if (remainingPoints.isNotEmpty()) {
            Log.d("ServiceLogs", "Stopping: Saving ${remainingPoints.size} remaining points to Cache")
            cacheService.saveToCache("location-buffer", "0", remainingPoints)
        } else {
            Log.d("ServiceLogs", "Stopping: Buffer empty, nothing to cache")
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
