package com.airecorder.android.ui.animation

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import com.airecorder.android.ui.theme.AnimationDurations
import com.airecorder.android.ui.theme.AnimationEasing

object PageTransitions {
    
    @OptIn(ExperimentalAnimationApi::class)
    val enterTransition: EnterTransition
        @Composable
        get() = slideInHorizontally(
            animationSpec = tween(
                durationMillis = AnimationDurations.PageTransition,
                easing = AnimationEasing.FastOutSlowInEasing
            ),
            initialOffsetX = { it }
        ) + fadeIn(
            animationSpec = tween(
                durationMillis = AnimationDurations.PageTransition,
                easing = AnimationEasing.FastOutSlowInEasing
            )
        )
    
    @OptIn(ExperimentalAnimationApi::class)
    val exitTransition: ExitTransition
        @Composable
        get() = slideOutHorizontally(
            animationSpec = tween(
                durationMillis = AnimationDurations.PageTransitionExit,
                easing = AnimationEasing.FastOutLinearInEasing
            ),
            targetOffsetX = { it }
        ) + fadeOut(
            animationSpec = tween(
                durationMillis = AnimationDurations.PageTransitionExit,
                easing = AnimationEasing.FastOutLinearInEasing
            )
        )
    
    @OptIn(ExperimentalAnimationApi::class)
    val popEnterTransition: EnterTransition
        @Composable
        get() = slideInHorizontally(
            animationSpec = tween(
                durationMillis = AnimationDurations.PageTransitionExit,
                easing = AnimationEasing.FastOutSlowInEasing
            ),
            initialOffsetX = { -it }
        ) + fadeIn(
            animationSpec = tween(
                durationMillis = AnimationDurations.PageTransitionExit,
                easing = AnimationEasing.FastOutSlowInEasing
            )
        )
    
    @OptIn(ExperimentalAnimationApi::class)
    val popExitTransition: ExitTransition
        @Composable
        get() = slideOutHorizontally(
            animationSpec = tween(
                durationMillis = AnimationDurations.PageTransition,
                easing = AnimationEasing.FastOutLinearInEasing
            ),
            targetOffsetX = { -it }
        ) + fadeOut(
            animationSpec = tween(
                durationMillis = AnimationDurations.PageTransition,
                easing = AnimationEasing.FastOutLinearInEasing
            )
        )
}
