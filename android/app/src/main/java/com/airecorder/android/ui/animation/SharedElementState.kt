package com.airecorder.android.ui.animation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size

/**
 * 共享元素过渡状态 — 在录音列表和详情页之间传递源元素的全局位置，
 * 供覆盖层动画使用。
 */
class SharedElementState {
    /** 列表中点击项的窗口坐标 */
    var sourceBounds by mutableStateOf(Rect.Zero)
        private set

    /** 是否正在执行过渡动画 */
    var isTransitioning by mutableStateOf(false)
        private set

    /** 当前过渡的录音 ID（避免重复触发） */
    var transitioningId by mutableStateOf<String?>(null)
        private set

    /** 录音文件名（覆盖层标题） */
    var fileName by mutableStateOf("")
        private set

    /** 录音时长文本（覆盖层副标题） */
    var subtitle by mutableStateOf("")
        private set

    fun startTransition(
        id: String,
        name: String,
        sub: String,
        bounds: Rect
    ) {
        if (isTransitioning && transitioningId == id) return
        transitioningId = id
        fileName = name
        subtitle = sub
        sourceBounds = bounds
        isTransitioning = true
    }

    fun endTransition() {
        isTransitioning = false
        transitioningId = null
    }
}
