package com.example.cityexplorer.data.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.location.Location
import android.os.IBinder
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.net.toUri
import com.example.cityexplorer.CityExplorerApp
import com.example.cityexplorer.MainActivity
import com.example.cityexplorer.R
import com.example.cityexplorer.data.dtos.LocationDto
import com.example.cityexplorer.data.dtos.PostLocationBatchDto
import com.example.cityexplorer.data.dtos.SimpleLocation
import com.example.cityexplorer.data.repositories.HexagonRepository
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

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

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val locationBuffer = mutableListOf<SimpleLocation>()
    private val bufferMutex = Mutex()
    private lateinit var locationClient: FusedLocationProviderClient
    private var lastLocation: Location? = null
    private var consecutiveFailedSendBatchData: Int = 0

    override fun onBind(intent: Intent?): IBinder? = null

    // Initializes service
    override fun onCreate() {
        super.onCreate()
        locationClient = LocationServices.getFusedLocationProviderClient(this)
    }

    // Initializes service
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        updateExplorationState("started")

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
        updateExplorationState("stopped")
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

        val notification = NotificationCompat.Builder(this, LOCATION_CHANNEL_ID)
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

        startForeground(LOCATION_NOTIFICATION_ID, notification)
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
            val cachedData =
                cacheService.getCachedData<List<SimpleLocation>>("location-buffer", typeToken)

            if (cachedData != null && cachedData.isNotEmpty()) {
                bufferMutex.withLock {
                    locationBuffer.addAll(cachedData)
                }
                cacheService.saveToCache("location-buffer", "0", emptyList<SimpleLocation>())
            }

            locationClient.getLocationFlow()
                .collect { androidLocation ->
                    if (consecutiveFailedSendBatchData >= 3) {
                        updateExplorationState("suspended")
                        showConnectionLostAlert()
                        return@collect
                    }

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

    // Saves data during exploring
    private suspend fun sendBatchData() {
        val pointsToSend = bufferMutex.withLock {
            if (locationBuffer.isEmpty()) return@withLock emptyList<SimpleLocation>()
            ArrayList(locationBuffer)
        }

        if (pointsToSend.isEmpty()) return

        var uploadSuccess = false

        val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }

        try {
            val locationsDtos = pointsToSend.map { point ->
                LocationDto(
                    latitude = point.latitude,
                    longitude = point.longitude,
                    timestamp = format.format(Date(point.timestamp))
                )
            }

            uploadSuccess = hexagonRepository.postLocationBatch(PostLocationBatchDto(locationsDtos))
        } catch (_: Exception) {
            consecutiveFailedSendBatchData++
        }


        if (uploadSuccess) {
            bufferMutex.withLock {
                locationBuffer.removeAll(pointsToSend)
            }
            consecutiveFailedSendBatchData = 0
            updateExplorationState("started")
        } else {
            consecutiveFailedSendBatchData++
        }
    }

    // Saves all data when service is stopped
    private suspend fun saveAndStop() {
        sendBatchData()

        val remainingPoints = bufferMutex.withLock {
            if (locationBuffer.isEmpty()) return@withLock emptyList<SimpleLocation>()
            ArrayList(locationBuffer)
        }

        if (remainingPoints.isNotEmpty()) {
            cacheService.saveToCache("location-buffer", "0", remainingPoints)
        }
    }

    // Creates notification channel
    private fun createNotificationChannel() {
        val locationChannel = NotificationChannel(
            LOCATION_CHANNEL_ID,
            "Location Tracking",
            NotificationManager.IMPORTANCE_LOW
        )

        val alertChannel = NotificationChannel(
            ALERT_CHANNEL_ID,
            "Alerts",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Connection errors and critical alerts"
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 500, 200, 500)
        }

        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannels(listOf(locationChannel, alertChannel))
    }

    // Shows notification about lost connection during exploring
    private fun showConnectionLostAlert() {
        val currentTime = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())

        val wifiIntent = Intent(Settings.ACTION_WIFI_SETTINGS)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            wifiIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, ALERT_CHANNEL_ID)
            .setContentTitle("Connection lost")
            .setContentText("Error while uploading data at $currentTime. Check internet access.")
            .setSmallIcon(R.drawable.baseline_explore_24)
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText("Error while uploading data at $currentTime. Check internet access."))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ERROR)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setVibrate(longArrayOf(0, 500, 200, 500))
            .build()

        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(ALERT_NOTIFICATION_ID, notification)
    }

    companion object {
        private val _exploringState = MutableStateFlow("stopped")
        var exploringState = _exploringState.asStateFlow()
        fun updateExplorationState(newState: String) {
            _exploringState.value = newState
        }
        var activeCity: String? = null
            private set
        const val LOCATION_CHANNEL_ID = "location_channel"
        const val ALERT_CHANNEL_ID = "alert_channel"

        const val LOCATION_NOTIFICATION_ID = 1
        const val ALERT_NOTIFICATION_ID = 2
        const val ACTION_START = "ACTION_START"
        const val ACTION_STOP = "ACTION_STOP"
        const val ACTION_STOPPED_FROM_NOTIFICATION = "ACTION_STOPPED_FROM_NOTIF"
        const val EXTRA_CITY = "city"
    }
}
