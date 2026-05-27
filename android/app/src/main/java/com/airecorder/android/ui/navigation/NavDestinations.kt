package com.airecorder.android.ui.navigation

sealed class NavDestinations(val route: String) {
    object Library : NavDestinations("library")
    object Settings : NavDestinations("settings")
    object Health : NavDestinations("health")
    object Detail : NavDestinations("detail/{recordingId}") {
        fun createRoute(recordingId: String) = "detail/$recordingId"
    }
    
    companion object {
        // 获取所有底部导航目标
        val bottomNavDestinations = listOf(Library, Settings)
    }
}
