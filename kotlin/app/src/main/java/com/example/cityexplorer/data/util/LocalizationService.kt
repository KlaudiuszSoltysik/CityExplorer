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

private const val LOCATION_UPDATE_INTERVAL_MS = 10_000L
private const val MIN_UPDATE_DISTANCE_METERS = 20f

@SuppressLint("MissingPermission")
fun FusedLocationProviderClient.getLocationFlow(): Flow<Location> = callbackFlow {

    val request = LocationRequest.Builder(
        Priority.PRIORITY_HIGH_ACCURACY,
        LOCATION_UPDATE_INTERVAL_MS
    ).apply {
        setMinUpdateDistanceMeters(MIN_UPDATE_DISTANCE_METERS)
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