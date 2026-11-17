package com.leapauto.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import com.leapauto.app.ui.theme.LocalAppDarkTheme
import com.leapauto.app.ui.theme.statusGood

enum class ClimateToggleGlyph {
    CHECK,
    CROSS
}

data class ClimateToggleVisualState(
    val thumbOffsetDp: Int,
    val glyph: ClimateToggleGlyph
)

object ClimateToggleVisualSpec {
    const val TRACK_WIDTH_DP = 36
    const val TRACK_HEIGHT_DP = 14
    const val THUMB_SIZE_DP = 20
    const val TRACK_HORIZONTAL_PADDING_DP = 0
    const val ANIMATION_DURATION_MS = 200

    fun state(checked: Boolean): ClimateToggleVisualState = ClimateToggleVisualState(
        thumbOffsetDp = if (checked) {
            TRACK_WIDTH_DP - TRACK_HORIZONTAL_PADDING_DP - THUMB_SIZE_DP
        } else {
            TRACK_HORIZONTAL_PADDING_DP
        },
        glyph = if (checked) ClimateToggleGlyph.CHECK else ClimateToggleGlyph.CROSS
    )
}

object ClimateIconRotationSpec {
    const val ROTATION_DURATION_MS = 3000
    const val START_DEGREES = 0f
    const val END_DEGREES = 360f

    fun rotationAngle(isAcRunning: Boolean, animatedDegrees: Float): Float =
        if (isAcRunning) animatedDegrees else 0f
}

@Composable
fun ClimateToggle(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    contentDescription: String,
    stateDescription: String,
    modifier: Modifier = Modifier
) {
    val enabled = onCheckedChange != null
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    val isDark = LocalAppDarkTheme.current
    val uncheckedTrack = if (isDark) Color(0xFF383838) else Color(0xFFDFDFDF)
    val uncheckedBorder = if (isDark) Color(0x26FFFFFF) else Color(0x1A000000)
    val colors = SwitchDefaults.colors(
        checkedThumbColor = Color.White,
        checkedTrackColor = MaterialTheme.statusGood,
        checkedBorderColor = Color.Transparent,
        uncheckedThumbColor = Color.White,
        uncheckedTrackColor = uncheckedTrack,
        uncheckedBorderColor = uncheckedBorder,
        disabledCheckedThumbColor = Color.White.copy(alpha = 0.7f),
        disabledCheckedTrackColor = MaterialTheme.statusGood.copy(alpha = 0.5f),
        disabledUncheckedThumbColor = Color.White.copy(alpha = 0.7f),
        disabledUncheckedTrackColor = uncheckedTrack.copy(alpha = 0.5f)
    )

    Switch(
        checked = checked,
        onCheckedChange = if (enabled) {
            { target ->
                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                onCheckedChange?.invoke(target)
            }
        } else null,
        enabled = enabled,
        colors = colors,
        modifier = modifier
            .scale(0.78f)
            .semantics {
                this.contentDescription = contentDescription
                this.stateDescription = stateDescription
            }
    )
}
