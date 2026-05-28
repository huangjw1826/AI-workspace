package com.airecorder.android.ui.animation

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import com.airecorder.android.ui.theme.AnimationDurations
import com.airecorder.android.ui.theme.AnimationEasing

object PageTransitions {
    
    val enterTransition: EnterTransition
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
    
    val exitTransition: ExitTransition
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
    
    val popEnterTransition: EnterTransition
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
    
    val popExitTransition: ExitTransition
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
