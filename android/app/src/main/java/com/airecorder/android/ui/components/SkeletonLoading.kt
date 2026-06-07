package com.airecorder.android.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.airecorder.android.ui.theme.*

/**
 * 骨架屏列表 — 波形扫描线动画
 */
@Composable
fun SkeletonLoading(
    modifier: Modifier = Modifier,
    itemCount: Int = 4
) {
    val infiniteTransition = rememberInfiniteTransition(label = "shimmer")

    // 波浪扫描动画 — 模拟声波从左到右扫过
    val shimmerOffset by infiniteTransition.animateFloat(
        initialValue = -1f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = EaseInOutSine),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer_x"
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        repeat(itemCount) {
            SkeletonCard(shimmerOffset = shimmerOffset)
        }
    }
}

@Composable
private fun SkeletonCard(shimmerOffset: Float) {
    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant
    val shimmerBase = surfaceVariant
    val shimmerHighlight = surfaceVariant.copy(alpha = 0.4f)

    Card(
        modifier = Modifier
            .fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = CardShape,
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = androidx.compose.ui.Alignment.Top
        ) {
            // 左侧波形图标占位
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(shimmerBase)
            )

            Spacer(modifier = Modifier.width(14.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // 标题行占位
                ShimmerBlock(
                    widthFraction = 0.6f,
                    height = 18.dp,
                    shimmerOffset = shimmerOffset,
                    shimmerBase = shimmerBase,
                    shimmerHighlight = shimmerHighlight
                )

                // 元数据行占位
                ShimmerBlock(
                    widthFraction = 0.4f,
                    height = 14.dp,
                    shimmerOffset = shimmerOffset + 0.2f,
                    shimmerBase = shimmerBase,
                    shimmerHighlight = shimmerHighlight
                )
            }

            // 右侧状态徽章占位
            Box(
                modifier = Modifier
                    .size(48.dp, 20.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(shimmerBase)
            )
        }
    }
}

@Composable
private fun ShimmerBlock(
    widthFraction: Float,
    height: androidx.compose.ui.unit.Dp,
    shimmerOffset: Float,
    shimmerBase: Color,
    shimmerHighlight: Color
) {
    Box(
        modifier = Modifier
            .fillMaxWidth(widthFraction)
            .height(height)
            .clip(RoundedCornerShape(6.dp))
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(shimmerBase, shimmerHighlight, shimmerBase),
                    start = Offset(shimmerOffset * 500f, 0f),
                    end = Offset(shimmerOffset * 500f + 200f, 0f)
                )
            )
    )
}

// 自定义缓动
private val EaseInOutSine: Easing = CubicBezierEasing(0.37f, 0f, 0.63f, 1f)
