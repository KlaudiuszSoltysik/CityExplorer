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
import androidx.compose.ui.unit.sp
import com.example.cityexplorer.R
import com.example.cityexplorer.ui.theme.CustomWhite
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(
    modifier: Modifier = Modifier,
    onNavigateToCitySelectorScreen: () -> Unit,
    viewModel: LoginViewModel = viewModel(),
) {
    val uiState = viewModel.uiState
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val credentialManager = androidx.compose.runtime.remember {
        CredentialManager.create(context)
    }

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
                        coroutineScope.launch {
                            try {
                                val googleIdOption = GetGoogleIdOption.Builder()
                                    .setFilterByAuthorizedAccounts(false)
                                    .setServerClientId("357422343630-2v64co21drksl119p77bjhs642qk3cmd.apps.googleusercontent.com")
                                    .setAutoSelectEnabled(true)
                                    .build()

                                val request = GetCredentialRequest.Builder()
                                    .addCredentialOption(googleIdOption)
                                    .build()

                                val result = credentialManager.getCredential(
                                    request = request,
                                    context = context,
                                )

                                when (val credential = result.credential) {
                                    is GoogleIdTokenCredential -> {
                                        val googleIdToken = credential.idToken
                                        viewModel.onGoogleLoginSuccess(googleIdToken, onNavigateToCitySelectorScreen)
                                    }

                                    is CustomCredential -> {
                                        if (credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                                            try {
                                                val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                                                val googleIdToken = googleIdTokenCredential.idToken

                                                viewModel.onGoogleLoginSuccess(googleIdToken, onNavigateToCitySelectorScreen)
                                            } catch (e: Exception) {
                                                viewModel.uiState = MainUiState.Error(e.message ?: "Unknown error")
                                            }
                                        } else {
                                            viewModel.uiState = MainUiState.Error("Invalid Google credential type")
                                        }
                                    }

                                    else -> {
                                        viewModel.uiState = MainUiState.Error("Google credential missing")
                                    }
                                }
                            } catch (e: GetCredentialException) {
                                viewModel.uiState = MainUiState.Error(e.message ?: "Unknown error")
                            }
                        }
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
                        text = "Error: ${uiState.message}.",
                        color = CustomError
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button (
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Primary,
                            contentColor = CustomWhite
                        ),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(
                            horizontal = 16.dp,
                            vertical = 8.dp),
                        onClick = { viewModel.resetState() }
                    ) {
                        Text("Try again")
                    }
                }
            }
        }
    }
}
