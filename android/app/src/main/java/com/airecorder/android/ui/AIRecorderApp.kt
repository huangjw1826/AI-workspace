package com.airecorder.android.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.airecorder.android.data.local.PreferencesManager
import com.airecorder.android.data.model.Summary
import com.airecorder.android.di.PreferencesManagerEntryPoint
import com.airecorder.android.ui.animation.PageTransitions
import com.airecorder.android.ui.animation.SharedElementOverlay
import com.airecorder.android.ui.animation.SharedElementState
import com.airecorder.android.ui.components.BottomNavigationBar
import com.airecorder.android.ui.components.ToastContainer
import com.airecorder.android.ui.components.rememberToastManagerState
import com.airecorder.android.ui.navigation.NavDestinations
import com.airecorder.android.ui.screens.DetailScreen
import com.airecorder.android.ui.screens.LibraryScreen
import com.airecorder.android.ui.screens.SettingsScreen
import com.airecorder.android.ui.screens.UploadBottomSheet
import com.airecorder.android.ui.screens.detail.SummaryDetailScreen
import com.airecorder.android.ui.screens.watch.WatchScreen
import dagger.hilt.android.EntryPointAccessors
import kotlinx.serialization.json.Json

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun AIRecorderApp(
    navController: NavHostController = rememberNavController(),
    initialDeepLink: String? = null
) {
    val actions = remember(navController) { AppActions(navController) }
    val toastManager = rememberToastManagerState()
    var showUploadSheet by remember { mutableStateOf(false) }
    
    // 共享元素过渡状态
    val sharedElementState = remember { SharedElementState() }
    
    // 获取当前回退栈条目，用于判断是否显示底部导航栏
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination?.route
    
    // 判断是否需要显示底部导航栏（仅在主页面显示）
    val showBottomBar = when (currentDestination) {
        NavDestinations.Library.route,
        NavDestinations.Watch.route,
        NavDestinations.Settings.route -> true
        else -> false
    }
    
    // 获取当前导航目标，用于底部导航栏高亮
    val currentNavDest = when (currentDestination) {
        NavDestinations.Library.route -> NavDestinations.Library
        NavDestinations.Watch.route -> NavDestinations.Watch
        NavDestinations.Settings.route -> NavDestinations.Settings
        else -> NavDestinations.Library // 默认
    }

    // 处理 DeepLink
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

    Scaffold(
        bottomBar = {
            AnimatedVisibility(
                visible = showBottomBar,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
            ) {
                BottomNavigationBar(
                    currentDestination = currentNavDest,
                    onNavigateTo = { dest ->
                        when (dest) {
                            NavDestinations.Library -> actions.navigateToLibrary()
                            NavDestinations.Watch -> actions.navigateToWatch()
                            NavDestinations.Settings -> actions.navigateToSettings()
                            else -> {}
                        }
                    }
                )
            }
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues)) {
            // Toast 容器
            ToastContainer(toastManagerState = toastManager)
            
            // 主要导航内容
            NavHost(
                navController = navController,
                startDestination = NavDestinations.Library.route,
                modifier = Modifier.fillMaxSize()
            ) {
                composable(
                    route = NavDestinations.Library.route,
                    enterTransition = { PageTransitions.popEnterTransition },
                    exitTransition = { PageTransitions.exitTransition },
                    popEnterTransition = { PageTransitions.popEnterTransition },
                    popExitTransition = { PageTransitions.popExitTransition }
                ) {
                    val preferencesManager = EntryPointAccessors.fromApplication(
                        LocalContext.current.applicationContext as android.app.Application,
                        PreferencesManagerEntryPoint::class.java
                    ).preferencesManager()
                    
                    LibraryScreen(
                        preferencesManager = preferencesManager,
                        onNavigateToDetail = actions.navigateToDetail,
                        onUploadClick = { showUploadSheet = true },
                        sharedElementState = sharedElementState,
                        onRecordItemClick = { id, name, sub, coords ->
                            val pos = coords?.positionInWindow() ?: androidx.compose.ui.geometry.Offset.Zero
                            val bounds = Rect(
                                pos.x, pos.y,
                                pos.x + (coords?.size?.width ?: 0),
                                pos.y + (coords?.size?.height ?: 0)
                            )
                            sharedElementState.startTransition(id, name, sub, bounds)
                            actions.navigateToDetail(id)
                        }
                    )
                }
                composable(
                    route = NavDestinations.Watch.route,
                    enterTransition = { PageTransitions.enterTransition },
                    exitTransition = { PageTransitions.exitTransition },
                    popEnterTransition = { PageTransitions.popEnterTransition },
                    popExitTransition = { PageTransitions.popExitTransition }
                ) {
                    WatchScreen(onNavigateBack = actions.navigateBack)
                }
                composable(
                    route = NavDestinations.Settings.route,
                    enterTransition = { PageTransitions.enterTransition },
                    exitTransition = { PageTransitions.exitTransition },
                    popEnterTransition = { PageTransitions.popEnterTransition },
                    popExitTransition = { PageTransitions.popExitTransition }
                ) {
                    SettingsScreen()
                }
                composable(
                    route = NavDestinations.Detail.ROUTE_TEMPLATE,
                    enterTransition = { PageTransitions.enterTransition },
                    exitTransition = { PageTransitions.exitTransition },
                    popEnterTransition = { PageTransitions.popEnterTransition },
                    popExitTransition = { PageTransitions.popExitTransition }
                ) { backStackEntry ->
                    val recordingId = backStackEntry.arguments?.getString("recordingId") ?: ""
                    DetailScreen(
                        recordingId = recordingId,
                        onNavigateBack = actions.navigateBack,
                        onNavigateToSummaryDetail = actions.navigateToSummaryDetail,
                        onNavigateToLibrary = actions.navigateToLibrary,
                        onNavigateToSettings = actions.navigateToSettings,
                        sharedElementState = sharedElementState
                    )
                }
                composable(
                    route = NavDestinations.SummaryDetail.ROUTE_TEMPLATE,
                    enterTransition = { PageTransitions.enterTransition },
                    exitTransition = { PageTransitions.exitTransition },
                    popEnterTransition = { PageTransitions.popEnterTransition },
                    popExitTransition = { PageTransitions.popExitTransition }
                ) { backStackEntry ->
                    val summaryJson = backStackEntry.arguments?.getString("summaryJson") ?: ""
                    val summary = try {
                        val decodedBytes = android.util.Base64.decode(summaryJson, android.util.Base64.URL_SAFE)
                        val decodedJson = String(decodedBytes, Charsets.UTF_8)
                        Json.decodeFromString<Summary>(decodedJson)
                    } catch (_: Exception) {
                        null
                    }
                    
                    if (summary != null) {
                        SummaryDetailScreen(
                            summary = summary,
                            onBack = actions.navigateBack,
                            onDelete = { /* 后续实现删除逻辑 */ },
                            onExport = { /* 后续实现导出逻辑 */ }
                        )
                    }
                }
            }
            
            // 共享元素飞入覆盖层（最上层）
            if (sharedElementState.isTransitioning) {
                SharedElementOverlay(
                    state = sharedElementState,
                    destinationY = 0f,
                    onComplete = { sharedElementState.endTransition() }
                )
            }
        }
    }

    if (showUploadSheet) {
        UploadBottomSheet(onDismiss = { showUploadSheet = false })
    }
}

private class AppActions(navController: NavHostController) {
    val navigateToDetail: (String) -> Unit = { recordingId ->
        navController.navigate(NavDestinations.Detail.createRoute(recordingId))
    }
    
    val navigateToSummaryDetail: (Summary) -> Unit = { summary ->
        navController.navigate(NavDestinations.SummaryDetail.createRoute(summary))
    }
    
    val navigateToWatch: () -> Unit = {
        navController.navigate(NavDestinations.Watch.route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }
    
    val navigateToSettings: () -> Unit = {
        navController.navigate(NavDestinations.Settings.route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }
    
    val navigateToLibrary: () -> Unit = {
        navController.navigate(NavDestinations.Library.route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }
    
    val navigateBack: () -> Unit = {
        navController.navigateUp()
    }
}
