package com.airecorder.android.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.airecorder.android.ui.navigation.NavDestinations
import com.airecorder.android.ui.theme.*

// ============================================================
// 「悬浮雕塑」底部导航栏 — 中间主位按钮 + 两侧辅助Tab
// ============================================================

private data class SculptureNavItem(
    val destination: NavDestinations,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val label: String,
    val isCenter: Boolean = false
)

@Composable
fun BottomNavigationBar(
    currentDestination: NavDestinations,
    onNavigateTo: (NavDestinations) -> Unit
) {
    val navItems = listOf(
        SculptureNavItem(
            destination = NavDestinations.Settings,
            selectedIcon = Icons.Filled.Settings,
            unselectedIcon = Icons.Outlined.Settings,
            label = "设置"
        ),
        SculptureNavItem(
            destination = NavDestinations.Library,
            selectedIcon = Icons.Filled.Folder,
            unselectedIcon = Icons.Outlined.Folder,
            label = "首页",
            isCenter = true
        ),
        SculptureNavItem(
            destination = NavDestinations.Health,
            selectedIcon = Icons.Filled.Favorite,
            unselectedIcon = Icons.Outlined.Favorite,
            label = "健康"
        )
    )

    val colorScheme = MaterialTheme.colorScheme

    // 磨砂背景色：surface 80% 透明度
    val frostedBg = colorScheme.surface.copy(alpha = 0.80f)

    // 中心按钮渐变色：primaryContainer → secondaryContainer
    val centerGradient = Brush.linearGradient(
        colors = listOf(
            colorScheme.primaryContainer,
            colorScheme.secondaryContainer
        )
    )

    // 侧边指示器颜色（呼应中心渐变）
    val indicatorColor = colorScheme.primary

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        // ========== 磨砂悬浮卡片 ==========
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(68.dp)
                .shadow(
                    elevation = 16.dp,
                    shape = RoundedCornerShape(28.dp),
                    ambientColor = Color.Black.copy(alpha = 0.08f),
                    spotColor = Color.Black.copy(alpha = 0.12f)
                )
                .clip(RoundedCornerShape(28.dp))
                .background(frostedBg)
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                navItems.forEach { item ->
                    if (item.isCenter) {
                        // 中间留空，中心按钮在外层叠加
                        Spacer(modifier = Modifier.weight(1f))
                    } else {
                        // 两侧辅助Tab
                        SideNavItem(
                            item = item,
                            isSelected = currentDestination == item.destination,
                            indicatorColor = indicatorColor,
                            onClick = { onNavigateTo(item.destination) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        // ========== 中间主位按钮（叠加在卡片上方） ==========
        CenterNavButton(
            item = navItems[1],
            isSelected = currentDestination == navItems[1].destination,
            gradient = centerGradient,
            onClick = { onNavigateTo(navItems[1].destination) },
            modifier = Modifier.align(Alignment.TopCenter)
        )
    }
}

// ============================================================
// 中间主位按钮 — 向上凸起 + 渐变背景 + 光晕
// ============================================================

@Composable
private fun CenterNavButton(
    item: SculptureNavItem,
    isSelected: Boolean,
    gradient: Brush,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    // 弹性缩放动画
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.92f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "center_scale"
    )

    // 选中态光晕扩散
    val glowAlpha by animateFloatAsState(
        targetValue = if (isSelected) 1f else 0f,
        animationSpec = tween(400, easing = FastOutSlowInEasing),
        label = "glow_alpha"
    )

    // 图标颜色
    val iconColor by animateColorAsState(
        targetValue = if (isSelected) colorSchemeOnPrimaryContainer() else TextTertiary,
        animationSpec = tween(300),
        label = "center_icon_color"
    )

    // 文字颜色
    val textColor by animateColorAsState(
        targetValue = if (isSelected) colorSchemeOnPrimaryContainer() else TextTertiary,
        animationSpec = tween(300),
        label = "center_text_color"
    )

    // 波纹动画（点击时触发）
    var showRipple by remember { mutableStateOf(false) }
    val rippleAlpha by animateFloatAsState(
        targetValue = if (showRipple) 0f else 0.3f,
        animationSpec = tween(600, easing = FastOutSlowInEasing),
        label = "ripple_alpha",
        finishedListener = { if (showRipple) showRipple = false }
    )
    val rippleScale by animateFloatAsState(
        targetValue = if (showRipple) 1.8f else 1f,
        animationSpec = tween(600, easing = FastOutSlowInEasing),
        label = "ripple_scale"
    )

    val colorScheme = MaterialTheme.colorScheme

    Box(
        modifier = modifier
            .offset(y = (-12).dp) // 向上凸起 12dp
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null
            ) {
                showRipple = true
                onClick()
            },
        contentAlignment = Alignment.Center
    ) {
        // 光晕层
        if (glowAlpha > 0f) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .graphicsLayer {
                        alpha = glowAlpha * 0.4f
                    }
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                colorScheme.primary.copy(alpha = 0.25f),
                                Color.Transparent
                            )
                        ),
                        shape = CircleShape
                    )
            )
        }

        // 波纹层
        if (showRipple) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .graphicsLayer {
                        scaleX = rippleScale
                        scaleY = rippleScale
                        alpha = rippleAlpha
                    }
                    .background(
                        color = colorScheme.primary.copy(alpha = 0.15f),
                        shape = CircleShape
                    )
            )
        }

        // 主按钮体
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            // 渐变背景胶囊
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .shadow(
                        elevation = if (isSelected) 12.dp else 6.dp,
                        shape = RoundedCornerShape(18.dp),
                        ambientColor = colorScheme.primary.copy(alpha = 0.15f),
                        spotColor = colorScheme.primary.copy(alpha = 0.25f)
                    )
                    .clip(RoundedCornerShape(18.dp))
                    .background(brush = gradient),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isSelected) item.selectedIcon else item.unselectedIcon,
                    contentDescription = item.label,
                    tint = iconColor,
                    modifier = Modifier.size(28.dp)
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = item.label,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    fontSize = 12.sp
                ),
                color = textColor
            )
        }
    }
}

