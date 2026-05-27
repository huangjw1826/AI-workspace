package com.airecorder.android.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.airecorder.android.data.model.Recording
import com.airecorder.android.data.model.RecordingDetail
import com.airecorder.android.ui.theme.*

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
    val backgroundColor: Color
    val iconColor: Color
    val text: String
    val icon: androidx.compose.ui.graphics.vector.ImageVector?

    when (status) {
        RecordingStatus.Summarized -> {
            backgroundColor = StatusSuccessLight.copy(alpha = 0.4f)
            iconColor = StatusSuccess
            text = "已摘要"
            icon = Icons.Default.CheckCircle
        }
        RecordingStatus.Transcribed -> {
            backgroundColor = StatusInfoLight.copy(alpha = 0.4f)
            iconColor = StatusInfo
            text = "已转写"
            icon = Icons.Default.CheckCircle
        }
        RecordingStatus.Pending -> {
            backgroundColor = DividerLight.copy(alpha = 0.6f)
            iconColor = TextTertiary
            text = "待处理"
            icon = Icons.Default.Schedule
        }
        RecordingStatus.Processing -> {
            backgroundColor = StatusWarningLight.copy(alpha = 0.4f)
            iconColor = StatusWarning
            text = "处理中"
            icon = Icons.Default.HourglassEmpty
        }
        RecordingStatus.Error -> {
            backgroundColor = StatusErrorLight.copy(alpha = 0.4f)
            iconColor = StatusError
            text = "失败"
            icon = Icons.Default.Error
        }
    }

    Surface(
        modifier = modifier,
        color = backgroundColor,
        shape = RoundedCornerShape(20.dp)
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = if (compact) 8.dp else 12.dp,
                vertical = if (compact) 4.dp else 6.dp
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 6.dp)
        ) {
            if (status is RecordingStatus.Processing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(if (compact) 10.dp else 14.dp),
                    color = iconColor,
                    strokeWidth = if (compact) 1.2.dp else 2.0.dp
                )
            } else if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(if (compact) 12.dp else 14.dp)
                )
            }
            
            if (showText) {
                Text(
                    text = text,
                    fontSize = if (compact) 10.sp else 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = iconColor
                )
            }
        }
    }
}
