package com.example.cityexplorer.ui.map

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.cityexplorer.data.dtos.GetCityHexagonsDataDto
import com.example.cityexplorer.ui.theme.CustomBlack
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

@Composable
fun MapScreen(
    city: String,
    mode: String,
    locationClient: FusedLocationProviderClient,
    viewModel: MapViewModel = viewModel(factory = MapViewModelFactory(city, mode, locationClient))
) {
    val uiState = viewModel.uiState
    val isRefreshing = viewModel.isRefreshing

    var selectedHexId by remember { mutableStateOf<String?>(null) }

    val context = LocalContext.current

    var hasLocationPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val isGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true

        hasLocationPermission = isGranted
    }

    LaunchedEffect(Unit) {
        if (!hasLocationPermission) {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    LaunchedEffect(hasLocationPermission) {
        if (hasLocationPermission) {
            viewModel.startLocationTracking()
        }
    }

    fun isLocationInBbox(location: android.location.Location?, bbox: List<Double>): Boolean {
        if (location == null) return false

        return location.latitude in bbox[0]..bbox[2] &&
                location.longitude in bbox[1]..bbox[3]
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
                val showActionBtn by remember(viewModel.userLocation, uiState.data.bbox) {
                    derivedStateOf { isLocationInBbox(viewModel.userLocation, uiState.data.bbox) }
                }

                Box(modifier = Modifier.fillMaxSize()) {
                    HexMap(
                        data = uiState.data,
                        selectedHexId = selectedHexId,
                        onHexClick = { id ->
                            selectedHexId = if (selectedHexId == id) null else id
                        },
                        hasLocationPermission,
                        modifier = Modifier.fillMaxSize()
                    )

                    val selectedHexagon = uiState.data.hexagons.find { it.id == selectedHexId }

                    if (selectedHexagon != null) {
                        val displayText = "ID: ${selectedHexagon.id}   weight: ${"%.3f".format(selectedHexagon.weight * 100)}%"

                        Text(
                            text = displayText,
                            color = CustomBlack,
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = 16.dp, start = 16.dp, end = 16.dp)
                                .background(
                                    Color.White.copy(alpha = 0.8f),
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .padding(vertical = 8.dp, horizontal = 16.dp)
                        )
                    }

                    if (showActionBtn) {
                        Button(
                            onClick = { /* TODO: Akcja */ },
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 32.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = CustomBlack)
                        ) {
                            Text("Jesteś na obszarze!")
                        }
                    }
                }
            }
            is MainUiState.Error -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
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
    data: GetCityHexagonsDataDto,
    selectedHexId: String?,
    onHexClick: (String) -> Unit,
    hasLocationPermission: Boolean,
    modifier: Modifier
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

    val mapProperties = remember(hasLocationPermission) {
        MapProperties(
            mapStyleOptions = MapStyleOptions(rawJsonStyle),
            latLngBoundsForCameraTarget = bounds,
            maxZoomPreference = 16f,
            minZoomPreference = 9f,
            isMyLocationEnabled = hasLocationPermission
        )
    }

    val mapUiSettings = MapUiSettings(
        zoomControlsEnabled = false,
        compassEnabled = false,
        rotationGesturesEnabled = false,
        tiltGesturesEnabled = false
    )

    GoogleMap(
        modifier = modifier,
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