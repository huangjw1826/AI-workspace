package com.airecorder.android.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
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
    modifier: Modifier = Modifier,
    isExpanded: Boolean = true
) {
    AnimatedVisibility(
        visible = isExpanded,
        enter = fadeIn() + expandVertically(expandFrom = Alignment.Top),
        exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Top)
    ) {
        val metrics = recordings.calculateMetrics()
        
        Column(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                MetricCard(
                    title = "全部录音",
                    value = "${metrics.totalCount}",
                    color = Primary,
                    modifier = Modifier.weight(1f)
                )
                
                MetricCard(
                    title = "待处理",
                    value = "${metrics.pendingCount}",
                    color = StatusWarning,
                    modifier = Modifier.weight(1f)
                )
            }
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                MetricCard(
                    title = "AI 完成率",
                    value = "${(metrics.aiCompletionRate * 100).toInt()}%",
                    color = StatusSuccess,
                    modifier = Modifier.weight(1f)
                )
                
                MetricCard(
                    title = "总时长",
                    value = FormatUtils.formatDuration(metrics.totalDuration),
                    color = Tertiary,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}