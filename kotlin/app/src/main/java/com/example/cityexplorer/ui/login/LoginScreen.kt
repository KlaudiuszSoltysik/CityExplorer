package com.example.cityexplorer.ui.login

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.cityexplorer.ui.theme.CustomBlack
import com.example.cityexplorer.ui.theme.CustomError
import com.example.cityexplorer.ui.theme.Primary
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.cityexplorer.R
import com.example.cityexplorer.ui.theme.CustomWhite

@Composable
fun LoginScreen(
    modifier: Modifier = Modifier,
    onNavigateToCitySelectorScreen: () -> Unit,
    viewModel: LoginViewModel = viewModel(),
) {
    val uiState = viewModel.uiState
    val isRefreshing = viewModel.isRefreshing

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        when (uiState) {
            is MainUiState.Loading -> CircularProgressIndicator()

            is MainUiState.Waiting -> {
                Button(
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CustomWhite,
                    ),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    onClick = {
                        // Tutaj wkrótce wstawimy prawdziwy kod Google!
                        val fakeToken = "ey...twoj_token_jwt..."
                        viewModel.onGoogleLoginSuccess(fakeToken, onNavigateToCitySelectorScreen)
                    }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_google_logo),
                            contentDescription = "Google Logo",
                            tint = Color.Unspecified,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Login with Google",
                            color = CustomBlack,
                            fontSize = 20.sp,
                        )
                    }
                }
            }

            is MainUiState.Error -> {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = uiState.message,
                        color = CustomError
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button (
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Primary,
                        ),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        onClick = { viewModel.resetState() }
                    ) {
                        Text("Try again")
                    }
                }
            }
        }
    }
}
