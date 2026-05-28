package com.airecorder.android.ui.util

import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView

class HapticFeedbackHelper(private val view: View) {
    
    fun performClick() {
        view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
    }
    
    fun performLongPress() {
        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
    }
    
    fun performConfirm() {
        view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
    }
    
    fun performReject() {
        view.performHapticFeedback(HapticFeedbackConstants.REJECT)
    }
    
    fun performHeavyClick() {
        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
    }
}

@Composable
fun rememberHapticFeedback(): HapticFeedbackHelper {
    val view = LocalView.current
    return remember(view) { HapticFeedbackHelper(view) }
}
