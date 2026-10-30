package com.leapauto.app.ui.theme

import androidx.compose.ui.graphics.Color

// 中性界面色板；绿色和红色仅表达车辆状态。
val PrimaryLight = Color(0xFF303030)
val SecondaryLight = Color(0xFF505050)
val EnergyGreen = Color(0xFF00C853)
val AlertRed = Color(0xFFFF3B30)
val TertiaryLight = Color(0xFF606060)

// 亮色 Token
val OnPrimaryLight = Color(0xFFFFFFFF)
val PrimaryContainerLight = Color(0xFFE5E5E5)
val OnPrimaryContainerLight = Color(0xFF202020)
val SecondaryContainerLight = Color(0xFFE9E9E9)
val OnSecondaryContainerLight = Color(0xFF303030)
val TertiaryContainerLight = Color(0xFFECECEC)
val OnTertiaryContainerLight = Color(0xFF404040)
val SurfaceLight = Color(0xFFFFFFFF)
val OnSurfaceLight = Color(0xFF181818)
val SurfaceVariantLight = Color(0xFFF1F1F1)
val OnSurfaceVariantLight = SecondaryLight
val OutlineLight = Color(0xFF787878)
val OutlineVariantLight = Color(0xFFDEDEDE)
val ErrorLight = AlertRed
val ErrorContainerLight = Color(0xFFFFDAD6)
val OnErrorContainerLight = Color(0xFF410002)
val AppBackgroundLight = Color(0xFFE8E8E8)
val GlassSurfaceLight = Color(0xFFFFFFFF)
val GlassInsetSurfaceLight = Color(0xFFF6F6F6)

// 暗色 Token（M3 Dark 自动映射，禁止手动反转颜色）
val PrimaryDark = Color(0xFFE4E4E4)
val OnPrimaryDark = Color(0xFF202020)
val PrimaryContainerDark = Color(0xFF353535)
val OnPrimaryContainerDark = Color(0xFFECECEC)
val SecondaryDark = Color(0xFFC2C2C2)
val OnSecondaryDark = Color(0xFF252525)
val SecondaryContainerDark = Color(0xFF3D3D3D)
val OnSecondaryContainerDark = Color(0xFFE5E5E5)
val TertiaryDark = Color(0xFFB6B6B6)
val OnTertiaryDark = Color(0xFF252525)
val TertiaryContainerDark = Color(0xFF454545)
val OnTertiaryContainerDark = Color(0xFFE8E8E8)
val SurfaceDark = Color(0xFF161616)
val OnSurfaceDark = Color(0xFFE9E9E9)
val SurfaceVariantDark = Color(0xFF303030)
val OnSurfaceVariantDark = Color(0xFFB5B5B5)
val OutlineDark = Color(0xFF969696)
val OutlineVariantDark = Color(0xFF424242)
val ErrorDark = Color(0xFFFFB4AB)
val OnErrorDark = Color(0xFF690005)
val ErrorContainerDark = Color(0xFF93000A)
val OnErrorContainerDark = Color(0xFFFFDAD6)
val AppBackgroundDark = Color(0xFF111111)
val GlassSurfaceDark = Color(0xFF202020)
val GlassInsetSurfaceDark = Color(0xFF303030)

// 语义状态色（配合文字使用，不单独传达信息）
val StatusGoodLight = EnergyGreen
val StatusGoodDark = Color(0xFF4ADE80)
val StatusWarnLight = Color(0xFFFF9500)
val StatusWarnDark = Color(0xFFFFB74D)
val StatusBadLight = AlertRed
val StatusBadDark = Color(0xFFFF8A80)
