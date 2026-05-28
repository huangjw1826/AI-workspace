package com.airecorder.android.ui.theme

import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing

object AnimationDurations {
    const val PageTransition = 300
    const val PageTransitionExit = 250
    const val ListItemEnter = 300
    const val ListItemExit = 250
    const val CardClick = 100
    const val Snackbar = 250
    const val NumberAnimation = 500
    const val ProgressBar = 300
    const val TabSwitch = 250
    const val SearchFocus = 200
    const val StaggerDelay = 50
}

object AnimationEasing {
    val FastOutSlowInEasing: Easing = androidx.compose.animation.core.FastOutSlowInEasing
    val FastOutLinearInEasing: Easing = androidx.compose.animation.core.FastOutLinearInEasing
    val LinearOutSlowInEasing: Easing = androidx.compose.animation.core.LinearOutSlowInEasing
}
