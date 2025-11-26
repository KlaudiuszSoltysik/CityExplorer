package com.example.cityexplorer.ui.map

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.cityexplorer.data.dtos.GetHexagonsFromCityDto

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

            }
            is MainUiState.Error -> {
                Text(text = "Error: ${uiState.message}")
            }
        }
    }
}
