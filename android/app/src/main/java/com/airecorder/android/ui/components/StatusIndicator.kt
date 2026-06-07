package com.airecorder.android.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.airecorder.android.data.model.Recording
import com.airecorder.android.data.model.RecordingDetail
import com.airecorder.android.ui.theme.*

/**
 * 录音状态枚举
 */
sealed class RecordingStatus {
    object Pending : RecordingStatus()       // 待处理
    object Transcribed : RecordingStatus()   // 已转写
    object Summarized : RecordingStatus()    // 已摘要
    object Processing : RecordingStatus()    // 处理中
    object Error : RecordingStatus()         // 失败
}

/**
 * 从录音数据推断状态
 */
fun Recording.getStatus(detail: RecordingDetail? = null): RecordingStatus {
    if (detail != null) {
        if (detail.summaries.isNotEmpty()) return RecordingStatus.Summarized
        if (detail.segments.isNotEmpty()) return RecordingStatus.Transcribed
    }
    return when {
        status == "summarized" || tags?.contains("summarized") == true -> RecordingStatus.Summarized
        status == "completed" || status == "done" || tags?.contains("transcribed") == true -> RecordingStatus.Transcribed
        status == "processing" || status == "transcribing" -> RecordingStatus.Processing
        status == "error" || status == "failed" -> RecordingStatus.Error
        else -> RecordingStatus.Pending
    }
}

/**
 * 状态指示器 — 圆点 + 标签样式（健康面板使用）
 */
@Composable
fun StatusDot(
    isHealthy: Boolean,
    label: String,
    sublabel: String? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 状态圆点（带发光效果）
        val dotColor = if (isHealthy) HealthGreen else HealthRed
        Canvas(modifier = Modifier.size(10.dp)) {
            // 外发光
            drawCircle(
                color = dotColor.copy(alpha = 0.25f),
                radius = size.minDimension / 2 + 2.dp.toPx()
            )
            // 核心圆点
            drawCircle(color = dotColor)
        }

        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = TextPrimary
            )
            sublabel?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextTertiary
                )
            }
        }
    }
}

/**
 * 健康面板状态行
 */
@Composable
fun HealthStatusRow(
    label: String,
    value: String,
    dotColor: Color,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Canvas(modifier = Modifier.size(8.dp)) {
                drawCircle(color = dotColor)
            }
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )
        }
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = TextPrimary,
            fontWeight = FontWeight.Medium
        )
    }
}

/**
 * 录音状态小徽章（列表项使用）— 已被 RecordingItem.StatusBadge 替代
 * 保留此兼容方法，内部委托给 StatusBadge
 */
@Composable
fun StatusIndicator(
    status: RecordingStatus,
    modifier: Modifier = Modifier,
    showText: Boolean = true,
    compact: Boolean = false
) {
    val containerColor = when (status) {
        RecordingStatus.Summarized -> StatusSuccessLight
        RecordingStatus.Transcribed -> SecondaryContainer
        RecordingStatus.Pending -> SurfaceVariant
        RecordingStatus.Processing -> StatusWarningLight
        RecordingStatus.Error -> ErrorContainer
    }

    val contentColor = when (status) {
        RecordingStatus.Summarized -> StatusSuccess
        RecordingStatus.Transcribed -> Secondary
        RecordingStatus.Pending -> TextTertiary
        RecordingStatus.Processing -> StatusWarning
        RecordingStatus.Error -> Error
    }

    val text = when (status) {
        RecordingStatus.Summarized -> "已摘要"
        RecordingStatus.Transcribed -> "已转写"
        RecordingStatus.Pending -> "待处理"
        RecordingStatus.Processing -> "处理中"
        RecordingStatus.Error -> "失败"
    }

    Surface(
        modifier = modifier,
        color = containerColor,
        shape = BadgeShape
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = if (compact) 6.dp else 8.dp,
                vertical = if (compact) 3.dp else 4.dp
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(if (compact) 3.dp else 4.dp)
        ) {
            if (status is RecordingStatus.Processing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(if (compact) 10.dp else 12.dp),
                    color = contentColor,
                    strokeWidth = 1.5.dp
                )
            }
            if (showText) {
                Text(
                    text = text,
                    style = StatusLabelStyle,
                    color = contentColor
                )
            }
        }
    }
}
