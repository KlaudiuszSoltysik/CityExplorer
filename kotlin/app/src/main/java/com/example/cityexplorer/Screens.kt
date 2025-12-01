package com.example.cityexplorer

sealed class Screen(val route: String) {
    data object LoginScreen : Screen("login?returnRoute={returnRoute}") {
        fun createRoute(returnRoute: String? = null): String {
            return if (returnRoute != null) "login?returnRoute=$returnRoute" else "login"
        }
    }
    data object CitySelectorScreen : Screen("city_selector")
    data class MapScreen(val city: String) : Screen("map/{city}") {
        fun createRoute(city: String) = "map/$city"
    }
}