package com.example.cityexplorer.data.util

import android.annotation.SuppressLint
import android.location.Location
import android.os.Looper
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.Priority
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

const val UPDATE_INTERVAL_MS = 30_000L
const val MIN_DIST_METERS = 50.0f

@SuppressLint("MissingPermission")
fun FusedLocationProviderClient.getLocationFlow(): Flow<Location> = callbackFlow {

    val request = LocationRequest.Builder(
        Priority.PRIORITY_HIGH_ACCURACY,
        UPDATE_INTERVAL_MS
    ).apply {
        setMinUpdateDistanceMeters(0f)
        setMinUpdateIntervalMillis(5_000L)
    }.build()

    val callback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.lastLocation?.let { location ->
                trySend(location)
            }
        }
    }

    requestLocationUpdates(request, callback, Looper.getMainLooper())

    awaitClose {
        removeLocationUpdates(callback)
    }
}
