package com.example.cityexplorer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.cityexplorer.ui.cityselector.CitySelectorScreen
import com.example.cityexplorer.ui.map.MapScreen
import com.example.cityexplorer.ui.navigation.Screen
import com.example.cityexplorer.ui.theme.CityExplorerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CityExplorerTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    CityExplorerAppHost(
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

@Composable
fun CityExplorerAppHost(modifier: Modifier = Modifier) {
    val navController = rememberNavController()

    Surface(modifier = modifier.fillMaxSize()) {
        NavHost(
            navController = navController,
            startDestination = Screen.CitySelector.route,
            modifier = modifier
        ) {
            composable(Screen.CitySelector.route) {
                CitySelectorScreen(
                    onNavigateToMapScreen = { city ->
                        navController.navigate(Screen.MapScreen("").createRoute(city))
                    }
                )
            }

            composable(
                route = Screen.MapScreen("").route,
                arguments = listOf(navArgument("city") { type = NavType.StringType })
            ) { backStackEntry ->
                val city = backStackEntry.arguments?.getString("city")!!
                MapScreen(city = city)
            }
        }
    }
}
