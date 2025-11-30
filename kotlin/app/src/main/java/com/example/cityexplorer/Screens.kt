package com.example.cityexplorer

sealed class Screen(val route: String) {
    data object LoginScreen : Screen("login?returnRoute={returnRoute}") {
        fun createRoute(returnRoute: String? = null): String {
            return if (returnRoute != null) "login?returnRoute=$returnRoute" else "login"
        }
    }
    data object CitySelectorScreen : Screen("city_selector")

    data class ModeSelectorScreen(val city: String) : Screen("mode_selector/{city}") {
        fun createRoute(city: String) = "mode_selector/$city"
    }

    data class MapScreen(val city: String, val mode: String) : Screen("map/{city}/{mode}") {
        fun createRoute(city: String, mode: String) = "map/$city/$mode"
    }
}