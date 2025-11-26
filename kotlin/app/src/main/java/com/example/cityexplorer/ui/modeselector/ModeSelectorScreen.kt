package com.example.cityexplorer.ui.modeselector

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.cityexplorer.ui.cityselector.CitySelectorViewModel

@Composable
fun ModeSelectorScreen(
    city: String,
    viewModel: ModeSelectorViewModel = viewModel(),
    onNavigateToMapScreen: (city: String, mode: String) -> Unit
) {
    fun handleModeClick(mode: String) {
        onNavigateToMapScreen(city, mode)
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column {
            Text(
                text = "Tourist",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        handleModeClick("tourist")
                    }
                    .padding(start = 24.dp, top = 8.dp, bottom = 8.dp)
            )
            Text(
                text = "Local",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        handleModeClick("local")
                    }
                    .padding(start = 24.dp, top = 8.dp, bottom = 8.dp)
            )
        }
    }
}