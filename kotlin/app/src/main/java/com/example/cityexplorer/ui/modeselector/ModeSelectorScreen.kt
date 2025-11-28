package com.example.cityexplorer.ui.modeselector

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun ModeSelectorScreen(
    modifier: Modifier = Modifier,
    city: String,
    onNavigateToMapScreen: (city: String, mode: String) -> Unit,
    viewModel: ModeSelectorViewModel = viewModel(factory = ModeSelectorViewModelFactory(city))
) {
    fun handleModeClick(mode: String) {
        onNavigateToMapScreen(city, mode)
    }

    Box(
        modifier = modifier
            .fillMaxWidth(),
        contentAlignment = Alignment.Center,
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