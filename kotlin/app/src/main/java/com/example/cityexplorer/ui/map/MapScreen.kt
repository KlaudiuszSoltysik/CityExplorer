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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.example.cityexplorer.data.dtos.SelectedHexagonDto
import com.example.cityexplorer.data.repositories.HexagonRepository
import com.example.cityexplorer.data.repositories.UserRepository
import com.example.cityexplorer.ui.theme.CustomError
import com.example.cityexplorer.ui.theme.CustomSuccess
import com.example.cityexplorer.ui.theme.CustomWarning
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.maps.android.compose.CameraMoveStartedReason

@Composable
fun MapScreen(
    city: String,
    locationClient: FusedLocationProviderClient,
    tokenService: TokenService,
    hexagonRepository: HexagonRepository,
    userRepository: UserRepository,
    modifier: Modifier,
    onNavigateToLogin: () -> Unit,
    onNavigateBack: () -> Unit,
    viewModel: MapViewModel = viewModel(
        factory = MapViewModelFactory(city, locationClient, tokenService, hexagonRepository, userRepository)
    )
) {
    val state = viewModel.state

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Local state for back press handling
    var lastBackPressTime by remember { mutableLongStateOf(0L) }

    // Local state for map interaction
    var selectedHexagonId by remember { mutableStateOf<String?>(null) }

    // Permission handling
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val isFineLocationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
        val isNotificationGranted =
            permissions[Manifest.permission.POST_NOTIFICATIONS] == true

        val isGranted = isFineLocationGranted && isNotificationGranted
        viewModel.updatePermissionStatus(isGranted)
    }

    // Localization service control
    fun toggleLocalizationService(enable: Boolean) {
        Intent(context, NotificationService::class.java).also { intent ->
            if (enable) {
                intent.action = NotificationService.ACTION_START
                intent.putExtra("city", city)
                ContextCompat.startForegroundService(context, intent)
            } else {
                intent.action = NotificationService.ACTION_STOP
                context.startService(intent)
            }
        }
    }

    // One-off UI events
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.uiEvent.collect { event ->
                when (event) {
                    is MapUiEvent.ToggleService -> {
                        toggleLocalizationService(event.shouldStart)
                    }
                    is MapUiEvent.NavigateToLogin -> {
                        toggleLocalizationService(false)
                        onNavigateToLogin()
                    }
                    is MapUiEvent.ShowError -> {
                        Toast.makeText(context, event.message, Toast.LENGTH_LONG).show()
                    }
                    is MapUiEvent.RequestPermissions -> {
                        val permissionsToRequest = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION)
                        permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
                        permissionLauncher.launch(permissionsToRequest.toTypedArray())
                    }
                }
            }
        }
    }

    // Initial checks
    LaunchedEffect(Unit) {
        val hasLocation = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasNotification =
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

        viewModel.updatePermissionStatus(hasLocation && hasNotification)
    }

    // External service events
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
        }
    }

    // Navigation control (back press)
    BackHandler {
        val currentTime = System.currentTimeMillis()
        val timeDifference = currentTime - lastBackPressTime

        if (timeDifference < 1500) {
            toggleLocalizationService(false)
            onNavigateBack()
        } else {
            lastBackPressTime = currentTime
            Toast.makeText(context, "Press back again to return", Toast.LENGTH_SHORT).show()
        }
    }

    // UI content
    MapScreenContent(
        state = state,
        selectedHexagonId = selectedHexagonId,
        modifier = modifier,
        onRefresh = { viewModel.refreshData() },
        onExplorerToggle = { viewModel.onExplorerToggleClick() },
        onHexagonClick = { id, weight ->
            selectedHexagonId = if (selectedHexagonId == id) { null } else { id }

            viewModel.getPoisFromHexagon(selectedHexagonId, weight)
        },
        onMyLocationClick = {
            viewModel.getPoisFromHexagon(null, null)
            selectedHexagonId = null
        }
    )
}

