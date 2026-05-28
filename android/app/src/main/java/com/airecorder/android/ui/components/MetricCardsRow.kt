package com.airecorder.android.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.airecorder.android.data.model.Recording
import com.airecorder.android.ui.theme.*
import com.airecorder.android.util.FormatUtils

data class RecordingMetrics(
    val totalCount: Int,
    val totalSize: Long,
    val pendingCount: Int,
    val completedCount: Int,
    val aiCompletionRate: Float,
    val totalDuration: Double,
    val errorCount: Int
)

fun List<Recording>.calculateMetrics(): RecordingMetrics {
    val totalCount = size
    val totalSize = sumOf { it.fileSizeBytes ?: 0L }
    val pendingCount = count { 
        it.status == "uploaded" || it.status == "queued" 
    }
    val completedCount = count { it.status == "completed" }
    val aiCompletionRate = if (totalCount > 0) {
        completedCount.toFloat() / totalCount.toFloat()
    } else {
        0f
    }
    val totalDuration = sumOf { it.durationSeconds ?: 0.0 }
    val errorCount = count { it.status == "error" }
    
    return RecordingMetrics(
        totalCount = totalCount,
        totalSize = totalSize,
        pendingCount = pendingCount,
        completedCount = completedCount,
        aiCompletionRate = aiCompletionRate,
        totalDuration = totalDuration,
        errorCount = errorCount
    )
}

@Composable
fun MetricCardsRow(
    recordings: List<Recording>,
    modifier: Modifier = Modifier
) {
    val metrics = recordings.calculateMetrics()
    
    Row(
        modifier = modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        MetricCard(
            title = "全部录音",
            value = metrics.totalCount.toString(),
            subtitle = FormatUtils.formatFileSize(metrics.totalSize),
            color = Primary
        )
        
        Spacer(modifier = Modifier.width(12.dp))
        
        MetricCard(
            title = "待处理",
            value = metrics.pendingCount.toString(),
            progress = if (metrics.totalCount > 0) {
                1f - metrics.aiCompletionRate
            } else {
                0f
            },
            color = StatusWarning
        )
        
        Spacer(modifier = Modifier.width(12.dp))
        
        MetricCard(
            title = "AI 完成率",
            value = "${(metrics.aiCompletionRate * 100).toInt()}%",
            subtitle = "${metrics.completedCount} 已摘要",
            progress = metrics.aiCompletionRate,
            color = StatusSuccess
        )
        
        Spacer(modifier = Modifier.width(12.dp))
        
        MetricCard(
            title = "总时长",
            value = FormatUtils.formatDuration(metrics.totalDuration),
            subtitle = if (metrics.errorCount > 0) "${metrics.errorCount} 错误" else null,
            color = Tertiary
        )
    }
}
