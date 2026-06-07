package com.airecorder.android.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.airecorder.android.ui.theme.*
import kotlinx.coroutines.delay

// ============================================================
// 加载指示器
// ============================================================
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
            color = Primary,
            strokeWidth = 3.dp
        )
    }
}

// ============================================================
// 成功动画
// ============================================================
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

// ============================================================
// 卡片淡入动画
// ============================================================
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

// ============================================================
// 数值变化动画
// ============================================================
@Composable
fun AnimatedCounter(
    targetValue: Int,
    modifier: Modifier = Modifier,
    prefix: String = "",
    suffix: String = ""
) {
    val animatedValue by animateIntAsState(
        targetValue = targetValue,
        animationSpec = tween(durationMillis = 500, easing = FastOutSlowInEasing),
        label = "counter"
    )

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

// ============================================================
// 呼吸脉冲动画（FAB 专用）
// ============================================================
@Composable
fun BreathingPulseAnimation(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "breathing")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breath_scale"
    )

    Box(
        modifier = modifier
            .scale(scale)
            .graphicsLayer {
                // 微妙的透明度变化
                alpha = 0.85f + (scale - 1f) * 3f
            }
    ) {
        content()
    }
}

// ============================================================
// FAB 上传按钮（呼吸动画版）
// ============================================================
@Composable
fun UploadFAB(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    BreathingPulseAnimation(
        modifier = modifier
    ) {
        FloatingActionButton(
            onClick = onClick,
            shape = FABShape,
            containerColor = Primary,
            contentColor = Color.White,
            elevation = FloatingActionButtonDefaults.elevation(
                defaultElevation = 6.dp,
                pressedElevation = 10.dp
            ),
            modifier = Modifier
                .size(56.dp)
                .shadow(12.dp, FABShape, ambientColor = Primary.copy(alpha = 0.3f))
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "上传音频",
                modifier = Modifier.size(26.dp)
            )
        }
    }
}

// ============================================================
// 淡入淡出内容切换
// ============================================================
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

// ============================================================
// 自定义缓动曲线
// ============================================================
private val EaseInOutCubic: Easing = CubicBezierEasing(0.65f, 0f, 0.35f, 1f)
