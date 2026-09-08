package com.leapauto.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// 亮色：零跑品牌色板（leap-design.md §1，Leap Blue 而非通用 Primary）
private val LightColors = lightColorScheme(
    primary = LeapBlue,
    onPrimary = OnPrimaryLight,
    primaryContainer = PrimaryContainerLight,
    onPrimaryContainer = OnPrimaryContainerLight,
    secondary = TechGrey,
    onSecondary = OnPrimaryLight,
    secondaryContainer = SecondaryContainerLight,
    onSecondaryContainer = OnSecondaryContainerLight,
    tertiary = AccentBlue,
    onTertiary = OnPrimaryLight,
    tertiaryContainer = TertiaryContainerLight,
    onTertiaryContainer = OnTertiaryContainerLight,
    background = AppBackgroundLight,
    surface = SurfaceLight,
    onSurface = OnSurfaceLight,
    surfaceVariant = SurfaceVariantLight,
    onSurfaceVariant = OnSurfaceVariantLight,
    outline = OutlineLight,
    outlineVariant = OutlineVariantLight,
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF7F7F5),
    surfaceContainer = Color(0xFFF2F2EF),
    surfaceContainerHigh = Color(0xFFEDEDEA),
    surfaceContainerHighest = Color(0xFFE6E6E2),
    error = ErrorLight,
    onError = Color.White,
    errorContainer = ErrorContainerLight,
    onErrorContainer = OnErrorContainerLight
)

// 暗色：M3 Dark Token 自动映射（禁止手动反转颜色）
private val DarkColors = darkColorScheme(
    primary = PrimaryDark,
    onPrimary = OnPrimaryDark,
    primaryContainer = PrimaryContainerDark,
    onPrimaryContainer = OnPrimaryContainerDark,
    secondary = SecondaryDark,
    onSecondary = OnSecondaryDark,
    secondaryContainer = SecondaryContainerDark,
    onSecondaryContainer = OnSecondaryContainerDark,
    tertiary = TertiaryDark,
    onTertiary = OnTertiaryDark,
    tertiaryContainer = TertiaryContainerDark,
    onTertiaryContainer = OnTertiaryContainerDark,
    background = AppBackgroundDark,
    surface = SurfaceDark,
    onSurface = OnSurfaceDark,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = OnSurfaceVariantDark,
    outline = OutlineDark,
    outlineVariant = OutlineVariantDark,
    surfaceContainerLowest = Color(0xFF0E1116),
    surfaceContainerLow = Color(0xFF1A1E26),
    surfaceContainer = Color(0xFF20242D),
    surfaceContainerHigh = Color(0xFF2B303B),
    surfaceContainerHighest = Color(0xFF363C48),
    error = ErrorDark,
    onError = OnErrorDark,
    errorContainer = ErrorContainerDark,
    onErrorContainer = OnErrorContainerDark
)

// 语义状态色（始终与文字配对使用）
val LocalAppDarkTheme = staticCompositionLocalOf { false }

val MaterialTheme.statusGood: Color
    @Composable get() = if (LocalAppDarkTheme.current) StatusGoodDark else StatusGoodLight

val MaterialTheme.statusWarn: Color
    @Composable get() = if (LocalAppDarkTheme.current) StatusWarnDark else StatusWarnLight

val MaterialTheme.statusBad: Color
    @Composable get() = if (LocalAppDarkTheme.current) StatusBadDark else StatusBadLight

val MaterialTheme.glassSurface: Color
    @Composable get() = if (LocalAppDarkTheme.current) GlassSurfaceDark.copy(alpha = 0.80f) else GlassSurfaceLight.copy(alpha = 0.72f)

val MaterialTheme.glassInsetSurface: Color
    @Composable get() = if (LocalAppDarkTheme.current) GlassInsetSurfaceDark.copy(alpha = 0.68f) else GlassInsetSurfaceLight.copy(alpha = 0.65f)

@Composable
fun LeapAutoTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !darkTheme
            controller.isAppearanceLightNavigationBars = !darkTheme
        }
    }
    CompositionLocalProvider(LocalAppDarkTheme provides darkTheme) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            typography = AppTypography,
            content = content
        )
    }
}
