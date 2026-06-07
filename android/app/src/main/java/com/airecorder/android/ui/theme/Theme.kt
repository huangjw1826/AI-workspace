package com.airecorder.android.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

// ============================================================
// 「有机录音室」主题 — 暖橄榄绿 + 琥珀
// ============================================================

// ========== 浅色主题配色方案 ==========
private val LightColorScheme = lightColorScheme(
    primary = Primary,
    onPrimary = OnPrimary,
    primaryContainer = PrimaryContainer,
    onPrimaryContainer = OnPrimaryContainer,

    secondary = Secondary,
    onSecondary = OnSecondary,
    secondaryContainer = SecondaryContainer,
    onSecondaryContainer = OnSecondaryContainer,

    tertiary = Tertiary,
    onTertiary = OnTertiary,
    tertiaryContainer = TertiaryContainer,
    onTertiaryContainer = OnTertiaryContainer,

    error = Error,
    onError = OnError,
    errorContainer = ErrorContainer,
    onErrorContainer = OnErrorContainer,

    background = Background,
    onBackground = OnBackground,

    surface = Surface,
    onSurface = OnSurface,
    surfaceVariant = SurfaceVariant,
    onSurfaceVariant = OnSurfaceVariant,

    outline = Outline,
    outlineVariant = OutlineVariant,

    scrim = Color.Black.copy(alpha = 0.32f)
)

// ========== 深色主题配色方案 ==========
private val DarkColorScheme = darkColorScheme(
    primary = DarkPrimary,
    onPrimary = Color(0xFF0A1F10),
    primaryContainer = DarkPrimaryContainer,
    onPrimaryContainer = Color(0xFFD5F5DD),

    secondary = Color(0xFF7BC89A),
    onSecondary = Color(0xFF0A2A14),
    secondaryContainer = Color(0xFF1E4E2E),
    onSecondaryContainer = Color(0xFFD5F5DD),

    tertiary = Color(0xFF8BC4F0),
    onTertiary = Color(0xFF0A2A40),
    tertiaryContainer = Color(0xFF1E4E64),
    onTertiaryContainer = Color(0xFFD9F0FF),

    error = Color(0xFFFFB4AB),
    onError = Color(0xFF560E0E),
    errorContainer = Color(0xFF7A1A1A),
    onErrorContainer = Color(0xFFFFDAD6),

    background = DarkBackground,
    onBackground = DarkOnSurface,

    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,

    outline = DarkOutline,
    outlineVariant = Color(0xFF555950),

    scrim = Color.Black.copy(alpha = 0.5f)
)

// ========== 导航栏设计规范 ==========
object AppIconDefaults {
    val Size = 24.dp
    val NavItemSize = 26.dp
    val SelectedAlpha = 1.0f
    val UnselectedAlpha = 0.55f

    @Composable
    fun navItemColors() = NavigationBarItemDefaults.colors(
        selectedIconColor = MaterialTheme.colorScheme.primary,
        selectedTextColor = MaterialTheme.colorScheme.primary,
        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = UnselectedAlpha),
        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = UnselectedAlpha),
        indicatorColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
    )
}

// ========== 主题入口 ==========
@Composable
fun AIRecorderTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // 默认关闭，确保自定义主题在所有设备上生效
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = Shapes,
        content = content
    )
}
