package com.example.cityexplorer.ui.map

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun MapScreen(
    city: String,
    mode: String,
    viewModel: MapViewModel = viewModel(factory = MapViewModelFactory(city, mode))
) {
    val uiState = viewModel.uiState

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        when (uiState) {
            is MainUiState.Loading -> CircularProgressIndicator()
            is MainUiState.Success -> {
                Column {
                    Text(
                        text = "Map Screen: $city - $mode",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = "${uiState.hexagons[0].boundaries} - ${uiState.hexagons[0].boundaries} - ${uiState.hexagons[0].weight}",
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
            is MainUiState.Error -> {
                Text(text = "Error: ${uiState.message}")
            }
        }
    }
}