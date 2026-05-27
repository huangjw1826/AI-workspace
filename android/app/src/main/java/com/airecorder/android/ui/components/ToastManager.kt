package com.airecorder.android.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

// Toast 数据类
data class ToastMessage(
    val id: Long = System.currentTimeMillis(),
    val message: String,
    val type: ToastType = ToastType.Info,
    val duration: Long = 3000L // 默认显示3秒
)

// Toast 类型
enum class ToastType {
    Info,
    Success,
    Warning,
    Error
}

// Toast 管理器状态
class ToastManagerState {
    private val _toastMessage = mutableStateOf<ToastMessage?>(null)
    val toastMessage: State<ToastMessage?> = _toastMessage

    fun show(message: String, type: ToastType = ToastType.Info, duration: Long = 3000L) {
        _toastMessage.value = ToastMessage(message = message, type = type, duration = duration)
    }

    fun hide() {
        _toastMessage.value = null
    }

    fun clear() {
        _toastMessage.value = null
    }
}

// 创建 Toast 管理器实例
@Composable
fun rememberToastManagerState(): ToastManagerState {
    return remember { ToastManagerState() }
}

// Toast 容器组件
@Composable
fun ToastContainer(
    toastManagerState: ToastManagerState,
    modifier: Modifier = Modifier
) {
    val currentToast by toastManagerState.toastMessage

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        AnimatedVisibility(
            visible = currentToast != null,
            enter = fadeIn() + slideInVertically(initialOffsetY = { -it }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { -it })
        ) {
            currentToast?.let { toast ->
                ToastItem(toast = toast, onDismiss = { toastManagerState.hide() })
            }
        }
    }

    // 自动隐藏 Toast
    LaunchedEffect(currentToast) {
        currentToast?.let { toast ->
            delay(toast.duration)
            toastManagerState.hide()
        }
    }
}

// Toast 项组件
@Composable
private fun ToastItem(
    toast: ToastMessage,
    onDismiss: () -> Unit
) {
    val containerColor = when (toast.type) {
        ToastType.Info -> MaterialTheme.colorScheme.secondaryContainer
        ToastType.Success -> MaterialTheme.colorScheme.tertiaryContainer
        ToastType.Warning -> MaterialTheme.colorScheme.primaryContainer
        ToastType.Error -> MaterialTheme.colorScheme.errorContainer
    }
    
    val contentColor = when (toast.type) {
        ToastType.Info -> MaterialTheme.colorScheme.onSecondaryContainer
        ToastType.Success -> MaterialTheme.colorScheme.onTertiaryContainer
        ToastType.Warning -> MaterialTheme.colorScheme.onPrimaryContainer
        ToastType.Error -> MaterialTheme.colorScheme.onErrorContainer
    }

    Surface(
        modifier = Modifier
            .padding(top = 16.dp, start = 16.dp, end = 16.dp)
            .fillMaxWidth(0.95f),
        shape = MaterialTheme.shapes.medium,
        color = containerColor,
        shadowElevation = 4.dp
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp, 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = toast.message,
                style = MaterialTheme.typography.bodyMedium,
                color = contentColor,
                modifier = Modifier.weight(1f)
            )
            
            Spacer(modifier = Modifier.width(8.dp))
            
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "关闭",
                    tint = contentColor,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
