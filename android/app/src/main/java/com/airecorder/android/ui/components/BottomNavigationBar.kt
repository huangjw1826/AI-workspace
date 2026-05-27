package com.airecorder.android.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.airecorder.android.R
import com.airecorder.android.ui.navigation.NavDestinations
import com.airecorder.android.ui.theme.Primary
import com.airecorder.android.ui.theme.TextSecondary
import com.airecorder.android.ui.theme.Surface

@Composable
fun BottomNavigationBar(
    currentDestination: NavDestinations,
    onNavigateTo: (NavDestinations) -> Unit
) {
    Surface(
        color = Surface,
        tonalElevation = 0.dp,
        modifier = Modifier.fillMaxWidth().height(80.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            NavItem(
                icon = if (currentDestination == NavDestinations.Library) Icons.Filled.Home else Icons.Outlined.Home,
                label = "Home",
                selected = currentDestination == NavDestinations.Library,
                onClick = { onNavigateTo(NavDestinations.Library) }
            )
            NavItem(
                icon = if (currentDestination == NavDestinations.Health) Icons.Filled.MonitorHeart else Icons.Outlined.MonitorHeart,
                label = "Health",
                selected = currentDestination == NavDestinations.Health,
                onClick = { onNavigateTo(NavDestinations.Health) }
            )
            NavItem(
                icon = if (currentDestination == NavDestinations.Settings) Icons.Filled.Person else Icons.Outlined.Person,
                label = "Profile",
                selected = currentDestination == NavDestinations.Settings,
                onClick = { onNavigateTo(NavDestinations.Settings) }
            )
        }
    }
}

@Composable
private fun NavItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(64.dp)
            .height(64.dp)
            .clickable(
                onClick = onClick,
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (selected) Primary else TextSecondary,
            modifier = Modifier.size(26.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) Primary else TextSecondary,
            fontSize = 11.sp
        )
    }
}
