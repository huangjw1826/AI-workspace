package com.airecorder.android.ui.theme

import androidx.compose.ui.graphics.Color

// ============================================================
// 「有机录音室」色彩体系 — 暖橄榄 + 琥珀
// ============================================================

// ---------- 主色调：暖橄榄绿 ----------
val Primary = Color(0xFF2D8C4E)               // 成熟稳重的主色
val OnPrimary = Color(0xFFFFFFFF)
val PrimaryContainer = Color(0xFFE8F2EB)       // 薄荷底
val OnPrimaryContainer = Color(0xFF0E3A1E)

// ---------- 次色调：柔和绿 ----------
val Secondary = Color(0xFF4A9E6B)
val OnSecondary = Color(0xFFFFFFFF)
val SecondaryContainer = Color(0xFFDCF5E3)
val OnSecondaryContainer = Color(0xFF163D24)

// ---------- 第三色调：柔和蓝（用于信息态）----------
val Tertiary = Color(0xFF5B9BD5)
val OnTertiary = Color(0xFFFFFFFF)
val TertiaryContainer = Color(0xFFE9F4FC)
val OnTertiaryContainer = Color(0xFF1A3D5C)

// ---------- 强调色：琥珀 ----------
val Accent = Color(0xFFE8A840)                 // 温暖、醒目 — FAB、重要按钮
val OnAccent = Color(0xFFFFFFFF)
val AccentContainer = Color(0xFFFFF3E0)
val OnAccentContainer = Color(0xFF5C3D0E)

// ---------- 错误色：暖红 ----------
val Error = Color(0xFFD9534F)
val OnError = Color(0xFFFFFFFF)
val ErrorContainer = Color(0xFFFDEAEA)
val OnErrorContainer = Color(0xFF5D0E0E)

// ---------- 背景和表面色 ----------
val Background = Color(0xFFFAF8F5)             // 奶油纸白
val OnBackground = Color(0xFF2C2416)           // 暖黑
val Surface = Color(0xFFFFFDF9)                // 暖白卡片
val OnSurface = Color(0xFF2C2416)
val SurfaceVariant = Color(0xFFF2EFE9)         // 暖灰底色
val OnSurfaceVariant = Color(0xFF6B6152)       // 暖灰文字

// ---------- 轮廓色 ----------
val Outline = Color(0xFFE8E3D8)               // 暖色分割线
val OutlineVariant = Color(0xFFD5D0C5)

// ---------- 状态颜色 ----------
val StatusSuccess = Color(0xFF3D9D5C)          // 柔和绿
val StatusSuccessLight = Color(0xFFE8F5ED)
val StatusInfo = Tertiary
val StatusInfoLight = TertiaryContainer
val StatusWarning = Color(0xFFE8A840)          // 琥珀（与强调色统一）
val StatusWarningLight = Color(0xFFFFF8EC)
val StatusError = Error
val StatusErrorLight = ErrorContainer

// ---------- 文本颜色 ----------
val TextPrimary = OnSurface                   // #2C2416 暖黑
val TextSecondary = OnSurfaceVariant          // #6B6152 暖灰
val TextTertiary = Color(0xFFA09888)          // 浅暖灰
val TextPlaceholder = Color(0xFFC5BDB2)

// ---------- 分隔线颜色 ----------
val Divider = Outline
val DividerLight = SurfaceVariant

// ---------- 功能特定颜色 ----------
val TranscribeStatus = Accent                  // 琥珀 — 转写态
val SummarizeStatus = Tertiary                 // 柔蓝 — 摘要态

val HealthGreen = StatusSuccess
val HealthOrange = StatusWarning
val HealthRed = StatusError

// ---------- 深色主题颜色 ----------
val DarkBackground = Color(0xFF1A1D19)         // 深橄榄黑
val DarkSurface = Color(0xFF242824)
val DarkSurfaceVariant = Color(0xFF303630)
val DarkOnSurface = Color(0xFFE8E3D8)
val DarkOnSurfaceVariant = Color(0xFFB5AEA0)
val DarkPrimary = Color(0xFF5BBF78)
val DarkPrimaryContainer = Color(0xFF1E4E2E)
val DarkOutline = Color(0xFF4A4D45)
val DarkAccent = Color(0xFFF0C060)
