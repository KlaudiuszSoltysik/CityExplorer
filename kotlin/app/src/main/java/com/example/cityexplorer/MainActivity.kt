package com.example.cityexplorer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.cityexplorer.data.util.TokenManager
import com.example.cityexplorer.ui.cityselector.CitySelectorScreen
import com.example.cityexplorer.ui.map.MapScreen
import com.example.cityexplorer.ui.login.LoginScreen
import com.example.cityexplorer.ui.login.LoginViewModelFactory
import com.example.cityexplorer.ui.modeselector.ModeSelectorScreen
import com.example.cityexplorer.ui.theme.CityExplorerTheme
import com.example.cityexplorer.ui.theme.CustomBlack
import com.google.android.gms.location.LocationServices

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val tokenManager = TokenManager(applicationContext)

        val startDestination = if (tokenManager.getToken() != null) {
            Screen.CitySelectorScreen.route
        } else {
            Screen.LoginScreen.route
        }

        setContent {
            CityExplorerTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    CityExplorerAppHost(
                        modifier = Modifier
                            .background(CustomBlack)
                            .fillMaxSize(),
                        contentPadding = innerPadding,
                        startDestination = startDestination,
                        tokenManager = tokenManager
                    )
                }
            }
        }
    }
}

@Composable
fun CityExplorerAppHost(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues,
    startDestination: String,
    tokenManager: TokenManager
) {
    val navController = rememberNavController()

    Surface(modifier = modifier.fillMaxSize()) {
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = modifier
        ) {
            composable(Screen.LoginScreen.route) {
                LoginScreen(
                    modifier = Modifier.padding(contentPadding),
                    onNavigateToCitySelectorScreen = {
                        navController.navigate(Screen.CitySelectorScreen.route) {
                            popUpTo(Screen.LoginScreen.route) { inclusive = true }
                        }
                    },
                    viewModel = viewModel(
                        factory = LoginViewModelFactory(tokenManager)
                    )
                )
            }

            composable(Screen.CitySelectorScreen.route) {
                CitySelectorScreen(
                    modifier = Modifier.padding(contentPadding),
                    onNavigateToModeSelectorScreen = { city ->
                        navController.navigate(Screen.ModeSelectorScreen("").createRoute(city))
                    }
                )
            }

            composable(
                route = Screen.ModeSelectorScreen("").route,
                arguments = listOf(navArgument("city") { type = NavType.StringType })
            ) { backStackEntry ->
                val city = backStackEntry.arguments?.getString("city")!!
                ModeSelectorScreen(
                    modifier = Modifier.padding(contentPadding),
                    city = city,
                    onNavigateToMapScreen = { navigatedCity, navigatedMode ->
                        navController.navigate(Screen.MapScreen(navigatedCity, navigatedMode).createRoute(navigatedCity, navigatedMode))
                    }
                )
            }

            composable(
                route = Screen.MapScreen("", "").route,
                arguments = listOf(
                    navArgument("city") { type = NavType.StringType },
                    navArgument("mode") { type = NavType.StringType }
                )
            ) { backStackEntry ->
                val city = backStackEntry.arguments?.getString("city")!!
                val mode = backStackEntry.arguments?.getString("mode")!!
                val context = LocalContext.current

                val locationClient = remember {
                    LocationServices.getFusedLocationProviderClient(context)
                }

                MapScreen(
                    modifier = Modifier.padding(contentPadding),
                    city = city,
                    mode = mode,
                    locationClient = locationClient
                )
            }
        }
    }
}
