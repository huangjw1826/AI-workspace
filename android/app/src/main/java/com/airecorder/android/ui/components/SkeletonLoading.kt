package com.airecorder.android.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import com.airecorder.android.ui.theme.*

@Composable
fun SkeletonLoading(
    modifier: Modifier = Modifier,
    itemCount: Int = 4
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        repeat(itemCount) {
            SkeletonCard()
        }
    }
}

@Composable
fun SkeletonCard() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
        ) {
            SkeletonRectangle(
                width = 0.6f,
                height = 20.dp
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            SkeletonRectangle(
                width = 0.4f,
                height = 16.dp
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            SkeletonRectangle(
                width = 0.8f,
                height = 16.dp
            )
        }
    }
}

@Composable
fun SkeletonRectangle(
    width: Float,
    height: androidx.compose.ui.unit.Dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "skeleton")
    val shimmerOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer"
    )
    
    // 使用主题色系，深色/浅色模式自适应
    val shimmerBase = MaterialTheme.colorScheme.surfaceVariant
    val shimmerHighlight = shimmerBase.copy(alpha = 0.3f)
    val shimmerColors = listOf(
        shimmerBase,
        shimmerHighlight,
        shimmerBase
    )
    
    Box(
        modifier = Modifier
            .fillMaxWidth(width)
            .height(height)
            .clip(RoundedCornerShape(4.dp))
            .background(
                brush = Brush.linearGradient(
                    colors = shimmerColors,
                    start = Offset.Zero,
                    end = Offset(
                        shimmerOffset * 500f,
                        shimmerOffset * 500f
                    )
                )
            )
    )
}
