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
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink
import com.example.cityexplorer.data.util.TokenManager
import com.example.cityexplorer.ui.cityselector.CitySelectorScreen
import com.example.cityexplorer.ui.map.MapScreen
import com.example.cityexplorer.ui.login.LoginScreen
import com.example.cityexplorer.ui.theme.CityExplorerTheme
import com.example.cityexplorer.ui.theme.CustomBlack
import com.google.android.gms.location.LocationServices

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val tokenManager = TokenManager(applicationContext)

        setContent {
            CityExplorerTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    CityExplorerAppHost(
                        modifier = Modifier
                            .background(CustomBlack)
                            .fillMaxSize(),
                        contentPadding = innerPadding,
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
    tokenManager: TokenManager
) {
    val navController = rememberNavController()

    Surface(modifier = modifier.fillMaxSize()) {
        NavHost(
            navController = navController,
            startDestination = Screen.CitySelectorScreen.route,
            modifier = modifier
        ) {
            composable(Screen.LoginScreen.route) {
                LoginScreen(
                    modifier = Modifier.padding(contentPadding),
                    tokenManager = tokenManager,
                    onNavigateToNextScreen = {
                        navController.navigate(Screen.CitySelectorScreen.route) {
                            popUpTo(Screen.LoginScreen.route) { inclusive = true }
                        }
                    },
                )
            }

            composable(
                route = Screen.LoginScreen.route,
                arguments = listOf(
                    navArgument("returnRoute") {
                        nullable = true
                        defaultValue = null
                        type = NavType.StringType
                    }
                )
            ) { backStackEntry ->
                val returnRoute = backStackEntry.arguments?.getString("returnRoute")

                LoginScreen(
                    modifier = Modifier.padding(contentPadding),
                    tokenManager = tokenManager,
                    onNavigateToNextScreen = {
                        val targetDestination = returnRoute ?: Screen.CitySelectorScreen.route

                        navController.navigate(targetDestination) {
                            popUpTo(Screen.LoginScreen.route) { inclusive = true }
                        }
                    }
                )
            }

            composable(Screen.CitySelectorScreen.route) {
                CitySelectorScreen(
                    modifier = Modifier.padding(contentPadding),
                    onNavigateToModeSelectorScreen = { city ->
                        navController.navigate(Screen.MapScreen("").createRoute(city))
                    }
                )
            }

            composable(
                route = Screen.MapScreen("").route,
                arguments = listOf(
                    navArgument("city") { type = NavType.StringType }
                ),
                deepLinks = listOf(
                    navDeepLink {
                        uriPattern = "cityexplorer://map/{city}"
                    }
                )
            ) { backStackEntry ->
                val city = backStackEntry.arguments?.getString("city")!!
                val context = LocalContext.current

                val locationClient = remember {
                    LocationServices.getFusedLocationProviderClient(context)
                }

                MapScreen(
                    modifier = Modifier.padding(contentPadding),
                    city = city,
                    locationClient = locationClient,
                    tokenManager = tokenManager,
                    onNavigateToLogin = {
                        val currentRoute = Screen.MapScreen(city).createRoute(city)

                        navController.navigate(Screen.LoginScreen.createRoute(returnRoute = currentRoute)) {
                            popUpTo(Screen.MapScreen(city).route) { inclusive = true }
                        }
                    },
                    onNavigateBack = {
                        navController.popBackStack()
                    }
                )
            }
        }
    }
}
