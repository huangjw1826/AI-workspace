package com.airecorder.android.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.airecorder.android.ui.theme.*
import kotlinx.coroutines.delay

/**
 * 离线状态横幅 — PC 断连时在所有页面顶部显示
 *
 * 使用方式：
 * ```
 * var isOffline by remember { mutableStateOf(false) }
 * OfflineBanner(visible = isOffline, onRetry = { /* re-check */ })
 * // ... rest of content ...
 * ```
 */
@Composable
fun OfflineBanner(
    visible: Boolean,
    onRetry: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut()
    ) {
        Surface(
            modifier = modifier.fillMaxWidth(),
            color = ErrorContainer
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onRetry)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.CloudOff,
                    contentDescription = null,
                    tint = Error,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = "PC 已离线，请确认 PC 开机且 cloudflared 正在运行",
                    style = MaterialTheme.typography.labelMedium,
                    color = Error,
                    modifier = Modifier.weight(1f),
                    fontWeight = FontWeight.Medium
                )
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "重试连接",
                    tint = Error,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

/**
 * 健康检查轮询 Hook
 *
 * @param checkHealth 挂起函数，返回 true 表示健康
 * @param intervalMs 轮询间隔
 * @return 是否离线
 */
@Composable
fun rememberOfflineState(
    checkHealth: suspend () -> Boolean,
    intervalMs: Long = 30_000L
): Boolean {
    var isOffline by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        while (true) {
            try {
                isOffline = !checkHealth()
            } catch (_: Exception) {
                isOffline = true
            }
            delay(intervalMs)
        }
    }

    return isOffline
}
