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
import com.airecorder.android.ui.components.OfflineBanner
import com.airecorder.android.ui.components.ToastContainer
import com.airecorder.android.ui.components.rememberOfflineState
import com.airecorder.android.ui.components.rememberToastManagerState
import com.airecorder.android.ui.navigation.NavDestinations
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import com.airecorder.android.ui.screens.DetailScreen
import com.airecorder.android.ui.screens.HealthScreen
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

    // ========== 离线监测 ==========
    val context = LocalContext.current
    val preferencesManager = remember {
        EntryPointAccessors.fromApplication(
            context.applicationContext as android.app.Application,
            PreferencesManagerEntryPoint::class.java
        ).preferencesManager()
    }
    val serverUrl by preferencesManager.serverUrl.collectAsState(initial = "")

    val isOffline = rememberOfflineState(
        checkHealth = {
            if (serverUrl.isBlank()) {
                false // 未配置服务器不算离线
            } else {
                withContext(Dispatchers.IO) {
                    try {
                        val url = URL("${serverUrl.trimEnd('/')}/health")
                        val conn = url.openConnection() as HttpURLConnection
                        conn.connectTimeout = 5000
                        conn.readTimeout = 5000
                        conn.requestMethod = "GET"
                        conn.responseCode == 200
                    } catch (_: Exception) {
                        false
                    }
                }
            }
        },
        intervalMs = 30_000L
    )

    // 获取当前回退栈条目
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination?.route

    // 底栏显示规则：仅在 3 个主页显示
    val showBottomBar = when (currentDestination) {
        NavDestinations.Library.route,
        NavDestinations.Settings.route,
        NavDestinations.Health.route -> true
        else -> false
    }

    // 当前导航目标（用于底栏高亮）
    val currentNavDest = when (currentDestination) {
        NavDestinations.Library.route -> NavDestinations.Library
        NavDestinations.Settings.route -> NavDestinations.Settings
        NavDestinations.Health.route -> NavDestinations.Health
        else -> NavDestinations.Library
    }

    // 处理 DeepLink
    LaunchedEffect(initialDeepLink) {
        initialDeepLink?.let { deepLink ->
            when {
                deepLink.startsWith("recording/") -> {
                    val recordingId = deepLink.removePrefix("recording/")
                    actions.navigateToDetail(recordingId)
                }
                deepLink == "settings" -> actions.navigateToSettings()
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
                            NavDestinations.Settings -> actions.navigateToSettings()
                            NavDestinations.Health -> actions.navigateToHealth()
                            else -> {}
                        }
                    }
                )
            }
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues)) {
            // 离线横幅（所有页面顶部常驻）
            OfflineBanner(
                visible = isOffline && serverUrl.isNotBlank(),
                modifier = Modifier.align(androidx.compose.ui.Alignment.TopCenter)
            )

            // Toast 容器
            ToastContainer(toastManagerState = toastManager)

            // 主要导航内容
            NavHost(
                navController = navController,
                startDestination = NavDestinations.Library.route,
                modifier = Modifier.fillMaxSize()
            ) {
                // ========== 3 个主页 ==========
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
                            val pos = coords?.positionInWindow()
                                ?: androidx.compose.ui.geometry.Offset.Zero
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
                    route = NavDestinations.Settings.route,
                    enterTransition = { PageTransitions.enterTransition },
                    exitTransition = { PageTransitions.exitTransition },
                    popEnterTransition = { PageTransitions.popEnterTransition },
                    popExitTransition = { PageTransitions.popExitTransition }
                ) {
                    SettingsScreen(
                        onNavigateToWatch = actions.navigateToWatch,
                        onNavigateBack = actions.navigateBack
                    )
                }

                composable(
                    route = NavDestinations.Health.route,
                    enterTransition = { PageTransitions.enterTransition },
                    exitTransition = { PageTransitions.exitTransition },
                    popEnterTransition = { PageTransitions.popEnterTransition },
                    popExitTransition = { PageTransitions.popExitTransition }
                ) {
                    HealthScreen()
                }

                // ========== 详情页 ==========
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
                        val decodedBytes = android.util.Base64.decode(
                            summaryJson, android.util.Base64.URL_SAFE
                        )
                        val decodedJson = String(decodedBytes, Charsets.UTF_8)
                        Json.decodeFromString<Summary>(decodedJson)
                    } catch (_: Exception) {
                        null
                    }

                    if (summary != null) {
                        SummaryDetailScreen(
                            summary = summary,
                            onBack = actions.navigateBack,
                            onDelete = { /* 后续实现 */ },
                            onExport = { /* 后续实现 */ }
                        )
                    }
                }

                // ========== 监控页（设置页内导航）==========
                composable(
                    route = NavDestinations.Watch.route,
                    enterTransition = { PageTransitions.enterTransition },
                    exitTransition = { PageTransitions.exitTransition },
                    popEnterTransition = { PageTransitions.popEnterTransition },
                    popExitTransition = { PageTransitions.popExitTransition }
                ) {
                    WatchScreen(onNavigateBack = actions.navigateBack)
                }
            }

            // 共享元素飞入覆盖层
            if (sharedElementState.isTransitioning) {
                SharedElementOverlay(
                    state = sharedElementState,
                    destinationY = 0f,
                    onComplete = { sharedElementState.endTransition() }
                )
            }
        }
    }

    // 上传底部 Sheet
    if (showUploadSheet) {
        UploadBottomSheet(onDismiss = { showUploadSheet = false })
    }
}

/**
 * 导航操作集合
 */
private class AppActions(navController: NavHostController) {
    val navigateToDetail: (String) -> Unit = { recordingId ->
        navController.navigate(NavDestinations.Detail.createRoute(recordingId))
    }

    val navigateToSummaryDetail: (Summary) -> Unit = { summary ->
        navController.navigate(NavDestinations.SummaryDetail.createRoute(summary))
    }

    val navigateToWatch: () -> Unit = {
        navController.navigate(NavDestinations.Watch.route) {
            launchSingleTop = true
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
            // 点击"首页"主按钮：清空浏览栈，回到首页初始状态
            popUpTo(navController.graph.findStartDestination().id) {
                inclusive = true
            }
            launchSingleTop = true
        }
    }

    val navigateToHealth: () -> Unit = {
        navController.navigate(NavDestinations.Health.route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    val navigateBack: () -> Unit = {
        navController.navigateUp()
    }
}
