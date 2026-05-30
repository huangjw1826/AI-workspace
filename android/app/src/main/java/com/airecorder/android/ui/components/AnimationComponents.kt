package com.airecorder.android.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.airecorder.android.ui.theme.Primary
import com.airecorder.android.ui.theme.PrimaryContainer
import com.airecorder.android.ui.theme.StatusSuccess
import com.airecorder.android.ui.theme.StatusSuccessLight
import kotlinx.coroutines.delay

// 加载动画
@Composable
fun LoadingIndicator(
    modifier: Modifier = Modifier,
    size: Int = 40
) {
    val infiniteTransition = rememberInfiniteTransition(label = "loading")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )
    
    Box(
        modifier = modifier.size(size.dp),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(
            modifier = Modifier
                .size(size.dp)
                .rotate(rotation),
            color = MaterialTheme.colorScheme.primary,
            strokeWidth = 3.dp
        )
    }
}

// 成功动画
@Composable
fun SuccessAnimation(
    modifier: Modifier = Modifier,
    onComplete: () -> Unit = {}
) {
    var visible by remember { mutableStateOf(true) }
    val scale by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "scale"
    )
    
    LaunchedEffect(Unit) {
        delay(2000)
        visible = false
        onComplete()
    }
    
    Box(
        modifier = modifier.size(80.dp),
        contentAlignment = Alignment.Center
    ) {
        AnimatedVisibility(
            visible = visible,
            enter = scaleIn() + fadeIn(),
            exit = scaleOut() + fadeOut()
        ) {
            Surface(
                modifier = Modifier.scale(scale),
                color = StatusSuccessLight,
                shape = RoundedCornerShape(50)
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "成功",
                    tint = StatusSuccess,
                    modifier = Modifier.size(80.dp).padding(20.dp)
                )
            }
        }
    }
}

// 卡片淡入动画
@Composable
fun FadeInCard(
    visible: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(300)) + slideInVertically(
            initialOffsetY = { -20 },
            animationSpec = tween(300)
        ),
        exit = fadeOut(animationSpec = tween(200))
    ) {
        Box(modifier = modifier) {
            content()
        }
    }
}

// 数值变化动画
@Composable
fun AnimatedCounter(
    targetValue: Int,
    modifier: Modifier = Modifier,
    prefix: String = "",
    suffix: String = ""
) {
    var currentValue by remember { mutableStateOf(0) }
    val animatedValue by animateIntAsState(
        targetValue = targetValue,
        animationSpec = tween(durationMillis = 500, easing = FastOutSlowInEasing),
        label = "counter"
    )
    
    LaunchedEffect(targetValue) {
        currentValue = targetValue
    }
    
    Text(
        text = "$prefix$animatedValue$suffix",
        style = MaterialTheme.typography.titleLarge.copy(
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        ),
        color = Primary,
        modifier = modifier
    )
}

// 脉冲动画
@Composable
fun PulseAnimation(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )
    
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.7f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )
    
    Box(
        modifier = modifier
            .scale(scale)
            .graphicsLayer { this.alpha = alpha }
    ) {
        content()
    }
}

// 淡入淡出的内容切换
@Composable
fun AnimatedContentSwitch(
    isLoading: Boolean,
    modifier: Modifier = Modifier,
    loadingContent: @Composable () -> Unit = { LoadingIndicator() },
    successContent: @Composable () -> Unit
) {
    AnimatedContent(
        targetState = isLoading,
        transitionSpec = {
            (fadeIn(animationSpec = tween(200)) + slideInVertically { it / 2 })
                .togetherWith(fadeOut(animationSpec = tween(150)) + slideOutVertically { -it / 2 })
                .using(SizeTransform(clip = false))
        },
        label = "content_switch"
    ) { loading ->
        if (loading) {
            Box(
                modifier = modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                loadingContent()
            }
        } else {
            successContent()
        }
    }
}
