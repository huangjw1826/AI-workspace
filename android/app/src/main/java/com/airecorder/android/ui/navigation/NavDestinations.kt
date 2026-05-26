package com.airecorder.android.ui.navigation

sealed class NavDestinations(val route: String) {
    object Library : NavDestinations("library")
    object Settings : NavDestinations("settings")
    object Health : NavDestinations("health")
    object Detail : NavDestinations("detail/{recordingId}") {
        fun createRoute(recordingId: String) = "detail/$recordingId"
    }
}
