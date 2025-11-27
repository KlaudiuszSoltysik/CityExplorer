package com.example.cityexplorer.ui.map

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.cityexplorer.data.dtos.GetCityHexagonsDataDto
import com.example.cityexplorer.ui.theme.CustomBlack
import com.example.cityexplorer.ui.theme.CustomYellow
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
    viewModel: MapViewModel = viewModel(factory = MapViewModelFactory(city, mode))
) {
    val uiState = viewModel.uiState
    val isRefreshing = viewModel.isRefreshing

    var text by remember { mutableStateOf("") }

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = { viewModel.refreshData() },
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        when (uiState) {
            is MainUiState.Loading -> CircularProgressIndicator()
            is MainUiState.Success -> {
                Box(modifier = Modifier.fillMaxSize()) {
                    HexMap(
                        data = uiState.data,
                        onHexClick = { newText -> text = newText },
                        modifier = Modifier.fillMaxSize()
                    )

                    Text(
                        text = text,
                        color = CustomBlack,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 32.dp, start = 16.dp, end = 16.dp)
                            .background(Color.White.copy(alpha = 0.8f), shape = RoundedCornerShape(8.dp))
                            .padding(16.dp)
                    )
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
    onHexClick: (String) -> Unit,
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

    val mapProperties = MapProperties(
        mapStyleOptions = MapStyleOptions(rawJsonStyle),
        latLngBoundsForCameraTarget = bounds,
        maxZoomPreference = 16f,
        minZoomPreference = 11f
    )

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
            Polygon(
                points = hexagon.boundaries.map { point ->
                    LatLng(point[0], point[1])
                },
                strokeWidth = 1f,
                strokeColor = CustomBlack,
                fillColor = CustomYellow.copy(alpha = 0.05f),
                clickable = true,
                onClick = {
                    onHexClick("ID: ${hexagon.id}   weight: ${"%.3f".format(hexagon.weight * 100)}%")
                }
            )
        }
    }
}