@Composable
fun MapScreenContent(
    state: MapScreenState,
    selectedHexagonId: String?,
    modifier: Modifier = Modifier,
    onRefresh: () -> Unit,
    onExplorerToggle: () -> Unit,
    onHexagonClick: (String, Double) -> Unit,
    onMyLocationClick: () -> Unit
) {
    PullToRefreshBox(
        isRefreshing = state.isRefreshing,
        onRefresh = onRefresh,
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        when (val dataState = state.dataState) {
            is MapUiState.Loading -> {
                MapLoadingContent()
            }
            is MapUiState.Success -> {
                MapSuccessContent(
                    data = dataState.cityHexagonsDataDto,
                    state = state,
                    selectedHexagonId = selectedHexagonId,
                    modifier = Modifier.fillMaxSize(),
                    onHexagonClick = onHexagonClick,
                    onMyLocationClick = onMyLocationClick,
                    onExplorerToggle = onExplorerToggle
                )
            }
            is MapUiState.Error -> {
                MapErrorContent(
                    message = dataState.message
                )
            }
        }
    }
}

@Composable
private fun MapSuccessContent(
    data: GetCityHexagonsDataDto,
    state: MapScreenState,
    selectedHexagonId: String?,
    modifier: Modifier = Modifier,
    onHexagonClick: (String, Double) -> Unit,
    onMyLocationClick: () -> Unit,
    onExplorerToggle: () -> Unit
) {
    HexagonMap(
        state = state,
        data = data,
        selectedHexagonId = selectedHexagonId,
        onHexagonClick = onHexagonClick,
        onMyLocationClick = onMyLocationClick
    )

    MapUiOverlays(
        hexagonPois = state.selectedHexagonPois,
        isExploringMode = state.isExploringMode,
        isUserInCity = state.isUserInCity,
        arePermissionsGranted = state.arePermissionsGranted,
        modifier = modifier,
        onExplorerToggle = onExplorerToggle
    )
}

