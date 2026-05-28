package com.airecorder.android.ui.screens.detail

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.airecorder.android.data.model.Task
import com.airecorder.android.ui.components.EmptyContentCard
import com.airecorder.android.util.FormatUtils

@Composable
fun TaskTab(
    tasks: List<Task>,
    modifier: Modifier = Modifier
) {
    if (tasks.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            EmptyContentCard(
                icon = Icons.Default.AutoAwesome,
                title = "暂无任务记录",
                subtitle = "当您执行转写或摘要操作时，这里会显示相关任务"
            )
        }
    } else {
        LazyColumn(
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(
                items = tasks.sortedByDescending { it.createdAt },
                key = { it.id }
            ) { task ->
                TaskItem(task = task)
            }
        }
    }
}

@Composable
fun TaskItem(
    task: Task,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TaskIcon(
                        type = task.type,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = getTaskTitle(task.type),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                TaskStatusBadge(status = task.status)
            }
            
            if (task.progress != null) {
                LinearProgressIndicator(
                    progress = { task.progress / 100f },
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.primaryContainer
                )
            }
            
            if (task.error != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Error,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                    Text(
                        text = task.error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = if (task.createdAt != null) {
                        "创建: ${FormatUtils.formatDate(task.createdAt)}"
                    } else {
                        "创建时间未知"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
                if (task.updatedAt != null) {
                    Text(
                        text = "更新: ${FormatUtils.formatDate(task.updatedAt)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        }
    }
}

@Composable
fun TaskIcon(
    type: String?,
    modifier: Modifier = Modifier
) {
    val (icon, color) = when (type?.lowercase()) {
        "transcribe" -> Icons.Default.Subtitles to MaterialTheme.colorScheme.primary
        "summarize", "summary" -> Icons.Default.AutoAwesome to MaterialTheme.colorScheme.tertiary
        else -> Icons.Default.AutoAwesome to MaterialTheme.colorScheme.outline
    }
    
    Icon(
        imageVector = icon,
        contentDescription = null,
        modifier = modifier,
        tint = color
    )
}

@Composable
fun TaskStatusBadge(
    status: String?,
    modifier: Modifier = Modifier
) {
    val (text, color) = when (status?.lowercase()) {
        "pending", "queued" -> "等待中" to MaterialTheme.colorScheme.secondary
        "processing", "normalizing", "transcribing" -> "处理中" to MaterialTheme.colorScheme.primary
        "completed", "success" -> "已完成" to MaterialTheme.colorScheme.tertiary
        "failed", "error" -> "失败" to MaterialTheme.colorScheme.error
        "cancelled" -> "已取消" to MaterialTheme.colorScheme.outline
        else -> "未知" to MaterialTheme.colorScheme.outline
    }
    
    Surface(
        modifier = modifier,
        color = color.copy(alpha = 0.12f),
        shape = RoundedCornerShape(8.dp)
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = color
        )
    }
}

private fun getTaskTitle(type: String?): String {
    return when (type?.lowercase()) {
        "transcribe" -> "音频转写"
        "summarize", "summary" -> "生成摘要"
        else -> "未知任务"
    }
}
