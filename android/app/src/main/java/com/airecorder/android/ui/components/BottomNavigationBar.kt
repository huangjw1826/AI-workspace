package com.airecorder.android.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.airecorder.android.R
import com.airecorder.android.ui.navigation.NavDestinations
import com.airecorder.android.ui.theme.*
import androidx.compose.ui.text.font.FontWeight

private data class BottomNavItem(
    val destination: NavDestinations,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val label: String
)

@Composable
fun BottomNavigationBar(
    currentDestination: NavDestinations,
    onNavigateTo: (NavDestinations) -> Unit
) {
    val navItems = listOf(
        BottomNavItem(
            destination = NavDestinations.Watch,
            selectedIcon = Icons.Filled.Visibility,
            unselectedIcon = Icons.Outlined.Visibility,
            label = "监控"
        ),
        BottomNavItem(
            destination = NavDestinations.Library,
            selectedIcon = Icons.Filled.Folder,
            unselectedIcon = Icons.Outlined.Folder,
            label = stringResource(R.string.nav_library)
        ),
        BottomNavItem(
            destination = NavDestinations.Settings,
            selectedIcon = Icons.Filled.Settings,
            unselectedIcon = Icons.Outlined.Settings,
            label = stringResource(R.string.nav_settings)
        )
    )
    
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp,
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
    ) {
        navItems.forEach { item ->
            val isSelected = currentDestination == item.destination
            NavigationBarItem(
                selected = isSelected,
                onClick = { onNavigateTo(item.destination) },
                icon = {
                    Icon(
                        imageVector = if (isSelected) item.selectedIcon else item.unselectedIcon,
                        contentDescription = item.label,
                        modifier = Modifier.size(AppIconDefaults.NavItemSize)
                    )
                },
                label = {
                    Text(
                        text = item.label,
                        style = MaterialTheme.typography.labelMedium
                    )
                },
                colors = AppIconDefaults.navItemColors()
            )
        }
    }
}