@Composable
fun HexagonMap(
    state: MapScreenState,
    data: GetCityHexagonsDataDto,
    selectedHexagonId: String?,
    onHexagonClick: (String, Double) -> Unit,
    onMyLocationClick: () -> Unit
) {
    val context = LocalContext.current

    val userLatLng = remember(state.userLocation) {
        state.userLocation?.let { LatLng(it.latitude, it.longitude) }
    }

    val cityBounds = remember(data.bbox) {
        LatLngBounds(
            LatLng(data.bbox[0], data.bbox[1]),
            LatLng(data.bbox[2], data.bbox[3])
        )
    }

    val cityCenter = remember(data.bbox) {
        LatLng(
            (data.bbox[0] + data.bbox[2]) / 2,
            (data.bbox[1] + data.bbox[3]) / 2
        )
    }

    val mapProperties = remember(state.isUserInCity) {
        MapProperties(
            mapStyleOptions = MapStyleOptions.loadRawResourceStyle(context, R.raw.custom_map),
            latLngBoundsForCameraTarget = cityBounds,
            maxZoomPreference = 16f,
            minZoomPreference = 12f,
            isMyLocationEnabled = state.isUserInCity
        )
    }

    val mapUiSettings = remember {
        MapUiSettings(
            zoomControlsEnabled = false,
            compassEnabled = false,
            rotationGesturesEnabled = false,
            tiltGesturesEnabled = false,
            myLocationButtonEnabled = false
        )
    }

    var isAutoTracking by remember { mutableStateOf(true) }
    var shouldAnimateZoom by remember { mutableStateOf(false) }
    var visibleBounds by remember { mutableStateOf<LatLngBounds?>(null) }

    val cameraPositionState = rememberCameraPositionState {
        val startPos = if (state.isUserInCity && userLatLng != null) userLatLng else cityCenter
        val startZoom = if (state.isUserInCity) 15f else 12f
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


    // Turn off auto-tracking when camera moves
    LaunchedEffect(cameraPositionState.isMoving) {
        if (cameraPositionState.isMoving && cameraPositionState.cameraMoveStartedReason == CameraMoveStartedReason.GESTURE) {
            isAutoTracking = false
        }
    }

    // Update visible bounds when camera changes
    LaunchedEffect(cameraPositionState.position, cameraPositionState.isMoving) {
        if (!cameraPositionState.isMoving) {
            visibleBounds = cameraPositionState.projection?.visibleRegion?.latLngBounds
        }
    }

    // Aim at user on map initial load
    LaunchedEffect(state.isUserInCity) {
        if (state.isUserInCity) {
            isAutoTracking = true
            if (userLatLng != null) {
                cameraPositionState.animate(
                    CameraUpdateFactory.newCameraPosition(
                        CameraPosition.fromLatLngZoom(userLatLng, 15f)
                    )
                )
            }
        }
    }

    // Camera controller
    LaunchedEffect(userLatLng, isAutoTracking, shouldAnimateZoom) {
        if (state.isUserInCity && userLatLng != null && isAutoTracking) {
            if (shouldAnimateZoom) {
                cameraPositionState.animate(
                    CameraUpdateFactory.newCameraPosition(
                        CameraPosition.fromLatLngZoom(userLatLng, 15f)
                    )
                )
                shouldAnimateZoom = false
            } else {
                cameraPositionState.animate(
                    CameraUpdateFactory.newLatLng(userLatLng)
                )
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        GoogleMap(
            cameraPositionState = cameraPositionState,
            properties = mapProperties,
            uiSettings = mapUiSettings
        ) {
            visibleHexagons.forEach { hexagon ->
                val isSelected = hexagon.id == selectedHexagonId
                val fillAlpha = if (isSelected) 0.15f else 0.08f

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

        if (state.isUserInCity) {
            FloatingActionButton(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .statusBarsPadding()
                    .padding(bottom = 16.dp, end = 16.dp),
                onClick = {
                    isAutoTracking = true
                    shouldAnimateZoom = true
                    onMyLocationClick()
                },
                containerColor = CustomBlack,
                contentColor = CustomWhite
            ) {
                Icon(
                    imageVector = if (isAutoTracking) Icons.Default.MyLocation else Icons.Default.LocationSearching,
                    contentDescription = "Recenter Map"
                )
            }
        }
    }
}

@Composable
private fun MapUiOverlays(
    hexagonPois: SelectedHexagonDto,
    isExploringMode: Boolean,
    isUserInCity: Boolean,
    arePermissionsGranted: Boolean,
    modifier: Modifier = Modifier,
    onExplorerToggle: () -> Unit
) {
    Box(
        modifier = modifier.fillMaxSize()
    ) {
        if (hexagonPois.pois.isNotEmpty() || hexagonPois.weight != 0.0) {
            HexagonInfoPanel(
                hexagonPois = hexagonPois,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(16.dp)
            )
        }

        ExplorerControlButton(
            isExploringMode = isExploringMode,
            isUserInCity = isUserInCity,
            arePermissionsGranted = arePermissionsGranted,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp),
            onClick = onExplorerToggle
        )
    }
}

@Composable
private fun HexagonInfoPanel(
    hexagonPois: SelectedHexagonDto,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
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
            PoiInfoItem(
                name = poi.name,
                type = poi.type,
                isPromoted = poi.isPromoted,
                modifier = Modifier.padding(vertical = 2.dp)
            )
        }
    }
}

@Composable
private fun PoiInfoItem(
    name: String,
    type: String,
    isPromoted: Boolean,
    modifier: Modifier = Modifier
) {
    val contentColor = if (isPromoted) CustomWarning else CustomWhite

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth()
    ) {
        Icon(
            imageVector = Icons.Default.Place,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(16.dp)
        )

        Spacer(modifier = Modifier.width(8.dp))

        Text(
            text = "$name - $type",
            color = contentColor,
            textAlign = TextAlign.Start
        )
    }
}

@Composable
private fun ExplorerControlButton(
    isExploringMode: Boolean,
    isUserInCity: Boolean,
    arePermissionsGranted: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val containerColor = if (isUserInCity && arePermissionsGranted) {
        if (isExploringMode) CustomError else CustomSuccess
    } else {
        CustomBlack
    }

    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor.copy(alpha = 0.6f),
            contentColor = CustomBlack
        ),
        shape = RoundedCornerShape(8.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        modifier = modifier
    ) {
        Text(text = if (isExploringMode) "Stop exploring!" else "Start exploring!")
    }
}

@Composable
private fun MapLoadingContent(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun MapErrorContent(
    message: String,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            Text(
                text = message,
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth()
            )
        }
    }
}
