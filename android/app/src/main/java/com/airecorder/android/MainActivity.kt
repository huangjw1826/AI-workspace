package com.airecorder.android

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.airecorder.android.ui.theme.AIRecorderTheme
import com.airecorder.android.ui.AIRecorderApp
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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
            AIRecorderTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
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
