package com.airecorder.android.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.airecorder.android.ui.theme.*

// ============================================================
// 通用加载状态
// ============================================================
@Composable
fun LoadingState(
    modifier: Modifier = Modifier,
    message: String? = null
) {
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(40.dp),
            color = Primary,
            strokeWidth = 3.dp
        )
        message?.let {
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                textAlign = TextAlign.Center
            )
        }
    }
}

// ============================================================
// 空录音库 — Canvas 声波插画
// ============================================================
@Composable
fun EmptyLibraryView(
    modifier: Modifier = Modifier,
    onUploadClick: (() -> Unit)? = null
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 声波空状态插画
        EmptyWaveformIllustration(
            modifier = Modifier.size(120.dp)
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "还没有录音",
            style = MaterialTheme.typography.headlineSmall,
            color = TextPrimary,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "点击右下角 ⊕ 上传音频文件\n或通过 PC 端导入录音",
            style = MaterialTheme.typography.bodyMedium,
            color = TextTertiary,
            textAlign = TextAlign.Center,
            lineHeight = 20.sp
        )

        onUploadClick?.let { onClick ->
            Spacer(modifier = Modifier.height(32.dp))
            Button(
                onClick = onClick,
                shape = ButtonShape,
                colors = ButtonDefaults.buttonColors(containerColor = Primary)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("上传音频")
            }
        }
    }
}

// ============================================================
// 空搜索结果
// ============================================================
@Composable
fun EmptyFilteredView(
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.SearchOff,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = TextTertiary.copy(alpha = 0.5f)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "没有找到匹配的录音",
            style = MaterialTheme.typography.titleMedium,
            color = TextSecondary,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "尝试调整筛选条件或搜索关键词",
            style = MaterialTheme.typography.bodyMedium,
            color = TextTertiary
        )
    }
}

// ============================================================
// 通用空状态
// ============================================================
@Composable
fun EmptyState(
    modifier: Modifier = Modifier,
    title: String,
    subtitle: String? = null,
    action: (@Composable () -> Unit)? = null
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        EmptyWaveformIllustration(modifier = Modifier.size(100.dp))

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            color = TextPrimary,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        subtitle?.let {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                color = TextTertiary,
                textAlign = TextAlign.Center
            )
        }

        action?.let {
            Spacer(modifier = Modifier.height(32.dp))
            it()
        }
    }
}

// ============================================================
// 错误状态
// ============================================================
@Composable
fun ErrorState(
    modifier: Modifier = Modifier,
    title: String = "加载失败",
    error: String,
    onRetry: () -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // 断开的波形 — 错误插画
        BrokenWaveformIcon(
            modifier = Modifier.size(80.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = TextPrimary
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = error,
            style = MaterialTheme.typography.bodyMedium,
            color = TextTertiary,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(24.dp))
        OutlinedButton(
            onClick = onRetry,
            shape = ButtonShape,
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Primary)
        ) {
            Text("重新加载")
        }
    }
}

// ============================================================
// Canvas 插画：空状态声波
// ============================================================
@Composable
fun EmptyWaveformIllustration(
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "empty_wave")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave_phase"
    )

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val centerY = h * 0.5f
        val strokeColor = Primary.copy(alpha = 0.2f)
        val activeColor = Primary.copy(alpha = 0.5f)

        // 7 条声波柱
        val barCount = 7
        val barWidth = w * 0.04f
        val gap = w / (barCount + 1)
        val maxHeight = h * 0.6f

        for (i in 0 until barCount) {
            val x = gap * (i + 1)
            // 每根柱子有不同的相位偏移
            val barPhase = (phase + i * 0.15f) % 1f
            val heightRatio = 0.3f + 0.7f * (1f - kotlin.math.abs(barPhase - 0.5f) * 2f)
            val barHeight = maxHeight * heightRatio

            // 当前活动的柱子用更亮的颜色
            val color = if (kotlin.math.abs(barPhase - 0.5f) < 0.15f) activeColor else strokeColor

            drawLine(
                color = color,
                start = Offset(x, centerY - barHeight / 2),
                end = Offset(x, centerY + barHeight / 2),
                strokeWidth = barWidth,
                cap = StrokeCap.Round
            )
        }
    }
}

// ============================================================
// Canvas 插画：断开的波形（错误状态）
// ============================================================
@Composable
fun BrokenWaveformIcon(
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val centerY = h * 0.5f
        val stroke = h * 0.05f

        // 左侧2条正常
        drawLine(
            color = Error.copy(alpha = 0.3f),
            start = Offset(w * 0.2f, centerY - h * 0.15f),
            end = Offset(w * 0.2f, centerY + h * 0.15f),
            strokeWidth = stroke, cap = StrokeCap.Round
        )
        drawLine(
            color = Error.copy(alpha = 0.2f),
            start = Offset(w * 0.3f, centerY - h * 0.25f),
            end = Offset(w * 0.3f, centerY + h * 0.25f),
            strokeWidth = stroke, cap = StrokeCap.Round
        )

        // 中间断裂处 — X 标记
        val cx = w * 0.5f
        val cy = centerY
        val crossSize = h * 0.15f
        drawLine(
            color = Error.copy(alpha = 0.6f),
            start = Offset(cx - crossSize, cy - crossSize),
            end = Offset(cx + crossSize, cy + crossSize),
            strokeWidth = stroke * 1.5f, cap = StrokeCap.Round
        )
        drawLine(
            color = Error.copy(alpha = 0.6f),
            start = Offset(cx + crossSize, cy - crossSize),
            end = Offset(cx - crossSize, cy + crossSize),
            strokeWidth = stroke * 1.5f, cap = StrokeCap.Round
        )

        // 右侧2条依然正常
        drawLine(
            color = Error.copy(alpha = 0.2f),
            start = Offset(w * 0.7f, centerY - h * 0.2f),
            end = Offset(w * 0.7f, centerY + h * 0.2f),
            strokeWidth = stroke, cap = StrokeCap.Round
        )
        drawLine(
            color = Error.copy(alpha = 0.3f),
            start = Offset(w * 0.8f, centerY - h * 0.1f),
            end = Offset(w * 0.8f, centerY + h * 0.1f),
            strokeWidth = stroke, cap = StrokeCap.Round
        )
    }
}
