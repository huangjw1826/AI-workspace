package com.airecorder.android

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.airecorder.android.data.local.PreferencesManager
import com.airecorder.android.ui.theme.AIRecorderTheme
import com.airecorder.android.ui.AIRecorderApp
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var preferencesManager: PreferencesManager

    override fun onCreate(savedInstanceState: Bundle?) {
        // Splash Screen API：消除冷启动白屏，品牌色平滑过渡到 Compose 内容
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val deepLinkRecordingId = intent?.data?.let { uri ->
            if (uri.scheme == "airecorder" && uri.host == "recording") {
                uri.pathSegments.firstOrNull()
            } else null
        }

        val deepLinkTarget = when {
            deepLinkRecordingId != null -> "recording/$deepLinkRecordingId"
            intent?.data?.toString() == "airecorder://settings" -> "settings"
            else -> null
        }

        setContent {
            val useDynamicColor by preferencesManager.dynamicColor.collectAsState(initial = true)

            AIRecorderTheme(darkTheme = isSystemInDarkTheme(), dynamicColor = useDynamicColor) {
                // 暗黑模式切换时背景色平滑过渡（不重建导航状态）
                val animatedBg by animateColorAsState(
                    targetValue = MaterialTheme.colorScheme.background,
                    animationSpec = tween(400),
                    label = "bg_color"
                )

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = animatedBg
                ) {
                    AIRecorderApp(initialDeepLink = deepLinkTarget)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }
}
