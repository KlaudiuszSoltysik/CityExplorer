package com.example.cityexplorer.ui.map

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.cityexplorer.data.dtos.GetCityHexagonsDataDto
import com.example.cityexplorer.ui.theme.CustomBlack
import com.example.cityexplorer.ui.theme.CustomWhite
import com.example.cityexplorer.ui.theme.CustomYellow
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Polygon
import com.google.maps.android.compose.rememberCameraPositionState
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import androidx.compose.runtime.DisposableEffect
import com.example.cityexplorer.LocationService

@Composable
fun MapScreen(
    modifier: Modifier = Modifier,
    city: String,
    mode: String,
    locationClient: FusedLocationProviderClient,
    viewModel: MapViewModel = viewModel(factory = MapViewModelFactory(city, mode, locationClient))
) {
    val uiState = viewModel.uiState
    val isRefreshing = viewModel.isRefreshing

    var selectedHexId by remember { mutableStateOf<String?>(null) }
    var isExploringMode by remember { mutableStateOf(false) }

    val context = LocalContext.current

    var arePermissionsGranted by remember {
        mutableStateOf(false)
    }

    fun checkPermissions(): Boolean {
        val hasLocation = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val hasNotification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

        return hasLocation && hasNotification
    }

    fun toggleService(enable: Boolean) {
        Intent(context, LocationService::class.java).also { intent ->
            if (enable) {
                intent.action = LocationService.ACTION_START
                context.startForegroundService(intent)
                viewModel.startLocationTracking()
            } else {
                intent.action = LocationService.ACTION_STOP
                context.startService(intent)
            }
        }
    }

    fun isLocationInBbox(location: android.location.Location?, bbox: List<Double>): Boolean {
        if (location == null) return false

        return location.latitude in bbox[0]..bbox[2] &&
                location.longitude in bbox[1]..bbox[3]
    }

    LaunchedEffect(Unit) {
        arePermissionsGranted = checkPermissions()
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val locationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true

        val notificationGranted = permissions[Manifest.permission.POST_NOTIFICATIONS] == true

        arePermissionsGranted = locationGranted && notificationGranted
    }

    LaunchedEffect(Unit) {
        if (!checkPermissions()) {
            val permissionsToRequest = mutableListOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.POST_NOTIFICATIONS
            )

            permissionLauncher.launch(permissionsToRequest.toTypedArray())
        }
    }

    LaunchedEffect(arePermissionsGranted) {
        if (arePermissionsGranted) {
            viewModel.startLocationTracking()
        }
    }

    DisposableEffect(Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == LocationService.ACTION_STOPPED_FROM_NOTIFICATION) {
                    isExploringMode = false
                }
            }
        }
        val filter = IntentFilter(LocationService.ACTION_STOPPED_FROM_NOTIFICATION)
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)

        onDispose {
            context.unregisterReceiver(receiver)

            if (isExploringMode) {
                val stopIntent = Intent(context, LocationService::class.java)
                stopIntent.action = LocationService.ACTION_STOP
                context.startService(stopIntent)
            }
        }
    }

    PullToRefreshBox(
        modifier = Modifier.fillMaxSize(),
        isRefreshing = isRefreshing,
        onRefresh = { viewModel.refreshData() },
        contentAlignment = Alignment.Center
    ) {
        when (uiState) {
            is MainUiState.Loading -> CircularProgressIndicator()
            is MainUiState.Success -> {
                val isUserInCity by remember(viewModel.userLocation, uiState.data.bbox) {
                    derivedStateOf { isLocationInBbox(viewModel.userLocation, uiState.data.bbox) }
                }

                HexMap(
                    isUserInCity = isUserInCity,
                    data = uiState.data,
                    selectedHexId = selectedHexId,
                    onHexClick = { id ->
                        selectedHexId = if (selectedHexId == id) null else id
                    }
                )

                Box (
                    modifier = modifier
                        .fillMaxSize()
                ) {
                    val selectedHexagon = uiState.data.hexagons.find { it.id == selectedHexId }

                    if (selectedHexagon != null) {
                        val displayText = "ID: ${selectedHexagon.id}   weight: ${"%.3f".format(selectedHexagon.weight * 100)}%"

                        Text(
                            text = displayText,
                            color = CustomWhite,
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .background(
                                    CustomBlack.copy(alpha = 0.6f),
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .padding(vertical = 8.dp, horizontal = 16.dp)
                        )
                    }

                    if (arePermissionsGranted) {
                        Button(
                            onClick = {
                                isExploringMode = !isExploringMode
                                toggleService(isExploringMode)
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = CustomBlack.copy(alpha = 0.6f),
                                contentColor = CustomWhite
                            ),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 16.dp)
                        ) {
                            Text(text = if (isExploringMode) "Stop exploring!" else "Start exploring!")
                        }
                    }
                }
            }
            is MainUiState.Error -> {
                Box(
                    modifier = Modifier
                        .verticalScroll(rememberScrollState()),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "Connection error.")
                }
            }
        }
    }
}

@Composable
fun HexMap(
    isUserInCity: Boolean,
    data: GetCityHexagonsDataDto,
    selectedHexId: String?,
    onHexClick: (String) -> Unit
) {
    val center = LatLng((data.bbox[0] + data.bbox[2]) / 2, (data.bbox[1] + data.bbox[3]) / 2)

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(center, 11f)
    }

    val bounds = LatLngBounds(
        LatLng(data.bbox[0], data.bbox[1]),
        LatLng(data.bbox[2], data.bbox[3])
    )

    val rawJsonStyle = """
    [
      { "featureType": "all", "elementType": "labels", "stylers": [ { "visibility": "off" } ] },
      { "featureType": "poi", "stylers": [ { "visibility": "off" } ] },
      { "featureType": "transit", "stylers": [ { "visibility": "off" } ] }
    ]
    """

    val mapProperties = remember(isUserInCity) {
        MapProperties(
            mapStyleOptions = MapStyleOptions(rawJsonStyle),
            latLngBoundsForCameraTarget = bounds,
            maxZoomPreference = 16f,
            minZoomPreference = 9f,
            isMyLocationEnabled = isUserInCity
        )
    }

    val mapUiSettings = MapUiSettings(
        zoomControlsEnabled = false,
        compassEnabled = false,
        rotationGesturesEnabled = false,
        tiltGesturesEnabled = false,
        myLocationButtonEnabled = false
    )

    GoogleMap(
        cameraPositionState = cameraPositionState,
        properties = mapProperties,
        uiSettings = mapUiSettings
    ) {
        data.hexagons.forEach { hexagon ->
            val fillAlpha = if (hexagon.id == selectedHexId) 0.2f else 0.1f

            Polygon(
                points = hexagon.boundaries.map { point ->
                    LatLng(point[0], point[1])
                },
                strokeWidth = 1f,
                strokeColor = CustomBlack,
                fillColor = CustomYellow.copy(alpha = fillAlpha),
                clickable = true,
                onClick = {
                    onHexClick(hexagon.id)
                }
            )
        }
    }
}