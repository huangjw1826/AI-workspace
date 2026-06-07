package com.airecorder.android.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

// ============================================================
// 「有机录音室」形状系统 — 大圆角营造柔和触感
// ============================================================

// 圆角尺寸定义
val XSmallCorner = 4.dp
val SmallCorner = 8.dp
val MediumCorner = 12.dp
val LargeCorner = 16.dp
val XLargeCorner = 20.dp
val FullCorner = 50.dp

// 常用圆角形状
val XSmallShape = RoundedCornerShape(XSmallCorner)
val SmallShape = RoundedCornerShape(SmallCorner)
val MediumShape = RoundedCornerShape(MediumCorner)
val LargeShape = RoundedCornerShape(LargeCorner)
val XLargeShape = RoundedCornerShape(XLargeCorner)
val FullShape = RoundedCornerShape(FullCorner)

// Material 3 形状规范
val Shapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),   // Chip、Tooltip
    small = RoundedCornerShape(8.dp),         // Button、小型组件
    medium = RoundedCornerShape(12.dp),       // Dialog、中型组件
    large = RoundedCornerShape(16.dp),        // Card、大组件
    extraLarge = RoundedCornerShape(24.dp)    // 最顶层容器
)

// ========== 应用特定形状 ==========

val CardShape = RoundedCornerShape(16.dp)                    // 录音卡片
val SmallCardShape = RoundedCornerShape(12.dp)               // 小卡片（健康面板）
val BottomSheetShape = RoundedCornerShape(24.dp, 24.dp, 0.dp, 0.dp) // 底部弹出
val TextFieldShape = RoundedCornerShape(12.dp)               // 搜索框、输入框
val ButtonShape = RoundedCornerShape(12.dp)                  // 按钮
val FABShape = RoundedCornerShape(20.dp)                     // FAB 悬浮按钮
val ChipShape = RoundedCornerShape(20.dp)                    // 胶囊标签
val BadgeShape = RoundedCornerShape(6.dp)                    // 状态徽章
val NavIndicatorShape = RoundedCornerShape(16.dp)            // 导航指示器
val StatusChipShape = RoundedCornerShape(6.dp)               // 状态小标签
