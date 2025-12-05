package com.example.cityexplorer

sealed class Screen(val route: String) {

    // Login screen with optional return route
    data object LoginScreen : Screen("login?${Args.RETURN_ROUTE}={${Args.RETURN_ROUTE}}") {
        fun createRoute(returnRoute: String? = null): String {
            return if (returnRoute != null) {
                "login?${Args.RETURN_ROUTE}=$returnRoute"
            } else {
                "login"
            }
        }
    }

    // Simple screen without arguments
    data object CitySelectorScreen : Screen("city_selector")

    // Map screen with a mandatory argument
    data object MapScreen : Screen("map/{${Args.CITY}}") {
        fun createRoute(city: String): String = "map/$city"
    }

    // Constants for argument keys to avoid magic strings throughout the app
    object Args {
        const val CITY = "city"
        const val RETURN_ROUTE = "returnRoute"
    }
}