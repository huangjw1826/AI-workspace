package com.airecorder.android.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.airecorder.android.R
import com.airecorder.android.ui.navigation.NavDestinations
import com.airecorder.android.ui.theme.Primary
import com.airecorder.android.ui.theme.SurfaceVariant
import com.airecorder.android.ui.theme.AppIconDefaults
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight

// 底部导航项目数据类
private data class BottomNavItem(
    val destination: NavDestinations,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val label: String
)

@Composable
fun BottomNavigationBar(
    currentDestination: NavDestinations,
    onNavigateTo: (NavDestinations) -> Unit,
    onUploadClick: () -> Unit
) {
    // Material 3 导航栏
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp,
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
    ) {
        // 1. 录音库
        val isLibrarySelected = currentDestination == NavDestinations.Library
        NavigationBarItem(
            selected = isLibrarySelected,
            onClick = { onNavigateTo(NavDestinations.Library) },
            icon = {
                Icon(
                    imageVector = if (isLibrarySelected) Icons.Filled.Folder else Icons.Outlined.Folder,
                    contentDescription = stringResource(R.string.nav_library),
                    modifier = Modifier.size(AppIconDefaults.NavItemSize)
                )
            },
            label = {
                Text(
                    text = stringResource(R.string.nav_library),
                    style = MaterialTheme.typography.labelMedium
                )
            },
            colors = AppIconDefaults.navItemColors()
        )

        // 2. 中间上传按钮 (带交互样式)
        NavigationBarItem(
            selected = false,
            onClick = onUploadClick,
            icon = {
                Surface(
                    shape = androidx.compose.foundation.shape.CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(48.dp),
                    shadowElevation = 4.dp
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = stringResource(R.string.upload),
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            },
            label = {
                Text(
                    text = stringResource(R.string.upload),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            },
            alwaysShowLabel = true,
            colors = NavigationBarItemDefaults.colors(
                indicatorColor = Color.Transparent
            )
        )

        // 3. 设置
        val isSettingsSelected = currentDestination == NavDestinations.Settings
        NavigationBarItem(
            selected = isSettingsSelected,
            onClick = { onNavigateTo(NavDestinations.Settings) },
            icon = {
                Icon(
                    imageVector = if (isSettingsSelected) Icons.Filled.Settings else Icons.Outlined.Settings,
                    contentDescription = stringResource(R.string.nav_settings),
                    modifier = Modifier.size(AppIconDefaults.NavItemSize)
                )
            },
            label = {
                Text(
                    text = stringResource(R.string.nav_settings),
                    style = MaterialTheme.typography.labelMedium
                )
            },
            colors = AppIconDefaults.navItemColors()
        )
    }
}