// ============================================================
// 两侧辅助Tab — 小尺寸 + 底部彩色短线指示器
// ============================================================

@Composable
private fun SideNavItem(
    item: SculptureNavItem,
    isSelected: Boolean,
    indicatorColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    // 按压缩放（95%）
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "side_scale"
    )

    // 图标颜色过渡
    val iconColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.primary else TextTertiary,
        animationSpec = tween(300),
        label = "side_icon_color"
    )

    // 文字颜色过渡
    val textColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.primary else TextTertiary,
        animationSpec = tween(300),
        label = "side_text_color"
    )

    // 指示器颜色
    val barColor by animateColorAsState(
        targetValue = if (isSelected) indicatorColor else Color.Transparent,
        animationSpec = tween(300, easing = FastOutSlowInEasing),
        label = "side_bar_color"
    )

    Column(
        modifier = modifier
            .height(60.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = if (isSelected) item.selectedIcon else item.unselectedIcon,
            contentDescription = item.label,
            tint = iconColor,
            modifier = Modifier.size(22.dp)
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = item.label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
                fontSize = 11.sp
            ),
            color = textColor
        )

        Spacer(modifier = Modifier.height(4.dp))

        // 底部彩色短线指示器（4dp高）
        Box(
            modifier = Modifier
                .width(20.dp)
                .height(3.dp)
                .clip(RoundedCornerShape(1.5.dp))
                .background(barColor)
        )
    }
}

// ============================================================
// 辅助函数：获取 onPrimaryContainer 颜色
// ============================================================

@Composable
private fun colorSchemeOnPrimaryContainer(): Color {
    return MaterialTheme.colorScheme.onPrimaryContainer
}
