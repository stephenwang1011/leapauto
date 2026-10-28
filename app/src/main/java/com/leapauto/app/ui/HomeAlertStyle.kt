package com.leapauto.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.leapauto.app.ui.theme.AlertRed
import com.leapauto.app.ui.theme.LocalAppDarkTheme
import com.leapauto.app.ui.theme.StatusBadDark

internal object HomeAlertStyle {
    // The lighter red keeps small text readable on the brighter graphite cards.
    fun contentColor(isDark: Boolean, fallback: Color, onHero: Boolean = false): Color = when {
        !isDark -> fallback
        onHero -> AlertRed
        else -> StatusBadDark
    }
}

@Composable
internal fun homeAlertContentColor(onHero: Boolean = false): Color = HomeAlertStyle.contentColor(
    isDark = LocalAppDarkTheme.current,
    fallback = MaterialTheme.colorScheme.error,
    onHero = onHero
)
