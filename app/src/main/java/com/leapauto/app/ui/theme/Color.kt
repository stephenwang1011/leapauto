package com.leapauto.app.ui.theme

import androidx.compose.ui.graphics.Color

// 零跑品牌色板（leap-design.md §1）：
// Leap Blue 主色 / Tech Grey 辅助 / Energy Green 强调 / Alert Red 警示
val LeapBlue = Color(0xFF0066FF)
val TechGrey = Color(0xFF2D3748)
val EnergyGreen = Color(0xFF00C853)
val AlertRed = Color(0xFFFF3B30)
val AccentBlue = Color(0xFF00A6FF)

// 亮色 Token
val OnPrimaryLight = Color(0xFFFFFFFF)
val PrimaryContainerLight = Color(0xFFE3EEFF)
val OnPrimaryContainerLight = Color(0xFF00398F)
val SecondaryContainerLight = Color(0xFFE8EDF3)
val OnSecondaryContainerLight = Color(0xFF1A2534)
val TertiaryContainerLight = Color(0xFFD6F1FF)
val OnTertiaryContainerLight = Color(0xFF00374F)
val SurfaceLight = Color(0xFFFFFFFF)
val OnSurfaceLight = Color(0xFF101828)
val SurfaceVariantLight = Color(0xFFEFF3F8)
val OnSurfaceVariantLight = TechGrey
val OutlineLight = Color(0xFF8A94A6)
val OutlineVariantLight = Color(0xFFDDE3EC)
val ErrorLight = AlertRed
val ErrorContainerLight = Color(0xFFFFDAD6)
val OnErrorContainerLight = Color(0xFF410002)
val AppBackgroundLight = Color(0xFFE6E8E4)
val GlassSurfaceLight = Color(0xFFFFFFFF)
val GlassInsetSurfaceLight = Color(0xFFF5F7F8)

// 暗色 Token（M3 Dark 自动映射，禁止手动反转颜色）
val PrimaryDark = Color(0xFF7FB2FF)
val OnPrimaryDark = Color(0xFF003C9E)
val PrimaryContainerDark = Color(0xFF0052CC)
val OnPrimaryContainerDark = Color(0xFFDCE9FF)
val SecondaryDark = Color(0xFFA9B4C3)
val OnSecondaryDark = Color(0xFF1F2A38)
val SecondaryContainerDark = Color(0xFF374052)
val OnSecondaryContainerDark = Color(0xFFD9E2EC)
val TertiaryDark = Color(0xFF6FD6FF)
val OnTertiaryDark = Color(0xFF00344A)
val TertiaryContainerDark = Color(0xFF004C6B)
val OnTertiaryContainerDark = Color(0xFFD6F1FF)
val SurfaceDark = Color(0xFF12151C)
val OnSurfaceDark = Color(0xFFE6EAF2)
val SurfaceVariantDark = Color(0xFF2A303B)
val OnSurfaceVariantDark = Color(0xFFA3ADBB)
val OutlineDark = Color(0xFF8A94A6)
val OutlineVariantDark = Color(0xFF3A4150)
val ErrorDark = Color(0xFFFFB4AB)
val OnErrorDark = Color(0xFF690005)
val ErrorContainerDark = Color(0xFF93000A)
val OnErrorContainerDark = Color(0xFFFFDAD6)
val AppBackgroundDark = Color(0xFF0E1117)
val GlassSurfaceDark = Color(0xFF1A1F28)
val GlassInsetSurfaceDark = Color(0xFF28303B)

// 语义状态色（配合文字使用，不单独传达信息）
val StatusGoodLight = EnergyGreen
val StatusGoodDark = Color(0xFF4ADE80)
val StatusWarnLight = Color(0xFFFF9500)
val StatusWarnDark = Color(0xFFFFB74D)
val StatusBadLight = AlertRed
val StatusBadDark = Color(0xFFFF8A80)
