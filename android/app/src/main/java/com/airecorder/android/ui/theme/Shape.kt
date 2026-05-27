package com.airecorder.android.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

// ========== Material 3 形状系统 ==========

// 圆角尺寸定义
val ExtraSmallCornerSize = 4.dp
val SmallCornerSize = 8.dp
val MediumCornerSize = 12.dp
val LargeCornerSize = 16.dp
val ExtraLargeCornerSize = 24.dp
val FullCornerSize = 50.dp

// 常用圆角形状
val ExtraSmallShape = RoundedCornerShape(ExtraSmallCornerSize)
val SmallShape = RoundedCornerShape(SmallCornerSize)
val MediumShape = RoundedCornerShape(MediumCornerSize)
val LargeShape = RoundedCornerShape(LargeCornerSize)
val ExtraLargeShape = RoundedCornerShape(ExtraLargeCornerSize)
val FullShape = RoundedCornerShape(FullCornerSize)

// Material 3 官方形状规范
val Shapes = Shapes(
    extraSmall = ExtraSmallShape,      // 用于 Chip、Tooltip 等小组件
    small = SmallShape,                // 用于 Button、TextField、Card 等
    medium = MediumShape,              // 用于 NavigationDrawer、Dialog 等
    large = LargeShape,                // 用于 BottomSheet、大 Card 等
    extraLarge = ExtraLargeShape       // 用于最顶层的卡片或容器
)

// 应用特定的形状
val CardShape = LargeShape
val BottomSheetShape = RoundedCornerShape(topStart = ExtraLargeCornerSize, topEnd = ExtraLargeCornerSize)
val TextFieldShape = MediumShape
val ButtonShape = MediumShape
val FABShape = MediumShape
val NavigationShape = SmallShape
val ChipShape = ExtraSmallShape
