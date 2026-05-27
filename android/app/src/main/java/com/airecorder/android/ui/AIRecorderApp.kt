package com.airecorder.android.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.airecorder.android.ui.navigation.NavDestinations
import com.airecorder.android.ui.screens.DetailScreen
import com.airecorder.android.ui.screens.LibraryScreen
import com.airecorder.android.ui.screens.SettingsScreen

@Composable
fun AIRecorderApp(
    navController: NavHostController = rememberNavController(),
    initialDeepLink: String? = null
) {
    val actions = remember(navController) { AppActions(navController) }

    LaunchedEffect(initialDeepLink) {
        initialDeepLink?.let { deepLink ->
            when {
                deepLink.startsWith("recording/") -> {
                    val recordingId = deepLink.removePrefix("recording/")
                    actions.navigateToDetail(recordingId)
                }
                deepLink == "settings" -> {
                    actions.navigateToSettings()
                }
            }
        }
    }

    NavHost(
        navController = navController,
        startDestination = NavDestinations.Library.route
    ) {
        composable(NavDestinations.Library.route) {
            LibraryScreen(
                onNavigateToDetail = actions.navigateToDetail,
                onNavigateToSettings = actions.navigateToSettings
            )
        }
        composable(NavDestinations.Settings.route) {
            SettingsScreen(
                onNavigateBack = actions.navigateBack,
                onNavigateToLibrary = actions.navigateToLibrary
            )
        }
        composable(NavDestinations.Detail.route) { backStackEntry ->
            val recordingId = backStackEntry.arguments?.getString("recordingId") ?: ""
            DetailScreen(
                recordingId = recordingId,
                onNavigateBack = actions.navigateBack,
                onNavigateToLibrary = actions.navigateToLibrary,
                onNavigateToSettings = actions.navigateToSettings,
                onNavigateToHealth = {}
            )
        }
    }
}

private class AppActions(navController: NavHostController) {
    val navigateToDetail: (String) -> Unit = { recordingId ->
        navController.navigate(NavDestinations.Detail.createRoute(recordingId))
    }
    val navigateToSettings: () -> Unit = {
        navController.navigate(NavDestinations.Settings.route)
    }
    val navigateToLibrary: () -> Unit = {
        navController.navigate(NavDestinations.Library.route) {
            popUpTo(NavDestinations.Library.route) { inclusive = true }
        }
    }
    val navigateBack: () -> Unit = {
        navController.navigateUp()
    }
}
