package com.leapauto.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.leapauto.app.ui.theme.LocalAppDarkTheme

// Category accents belong only to settings header icons, not action or status colors.
internal enum class SettingsIconTone(private val light: Color, private val dark: Color) {
    THEME(Color(0xFF8055A6), Color(0xFFC8A4E8)),
    KEY(Color(0xFF9C690F), Color(0xFFE5BC70)),
    SUPPORT(Color(0xFFB54B73), Color(0xFFEEA0BE)),
    CODE(Color(0xFF26725B), Color(0xFF80C4A5)),
    OTA(Color(0xFF147B65), Color(0xFF77CCB6)),
    VERSION(Color(0xFF7D6596), Color(0xFFC5AFDC));

    fun color(isDark: Boolean): Color = if (isDark) dark else light
}

@Composable
internal fun settingsIconColor(tone: SettingsIconTone): Color =
    tone.color(LocalAppDarkTheme.current)
