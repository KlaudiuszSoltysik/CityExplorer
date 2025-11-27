package com.example.cityexplorer.data.repository

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

@SuppressLint("MissingPermission")
fun getLocationFlow(client: FusedLocationProviderClient): Flow<Location> = callbackFlow {
    val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 10000L).apply {
        setMinUpdateDistanceMeters(20f)
    }.build()

    val callback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.lastLocation?.let { location ->
                trySend(location)
            }
        }
    }

    client.requestLocationUpdates(request, callback, Looper.getMainLooper())

    awaitClose {
        client.removeLocationUpdates(callback)
    }
}