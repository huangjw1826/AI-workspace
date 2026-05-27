package com.airecorder.android.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.airecorder.android.data.model.Recording
import com.airecorder.android.data.model.RecordingDetail

sealed class RecordingStatus {
    object Pending : RecordingStatus()      // 待处理
    object Transcribed : RecordingStatus()  // 已转写
    object Summarized : RecordingStatus()   // 已摘要
    object Processing : RecordingStatus()   // 处理中
    object Error : RecordingStatus()        // 失败
}

/**
 * Enhanced status detection logic based on both basic status and content availability.
 * This directly maps to the server-side states.
 */
fun Recording.getStatus(detail: RecordingDetail? = null): RecordingStatus {
    // Priority 1: Check detailed summaries and segments if available
    if (detail != null) {
        if (detail.summaries.isNotEmpty()) return RecordingStatus.Summarized
        if (detail.segments.isNotEmpty()) return RecordingStatus.Transcribed
    }
    
    // Priority 2: Check server-side status string and tags
    return when {
        status == "summarized" || tags?.contains("summarized") == true -> RecordingStatus.Summarized
        status == "completed" || status == "done" || tags?.contains("transcribed") == true -> RecordingStatus.Transcribed
        status == "processing" || status == "transcribing" -> RecordingStatus.Processing
        status == "error" || status == "failed" -> RecordingStatus.Error
        else -> RecordingStatus.Pending // Includes "pending" status
    }
}

@Composable
fun StatusIndicator(
    status: RecordingStatus,
    modifier: Modifier = Modifier,
    showText: Boolean = true,
    compact: Boolean = false
) {
    val containerColor = when (status) {
        RecordingStatus.Summarized -> MaterialTheme.colorScheme.tertiaryContainer
        RecordingStatus.Transcribed -> MaterialTheme.colorScheme.secondaryContainer
        RecordingStatus.Pending -> MaterialTheme.colorScheme.surfaceVariant
        RecordingStatus.Processing -> MaterialTheme.colorScheme.primaryContainer
        RecordingStatus.Error -> MaterialTheme.colorScheme.errorContainer
    }
    
    val contentColor = when (status) {
        RecordingStatus.Summarized -> MaterialTheme.colorScheme.onTertiaryContainer
        RecordingStatus.Transcribed -> MaterialTheme.colorScheme.onSecondaryContainer
        RecordingStatus.Pending -> MaterialTheme.colorScheme.onSurfaceVariant
        RecordingStatus.Processing -> MaterialTheme.colorScheme.onPrimaryContainer
        RecordingStatus.Error -> MaterialTheme.colorScheme.onErrorContainer
    }
    
    val text = when (status) {
        RecordingStatus.Summarized -> "已摘要"
        RecordingStatus.Transcribed -> "已转写"
        RecordingStatus.Pending -> "待处理"
        RecordingStatus.Processing -> "处理中"
        RecordingStatus.Error -> "失败"
    }
    
    val icon = when (status) {
        RecordingStatus.Summarized -> Icons.Default.CheckCircle
        RecordingStatus.Transcribed -> Icons.Default.CheckCircle
        RecordingStatus.Pending -> Icons.Default.Schedule
        RecordingStatus.Processing -> Icons.Default.HourglassEmpty
        RecordingStatus.Error -> Icons.Default.Error
    }

    Surface(
        modifier = modifier,
        color = containerColor,
        shape = if (compact) MaterialTheme.shapes.small else MaterialTheme.shapes.medium
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = if (compact) 6.dp else 10.dp,
                vertical = if (compact) 3.dp else 5.dp
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(if (compact) 3.dp else 5.dp)
        ) {
            if (status is RecordingStatus.Processing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(if (compact) 10.dp else 14.dp),
                    color = contentColor,
                    strokeWidth = if (compact) 1.5.dp else 2.0.dp
                )
            } else {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(if (compact) 12.dp else 14.dp)
                )
            }
            
            if (showText) {
                Text(
                    text = text,
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = if (compact) 10.sp else 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = contentColor
                )
            }
        }
    }
}

