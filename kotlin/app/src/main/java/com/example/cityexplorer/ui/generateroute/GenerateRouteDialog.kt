package com.example.cityexplorer.ui.generateroute

import android.location.Location
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.example.cityexplorer.data.dtos.WorkerResult
import com.example.cityexplorer.ui.theme.CustomBlack
import com.example.cityexplorer.ui.theme.CustomWarning
import com.example.cityexplorer.ui.theme.CustomWhite

@Composable
fun GenerateRouteDialog(
    userLocation: Location,
    onNavigateToLogin: () -> Unit,
    onDismiss: () -> Unit,
    onRouteGenerated: (WorkerResult) -> Unit,
    onRouteCleared: () -> Unit,
    viewModel: GenerateRouteViewModel = hiltViewModel()
) {
    val selectedTime by viewModel.selectedTime.collectAsState()
    val uiState by viewModel.uiState.collectAsState()

    val lifecycleOwner = LocalLifecycleOwner.current
    val configuration = LocalConfiguration.current

    val screenWidth = configuration.screenWidthDp.dp
    val dialogWidth = screenWidth * 0.8f

    val closeDialog = {
        viewModel.resetState()
        onDismiss()
    }

    // One-off UI events
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.uiEvent.collect { event ->
                when (event) {
                    is GenerateRouteUiEvent.NavigateToLogin -> {
                        onNavigateToLogin()
                    }
                }
            }
        }
    }

    LaunchedEffect(uiState) {
        if (uiState is GenerateRouteUiState.Success) {
            val data = (uiState as GenerateRouteUiState.Success).data
            onRouteGenerated(data)
            closeDialog()
        }
    }

    Dialog(onDismissRequest = closeDialog) {
        Card(
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .width(dialogWidth)
                .height(400.dp),
            elevation = CardDefaults.cardElevation(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                when (val state = uiState) {

                    is GenerateRouteUiState.Choose -> {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text("Walking time", style = MaterialTheme.typography.titleLarge)

                            Spacer(modifier = Modifier.height(24.dp))

                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                items(viewModel.availableTimes) { time ->
                                    NumberItem(
                                        number = time,
                                        isSelected = time == selectedTime,
                                        onClick = { viewModel.onTimeSelected(time) }
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(24.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                TextButton(onClick = closeDialog) {
                                    Text("Close")
                                }
                                Button(
                                    onClick = { viewModel.onConfirmClicked(userLocation) },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = CustomWhite,
                                        contentColor = CustomBlack
                                    )
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.AutoAwesome, null)
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Generate route")
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(24.dp))

                            TextButton(
                                modifier = Modifier.fillMaxWidth(),
                                onClick = {
                                    onRouteCleared()
                                    closeDialog()
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = CustomWarning,
                                    contentColor = CustomWhite
                                )
                            ) {
                                Text("Clear route")
                            }
                        }
                    }

                    is GenerateRouteUiState.Loading -> {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("Generating route...")
                        }
                    }

                    is GenerateRouteUiState.Error -> {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = state.message,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 32.dp)
                            )

                            Button(onClick = { viewModel.resetState() }) {
                                Text("Try again")
                            }
                            TextButton(onClick = closeDialog) {
                                Text("Close")
                            }
                        }
                    }

                    is GenerateRouteUiState.Success -> {
                    }
                }
            }
        }
    }
}

@Composable
fun NumberItem(number: Int, isSelected: Boolean, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(60.dp)
            .background(
                if (isSelected) CustomWhite else CustomBlack,
                shape = RoundedCornerShape(50)
            )
            .clickable { onClick() }
    ) {
        Text(
            text = number.toString(),
            color = if (isSelected) CustomBlack else CustomWhite
        )
    }
}