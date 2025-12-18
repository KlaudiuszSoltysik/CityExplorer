package com.example.cityexplorer

import android.content.pm.ActivityInfo
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
import com.example.cityexplorer.data.repositories.HexagonRepository
import com.example.cityexplorer.data.repositories.UserRepository
import com.example.cityexplorer.data.util.LocationTrackingService
import com.example.cityexplorer.data.util.TokenService
import com.example.cityexplorer.ui.cityselector.CitySelectorScreen
import com.example.cityexplorer.ui.login.LoginScreen
import com.example.cityexplorer.ui.map.MapScreen
import com.example.cityexplorer.ui.theme.CityExplorerTheme
import com.example.cityexplorer.ui.theme.CustomBlack
import com.example.cityexplorer.ui.useraccount.UserAccountScreen
import com.google.android.gms.location.LocationServices

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT

        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val app = applicationContext as CityExplorerApp

        val runningCity = LocationTrackingService.activeCity

        val startDestination = if (runningCity != null) {
            "map/$runningCity"
        } else {
            "city_selector"
        }

        setContent {
            CityExplorerTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    CityExplorerAppHost(
                        startDestination = startDestination,
                        tokenService = app.tokenService,
                        hexagonRepository = app.hexagonRepository,
                        userRepository = app.userRepository,
                        modifier = Modifier
                            .background(CustomBlack)
                            .fillMaxSize(),
                        contentPadding = innerPadding,
                    )
                }
            }
        }
    }
}

@Composable
fun CityExplorerAppHost(
    startDestination: String,
    tokenService: TokenService,
    hexagonRepository: HexagonRepository,
    userRepository: UserRepository,
    modifier: Modifier,
    contentPadding: PaddingValues,
) {
    val navController = rememberNavController()
    val context = LocalContext.current

    val locationClient = remember(context) {
        LocationServices.getFusedLocationProviderClient(context)
    }

    Surface(modifier = modifier) {
        NavHost(
            navController = navController,
            startDestination = startDestination
        ) {

            // Login Screen
            composable(
                route = Screen.LoginScreen.route,
                arguments = listOf(
                    navArgument(Screen.Args.RETURN_ROUTE) {
                        nullable = true
                        defaultValue = null
                        type = NavType.StringType
                    }
                )
            ) { backStackEntry ->
                val returnRoute = backStackEntry.arguments?.getString(Screen.Args.RETURN_ROUTE)

                LoginScreen(
                    tokenService = tokenService,
                    userRepository = userRepository,
                    modifier = Modifier.padding(contentPadding),
                    onNavigateToNextScreen = {
                        val targetDestination = returnRoute ?: Screen.CitySelectorScreen.route

                        navController.navigate(targetDestination) {
                            popUpTo(Screen.LoginScreen.route) { inclusive = true }
                        }
                    }
                )
            }

            // City Selector Screen
            composable(Screen.CitySelectorScreen.route) {
                CitySelectorScreen(
                    hexagonRepository = hexagonRepository,
                    modifier = Modifier.padding(contentPadding),
                    onNavigateToModeSelectorScreen = { city ->
                        navController.navigate(Screen.MapScreen.createRoute(city))
                    }
                )
            }

            // Map Screen
            composable(
                route = Screen.MapScreen.route,
                arguments = listOf(
                    navArgument(Screen.Args.CITY) { type = NavType.StringType }
                ),
                deepLinks = listOf(
                    navDeepLink {
                        uriPattern = "cityexplorer://map/{${Screen.Args.CITY}}"
                    }
                )
            ) { backStackEntry ->
                val city = backStackEntry.arguments?.getString(Screen.Args.CITY) ?: ""

                MapScreen(
                    modifier = Modifier.padding(contentPadding),
                    city = city,
                    locationClient = locationClient,
                    tokenService = tokenService,
                    hexagonRepository = hexagonRepository,
                    userRepository = userRepository,
                    onNavigateToLogin = {
                        val currentRoute = Screen.MapScreen.createRoute(city)
                        val loginRoute = Screen.LoginScreen.createRoute(returnRoute = currentRoute)

                        navController.navigate(loginRoute) {
                            popUpTo(Screen.MapScreen.route) { inclusive = true }
                        }
                    },
                    onNavigateBack = {
                        navController.popBackStack()
                    },
                    onNavigateToUserAccount = {
                        navController.navigate("user_account")
                    }
                )
            }

            // User account screen
            composable(Screen.UserAccountScreen.route) {
                UserAccountScreen(
                    userRepository = userRepository,
                    tokenService = tokenService,
                    onNavigateBack = {
                        navController.popBackStack()
                    },
                    modifier = Modifier.padding(contentPadding),
                )
            }
        }
    }
}
