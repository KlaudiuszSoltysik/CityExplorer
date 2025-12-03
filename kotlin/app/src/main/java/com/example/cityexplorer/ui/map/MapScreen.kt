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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import android.location.Location
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.example.cityexplorer.data.util.NotificationService
import com.example.cityexplorer.R
import com.example.cityexplorer.data.util.TokenService
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationSearching
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.example.cityexplorer.data.util.CacheService
import com.example.cityexplorer.ui.theme.CustomError
import com.example.cityexplorer.ui.theme.CustomSuccess
import com.example.cityexplorer.ui.theme.CustomWarning
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.tasks.Task
import com.google.maps.android.compose.CameraMoveStartedReason
import kotlinx.coroutines.launch

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
fun MapScreen(
    modifier: Modifier = Modifier,
    city: String,
    locationClient: FusedLocationProviderClient,
    tokenService: TokenService,
    cacheService: CacheService,
    onNavigateToLogin: () -> Unit,
    onNavigateBack: () -> Unit,
    viewModel: MapViewModel = viewModel(factory = MapViewModelFactory(city, locationClient, tokenService, cacheService))
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val uiState = viewModel.uiState
    val isRefreshing = viewModel.isRefreshing
    val isUserInCity = viewModel.isUserInCity
    val hexagonPois = viewModel.hexagonPois
    val arePermissionsGranted = viewModel.arePermissionsGranted
    val isExploringMode = viewModel.isExploringMode
    var selectedHexagonId by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    var lastBackPressTime by remember { mutableLongStateOf(0L) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val isGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true &&
                permissions[Manifest.permission.POST_NOTIFICATIONS] == true

        viewModel.updatePermissionStatus(isGranted)

        if (isGranted) {
            viewModel.onExplorerToggleClick()
        }
    }

    fun toggleLocalizationService(enable: Boolean) {
        Intent(context, NotificationService::class.java).also { intent ->
            if (enable) {
                intent.action = NotificationService.ACTION_START

                intent.putExtra("city", city)

                context.startForegroundService(intent)
            } else {
                intent.action = NotificationService.ACTION_STOP
                context.startService(intent)
            }
        }
    }

    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.uiEvent.collect { event ->
                when (event) {
                    is MapUiEvent.ToggleService -> {
                        toggleLocalizationService(event.shouldStart)
                    }
                    is MapUiEvent.NavigateToLogin -> {
                        onNavigateToLogin()
                    }
                    is MapUiEvent.ShowError -> {
                        Toast.makeText(
                            context,
                            event.message,
                            Toast.LENGTH_LONG
                        ).show()
                    }
                    is MapUiEvent.RequestPermissions -> {
                        permissionLauncher.launch(
                            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.POST_NOTIFICATIONS)
                        )
                    }
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        val hasLocation = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasNotification = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

        viewModel.updatePermissionStatus(hasLocation && hasNotification)
    }

    BackHandler {
        val currentTime = System.currentTimeMillis()
        val timeDifference = currentTime - lastBackPressTime

        if (timeDifference < 1500) {
            onNavigateBack()
        } else {
            lastBackPressTime = currentTime
            Toast.makeText(context, "Press back again to return", Toast.LENGTH_SHORT).show()
        }
    }

    DisposableEffect(Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == NotificationService.ACTION_STOPPED_FROM_NOTIFICATION) {
                    viewModel.onServiceStoppedExternal()
                }
            }
        }
        val filter = IntentFilter(NotificationService.ACTION_STOPPED_FROM_NOTIFICATION)
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)

        onDispose {
            context.unregisterReceiver(receiver)
            if (isExploringMode) {
                toggleLocalizationService(false)
            }
        }
    }

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = { viewModel.refreshData() },
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        when (uiState) {
            is MainUiState.Loading -> CircularProgressIndicator()
            is MainUiState.Success -> {
                HexagonMap(
                    isUserInCity = isUserInCity,
                    userLocationTask = locationClient.lastLocation,
                    data = uiState.cityHexagonsDataDto,
                    selectedHexagonId = selectedHexagonId,
                    onHexagonClick = { id, weight ->
                        selectedHexagonId = if (selectedHexagonId == id) null else id
                        viewModel.getPoisFromHexagon(selectedHexagonId, weight)
                    },
                    onMyLocationClick = {
                        viewModel.getPoisFromHexagon(
                            hexagonId = null,
                            hexagonWeight = null
                        )
                        selectedHexagonId = null
                    }
                )

                Box (
                    modifier = modifier
                        .fillMaxSize()
                ) {
                    if (hexagonPois.pois.isNotEmpty() || hexagonPois.weight != 0.0) {
                        Column(
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(16.dp)
                                .background(
                                    color = CustomBlack.copy(alpha = 0.6f),
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .padding(vertical = 8.dp, horizontal = 16.dp)
                        ) {
                            Text(
                                text = "%.3f%%".format(hexagonPois.weight * 100),
                                color = CustomSuccess,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 8.dp)
                            )

                            hexagonPois.pois.forEach { poi ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 2.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Place,
                                        contentDescription = null,
                                        tint = if (poi.isPromoted) CustomWarning else CustomWhite,
                                        modifier = Modifier.size(16.dp)
                                    )

                                    Spacer(modifier = Modifier.width(8.dp))

                                    Text(
                                        text = "${poi.name} - ${poi.type}",
                                        color = if (poi.isPromoted) CustomWarning else CustomWhite,
                                        textAlign = TextAlign.Start
                                    )
                                }
                            }
                        }
                    }

                    Button(
                        onClick = {
                            viewModel.onExplorerToggleClick()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isUserInCity && arePermissionsGranted) {
                                (if (isExploringMode) CustomError else CustomSuccess).copy(alpha = 0.6f)
                            } else {
                                CustomBlack.copy(alpha = 0.6f)
                            },
                            contentColor = CustomBlack
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
            is MainUiState.Error -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    item {
                        Text(
                            text = uiState.message,
                            modifier = Modifier
                                .padding(16.dp)
                                .fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun HexagonMap(
    isUserInCity: Boolean,
    userLocationTask: Task<Location?>,
    data: GetCityHexagonsDataDto,
    selectedHexagonId: String?,
    onHexagonClick: (String, Double) -> Unit,
    onMyLocationClick: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val bounds = LatLngBounds(
        LatLng(data.bbox[0], data.bbox[1]),
        LatLng(data.bbox[2], data.bbox[3])
    )

    val mapProperties = remember(isUserInCity) {
        MapProperties(
            mapStyleOptions = MapStyleOptions.loadRawResourceStyle(context, R.raw.custom_map),
            latLngBoundsForCameraTarget = bounds,
            maxZoomPreference = 16f,
            minZoomPreference = 12f,
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

    var visibleBounds by remember { mutableStateOf<LatLngBounds?>(null) }

    var currentUserLocation by remember { mutableStateOf<LatLng?>(null) }

    val cityCenter = LatLng((data.bbox[0] + data.bbox[2]) / 2, (data.bbox[1] + data.bbox[3]) / 2)

    var isAutoTracking by remember { mutableStateOf(true) }

    val cameraPositionState = rememberCameraPositionState {
        val startPos = if (isUserInCity && currentUserLocation != null) currentUserLocation!! else cityCenter
        val startZoom = if (isUserInCity) 15f else 12f
        position = CameraPosition.fromLatLngZoom(startPos, startZoom)
    }

    val visibleHexagons by remember(data.hexagons, visibleBounds) {
        derivedStateOf {
            val bounds = visibleBounds ?: return@derivedStateOf emptyList()

            data.hexagons.filter { hexagon ->
                val center = LatLng(hexagon.center[0], hexagon.center[1])
                bounds.contains(center)
            }
        }
    }

    LaunchedEffect(userLocationTask) {
        userLocationTask.addOnSuccessListener { location ->
            if (location != null) {
                currentUserLocation = LatLng(location.latitude, location.longitude)
            }
        }
    }

    LaunchedEffect(cameraPositionState.isMoving) {
        if (cameraPositionState.isMoving && cameraPositionState.cameraMoveStartedReason == CameraMoveStartedReason.GESTURE) {
            isAutoTracking = false
        }
    }

    LaunchedEffect(cameraPositionState.position, cameraPositionState.isMoving) {
        if (!cameraPositionState.isMoving) {
            visibleBounds = cameraPositionState.projection?.visibleRegion?.latLngBounds
        }
    }

    LaunchedEffect(currentUserLocation) {
        if (isUserInCity && currentUserLocation != null && isAutoTracking) {
            cameraPositionState.animate(
                CameraUpdateFactory.newLatLng(currentUserLocation!!)
            )
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        GoogleMap(
            cameraPositionState = cameraPositionState,
            properties = mapProperties,
            uiSettings = mapUiSettings
        ) {
            visibleHexagons.forEach { hexagon ->
                val fillAlpha = if (hexagon.id == selectedHexagonId) 0.15f else 0.08f

                Polygon(
                    points = hexagon.boundaries.map { point ->
                        LatLng(point[0], point[1])
                    },
                    strokeWidth = 1f,
                    strokeColor = CustomBlack,
                    fillColor = CustomError.copy(alpha = fillAlpha),
                    clickable = true,
                    onClick = {
                        onHexagonClick(hexagon.id, hexagon.weight)
                    }
                )
            }
        }

        if (isUserInCity) {
            FloatingActionButton(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .statusBarsPadding()
                    .padding(bottom = 16.dp, end = 16.dp),
                onClick = {
                    isAutoTracking = true

                    if (currentUserLocation != null) {
                        scope.launch {
                            onMyLocationClick()

                            cameraPositionState.animate(
                                CameraUpdateFactory.newCameraPosition(CameraPosition.fromLatLngZoom(currentUserLocation!!, 15f))

                            )
                        }
                    }
                },
                containerColor = CustomBlack,
                contentColor = CustomWhite
            ) {
                Icon(
                    imageVector = if (isAutoTracking) Icons.Default.MyLocation else Icons.Default.LocationSearching,
                    contentDescription = null
                )
            }
        }
    }
}