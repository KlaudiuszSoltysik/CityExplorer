package com.example.cityexplorer.ui.navigation

sealed class Screen(val route: String) {
    data object CitySelector : Screen("city_selector")
    data class MapScreen(val city: String) : Screen("map/{city}") {
        fun createRoute(city: String) = "map/$city"
    }
}