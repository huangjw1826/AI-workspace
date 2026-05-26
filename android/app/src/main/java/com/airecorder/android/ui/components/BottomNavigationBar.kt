package com.airecorder.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.airecorder.android.R
import com.airecorder.android.ui.navigation.NavDestinations
import com.airecorder.android.ui.theme.Primary
import com.airecorder.android.ui.theme.TextTertiary

data class BottomNavItem(
    val label: String,
    val icon: ImageVector,
    val destination: NavDestinations
)

@Composable
fun BottomNavigationBar(
    currentDestination: NavDestinations,
    onNavigateTo: (NavDestinations) -> Unit
) {
    val items = listOf(
        BottomNavItem(
            label = stringResource(R.string.nav_library),
            icon = Icons.Default.Folder,
            destination = NavDestinations.Library
        ),
        BottomNavItem(
            label = stringResource(R.string.nav_settings),
            icon = Icons.Default.Settings,
            destination = NavDestinations.Settings
        ),
        BottomNavItem(
            label = stringResource(R.string.nav_health),
            icon = Icons.Default.HealthAndSafety,
            destination = NavDestinations.Health
        )
    )
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.SpaceAround
    ) {
        items.forEach { item ->
            val isSelected = currentDestination == item.destination
            NavigationBarItem(
                selected = isSelected,
                onClick = { onNavigateTo(item.destination) },
                icon = {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = item.label,
                        tint = if (isSelected) Primary else TextTertiary
                    )
                },
                label = {
                    Text(
                        text = item.label,
                        color = if (isSelected) Primary else TextTertiary,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            )
        }
    }
}
