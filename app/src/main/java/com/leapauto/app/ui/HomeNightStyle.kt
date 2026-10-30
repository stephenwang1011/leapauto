package com.leapauto.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Opted into only by the home information cards, never by the app theme or sheets. */
internal val LocalHomeNightCards = staticCompositionLocalOf { false }

internal object HomeNightStyle {
    const val auraScale = 0.4f
    val page = Color(0xFF1D1D1D)
    val cardStart = Color(0xFF373737).copy(alpha = 0.90f)
    val cardMiddle = Color(0xFF2E2E2E).copy(alpha = 0.86f)
    val cardEnd = Color(0xFF282828).copy(alpha = 0.90f)
    val borderStart = Color.White.copy(alpha = 0.20f)
    val borderMiddle = Color.White.copy(alpha = 0.09f)
    val borderEnd = Color.White.copy(alpha = 0.04f)
}

internal fun homeHeroTransitionPage(isDark: Boolean, lightPage: Color): Color =
    if (isDark) HomeNightStyle.page else lightPage

@Composable
internal fun BoxScope.HomeHeroTransition(heightDp: Int, isDark: Boolean) {
    val page = homeHeroTransitionPage(isDark, MaterialTheme.colorScheme.background)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(heightDp.dp)
            .align(Alignment.BottomCenter)
            .background(
                Brush.verticalGradient(
                    0.0f to Color.Transparent,
                    0.45f to page.copy(alpha = if (isDark) 0.25f else 0.35f),
                    0.80f to page.copy(alpha = if (isDark) 0.68f else 0.85f),
                    1.0f to page
                )
            )
    )
}
