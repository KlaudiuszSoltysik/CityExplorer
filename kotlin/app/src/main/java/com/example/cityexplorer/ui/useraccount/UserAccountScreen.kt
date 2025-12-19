package com.example.cityexplorer.ui.useraccount

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.cityexplorer.data.dtos.GetUserStatisticsDto
import com.example.cityexplorer.data.repositories.UserRepository
import com.example.cityexplorer.data.util.TokenService
import com.example.cityexplorer.ui.theme.CustomError
import com.example.cityexplorer.ui.theme.CustomWarning
import com.example.cityexplorer.ui.theme.CustomWhite

@Composable
fun UserAccountScreen(
    city: String,
    userRepository: UserRepository,
    tokenService: TokenService,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: UserAccountViewModel = viewModel(
        factory = UserAccountViewModelFactory(city, userRepository, tokenService)
    )
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var lastBackPressTime by remember { mutableLongStateOf(0L) }

    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.uiEvent.collect { event ->
                when (event) {
                    is UserAccountUiEvent.NavigateBack -> {
                        onNavigateBack()
                    }
                }
            }
        }
    }

    UserAccountContent(
        uiState = viewModel.uiState,
        isRefreshing = viewModel.isRefreshing,
        onRefresh = { viewModel.refreshData() },
        onLogoutButton = { viewModel.handleLogout() },
        onDeleteButton = {
            val currentTime = System.currentTimeMillis()
            val timeDifference = currentTime - lastBackPressTime

            if (timeDifference < 1500) {
                viewModel.handleDeleteAccount()
            } else {
                lastBackPressTime = currentTime
                Toast.makeText(context, "Press again to delete account.", Toast.LENGTH_SHORT).show()
            }},
        modifier = modifier,
    )
}

@Composable
fun UserAccountContent(
    uiState: UserAccountUiState,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onLogoutButton: () -> Unit,
    onDeleteButton: () -> Unit,
    modifier: Modifier = Modifier
) {
    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        when (uiState) {
            is UserAccountUiState.Loading -> {
                CircularProgressIndicator()
            }
            is UserAccountUiState.Success -> {
                UserAccount(
                    data = uiState.data,
                    onLogoutButton = onLogoutButton,
                    onDeleteButton = onDeleteButton,
                    modifier = Modifier.fillMaxSize()
                )
            }
            is UserAccountUiState.Error -> {
                ErrorMessage(
                    message = uiState.message,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

@Composable
fun UserAccount(
    data: GetUserStatisticsDto,
    onLogoutButton: () -> Unit,
    onDeleteButton: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .padding(16.dp)
            .fillMaxSize()
    ) {
        Text(
            text = "Statistics",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        StatisticRow(label = "Ranking", value = "# ${data.ranking} / ${data.userCount}")
        HorizontalDivider()

        StatisticRow(label = "Exploring time", value = formatPlayTime(data.playTime))
        HorizontalDivider()

        StatisticRow(label = "Hexagons discovered", value = data.progress.toString())
        HorizontalDivider()

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = onLogoutButton,
            colors = ButtonDefaults.buttonColors(
                containerColor = CustomWarning,
                contentColor = CustomWhite
            ),
            shape = RoundedCornerShape(8.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Logout")
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = onDeleteButton,
            colors = ButtonDefaults.buttonColors(
                containerColor = CustomError,
                contentColor = CustomWhite
            ),
            shape = RoundedCornerShape(8.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Delete account")
        }
    }
}

@Composable
fun StatisticRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = Color.Gray
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground
        )
    }
}


fun formatPlayTime(seconds: Int): String {
    val minutes = seconds / 60

    val h = minutes / 60
    val m = minutes % 60

    return if (h > 0) "$h h $m min" else "$m min"
}

@Composable
fun ErrorMessage(
    message: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.verticalScroll(rememberScrollState()),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(
            text = message,
            modifier = Modifier.padding(16.dp)
        )
    }
}
