package com.airecorder.android.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.airecorder.android.data.model.Recording
import com.airecorder.android.ui.theme.*
import com.airecorder.android.util.FormatUtils

/**
 * 录音卡片 — 左侧声波装饰线 + 状态徽章 + 选择模式
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RecordingItem(
    recording: Recording,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onPositioned: ((LayoutCoordinates) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val cardColor = if (isSelected) {
        PrimaryContainer.copy(alpha = 0.6f)
    } else {
        Surface
    }

    val borderModifier = if (isSelected) {
        Modifier.border(2.dp, Primary, CardShape)
    } else {
        Modifier
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 5.dp)
            .then(borderModifier)
            .combinedClickable(
                onClick = if (isSelectionMode) onLongClick else onClick,
                onLongClick = onLongClick
            )
            .then(if (onPositioned != null) Modifier.onGloballyPositioned { onPositioned(it) } else Modifier),
        colors = CardDefaults.cardColors(containerColor = cardColor),
        shape = CardShape,
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 选择模式：RadioButton
            if (isSelectionMode) {
                RadioButton(
                    selected = isSelected,
                    onClick = onLongClick,
                    modifier = Modifier
                        .padding(end = 12.dp)
                        .semantics {
                            contentDescription = if (isSelected) "已选择 ${recording.filename}" else "选择 ${recording.filename}"
                        },
                    colors = RadioButtonDefaults.colors(selectedColor = Primary)
                )
            }

            // 左侧图标区 — 声波装饰背景
            Box(
                modifier = Modifier.size(44.dp),
                contentAlignment = Alignment.Center
            ) {
                // 圆形底板
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    shape = RoundedCornerShape(12.dp),
                    color = if (isSelected) Primary.copy(alpha = 0.15f) else PrimaryContainer
                ) {}

                if (isSelected) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "已选择",
                        tint = Primary,
                        modifier = Modifier.size(22.dp)
                    )
                } else {
                    // 声波图标
                    WaveformIcon(
                        color = Primary.copy(alpha = 0.6f),
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            // 右侧内容区
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                // 第一行：文件名 + 状态徽章
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = recording.filename,
                        style = RecordingTitleStyle,
                        color = if (isSelected) OnPrimaryContainer else OnSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    // 状态小徽章
                    StatusBadge(label = recording.statusLabel, color = recording.statusColor)
                }

                Spacer(modifier = Modifier.height(6.dp))

                // 第二行：时长 · 大小 · 来源
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MetaText(text = FormatUtils.formatDuration(recording.durationSeconds))

                    MetaDot()

                    MetaText(text = FormatUtils.formatFileSize(recording.fileSizeBytes))

                    Spacer(modifier = Modifier.weight(1f))

                    recording.sourceType?.let { source ->
                        val sourceLabel = when (source) {
                            "upload" -> "上传"
                            "watch" -> "目录监控"
                            else -> source
                        }
                        MetaText(text = sourceLabel)
                    }
                }

                // 错误状态提示
                if (recording.isError) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "处理失败",
                        style = MaterialTheme.typography.bodySmall,
                        color = Error,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

/**
 * 状态小徽章 — 圆角标签
 */
@Composable
fun StatusBadge(
    label: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = StatusChipShape,
        color = color.copy(alpha = 0.12f)
    ) {
        Text(
            text = label,
            style = StatusLabelStyle,
            color = color,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
        )
    }
}

/**
 * 元数据文字
 */
@Composable
private fun MetaText(text: String) {
    Text(
        text = text,
        style = RecordingMetaStyle
    )
}

/**
 * 元数据分隔圆点
 */
@Composable
private fun MetaDot() {
    Surface(
        modifier = Modifier.size(3.dp),
        shape = CircleShape,
        color = TextTertiary.copy(alpha = 0.5f)
    ) {}
}

/**
 * 声波图标 — Canvas 绘制
 */
@Composable
fun WaveformIcon(
    color: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val centerY = h / 2
        val strokeWidth = h * 0.12f

        // 三条竖线模拟声波
        val bars = listOf(0.3f, 0.5f, 0.7f)
        bars.forEachIndexed { index, xRatio ->
            val barHeight = h * (0.3f + 0.4f * (1 - kotlin.math.abs(index - 1f)))
            drawLine(
                color = color,
                start = Offset(w * xRatio, centerY - barHeight / 2),
                end = Offset(w * xRatio, centerY + barHeight / 2),
                strokeWidth = strokeWidth,
                cap = androidx.compose.ui.graphics.StrokeCap.Round
            )
        }
    }
}
