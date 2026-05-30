package com.airecorder.android.ui.animation

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.airecorder.android.ui.theme.*
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/**
 * 共享元素飞入覆盖层 — 从录音列表中点击位置飞入详情页顶部。
 *
 * 阶段 1（0-250ms）：从 sourceBounds 位置向顶部移动并收缩透明度
 * 阶段 2：自动消失，触发 onComplete
 */
@Composable
fun SharedElementOverlay(
    state: SharedElementState,
    destinationY: Float = 0f, // 详情页标题区域的 Y 坐标（px）
    onComplete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current

    // 目标区域：详情页顶部居中
    val targetX = with(density) { 72.dp.toPx() } // 左边距（对应详情页图标位置）
    val targetWidth = with(density) { 200.dp.toPx() }

    // 动画进度 0→1
    var progress by remember { mutableStateOf(0f) }
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(300),
        label = "shared_elem_progress"
    )

    // 启动动画
    LaunchedEffect(state.isTransitioning) {
        if (state.isTransitioning) {
            progress = 0f
            // 短暂延迟让 Compose 先渲染起始位置
            kotlinx.coroutines.delay(16)
            progress = 1f
            kotlinx.coroutines.delay(350)
            onComplete()
        }
    }

    if (!state.isTransitioning && progress == 0f) return

    val sourceRect = state.sourceBounds
    if (sourceRect == androidx.compose.ui.geometry.Rect.Zero) return

    // 插值计算当前位置
    val currentX = sourceRect.left + (targetX - sourceRect.left) * animatedProgress
    val currentY = sourceRect.top + (destinationY - sourceRect.top) * animatedProgress
    val currentWidth = sourceRect.width + (targetWidth - sourceRect.width) * animatedProgress
    val currentAlpha = (1f - animatedProgress * 0.4f).coerceIn(0.3f, 1f)

    // 圆角变化
    val cornerRadiusPx = with(density) { 16.dp.toPx() * (1f - animatedProgress) + 8.dp.toPx() * animatedProgress }
    val cornerRadiusDp = with(density) { cornerRadiusPx.toDp() }

    Box(
        modifier = modifier
            .offset { IntOffset(currentX.roundToInt(), currentY.roundToInt()) }
            .width(with(density) { (currentWidth / density.density).dp })
            .alpha(currentAlpha)
            .shadow(8.dp, RoundedCornerShape(cornerRadiusDp))
            .clip(RoundedCornerShape(cornerRadiusDp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                modifier = Modifier.size(40.dp),
                shape = RoundedCornerShape(12.dp),
                color = PrimaryContainer
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = null,
                        tint = Primary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = state.fileName,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = state.subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                    maxLines = 1
                )
            }
        }
    }
}
