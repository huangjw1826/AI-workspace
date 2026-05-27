package com.airecorder.android.ui.theme

import androidx.compose.ui.graphics.Color

// ========== Material 3 完整颜色系统 ==========

// ---------- 主色调 (Primary) ----------
val Primary = Color(0xFF5B67F1) // 现代紫蓝色 - 品牌色
val OnPrimary = Color(0xFFFFFFFF)
val PrimaryContainer = Color(0xFFEEF0FF)
val OnPrimaryContainer = Color(0xFF1A1E69)

// ---------- 次色调 (Secondary) ----------
val Secondary = Color(0xFF6366F1)
val OnSecondary = Color(0xFFFFFFFF)
val SecondaryContainer = Color(0xFFF0F2FF)
val OnSecondaryContainer = Color(0xFF1A1B6A)

// ---------- 第三色调 (Tertiary) ----------
val Tertiary = Color(0xFF06B6D4) // 青色 - 用于转写
val OnTertiary = Color(0xFFFFFFFF)
val TertiaryContainer = Color(0xFFCFFAFE)
val OnTertiaryContainer = Color(0xFF005060)

// ---------- 错误色 (Error) ----------
val Error = Color(0xFFEF4444)
val OnError = Color(0xFFFFFFFF)
val ErrorContainer = Color(0xFFFEE2E2)
val OnErrorContainer = Color(0xFF5D0E0E)

// ---------- 背景和表面色 ----------
val Background = Color(0xFFF7F9FC)
val OnBackground = Color(0xFF1F2937)
val Surface = Color(0xFFFFFFFF)
val OnSurface = Color(0xFF1F2937)
val SurfaceVariant = Color(0xFFF3F4F6)
val OnSurfaceVariant = Color(0xFF6B7280)

// ---------- 轮廓色 ----------
val Outline = Color(0xFFE5E7EB)
val OutlineVariant = Color(0xFFD1D5DB)

// ---------- 状态颜色 ----------
val StatusSuccess = Color(0xFF10B981)
val StatusSuccessLight = Color(0xFFD1FAE5)
val StatusInfo = Tertiary
val StatusInfoLight = TertiaryContainer
val StatusWarning = Color(0xFFF59E0B)
val StatusWarningLight = Color(0xFFFEF3C7)
val StatusError = Error
val StatusErrorLight = ErrorContainer

// ---------- 文本颜色 ----------
val TextPrimary = OnSurface
val TextSecondary = OnSurfaceVariant
val TextTertiary = Color(0xFF9CA3AF)
val TextPlaceholder = OutlineVariant

// ---------- 分隔线颜色 ----------
val Divider = Outline
val DividerLight = SurfaceVariant

// ---------- 强调色 (Accent Colors) ----------
val AccentOrange = Color(0xFFFF8A65)
val AccentYellow = Color(0xFFFFD54F)
val AccentGreen = Color(0xFF81C784)
val AccentBlue = Color(0xFF64B5F6)
val AccentPurple = Color(0xFF8B5CF6) // 用于摘要

// ---------- 功能特定颜色 ----------
val TranscribeStatus = Tertiary
val SummarizeStatus = AccentPurple

val HealthGreen = StatusSuccess
val HealthOrange = StatusWarning
val HealthRed = StatusError

// ---------- 深色主题颜色 ----------
val DarkBackground = Color(0xFF0F1117)
val DarkSurface = Color(0xFF1A1D29)
val DarkSurfaceVariant = Color(0xFF252A3A)
val DarkOnSurface = Color(0xFFE5E7EB)
val DarkOnSurfaceVariant = Color(0xFF9CA3AF)
val DarkPrimary = Color(0xFF9DA5FF)
val DarkPrimaryContainer = Color(0xFF3A43C5)
val DarkOutline = Color(0xFF374151)
