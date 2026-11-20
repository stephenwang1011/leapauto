package com.leapauto.app.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
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

/**
 * 智能座舱级·真微晶开关
 * - 尺寸紧凑（38dp x 22dp，滑块 16dp），无需依赖失真模糊的 scale(0.78f)
 * - 关闭态（OFF）：深色模式采用银灰微晶滑块（#A0A0A0）与沉静内凹槽（#222222），内敛不突兀，彻底告别刺眼纯白大圆球
 * - 开启态（ON）：纯白滑块与能量生态绿（statusGood）饱满点亮
 */
@Composable
fun CockpitSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    stateDescription: String? = null,
    enabled: Boolean = onCheckedChange != null
) {
    val haptic = LocalHapticFeedback.current
    val isDark = LocalAppDarkTheme.current

    val trackWidth = 38.dp
    val trackHeight = 22.dp
    val thumbSize = 16.dp
    val padding = 3.dp

    val thumbOffset by animateDpAsState(
        targetValue = if (checked) trackWidth - thumbSize - padding else padding,
        animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
        label = "cockpitSwitchThumbOffset"
    )

    val trackColor by animateColorAsState(
        targetValue = when {
            !enabled -> if (checked) MaterialTheme.statusGood.copy(alpha = 0.4f) else (if (isDark) Color(0xFF1E1E1E) else Color(0xFFECECEC))
            checked -> MaterialTheme.statusGood
            else -> if (isDark) Color(0xFF222222) else Color(0xFFE5E5EA)
        },
        animationSpec = tween(durationMillis = 180),
        label = "cockpitSwitchTrackColor"
    )

    val trackBorderColor by animateColorAsState(
        targetValue = when {
            checked -> Color.Transparent
            else -> if (isDark) Color(0x33FFFFFF) else Color(0x1F000000)
        },
        animationSpec = tween(durationMillis = 180),
        label = "cockpitSwitchBorderColor"
    )

    val thumbColor by animateColorAsState(
        targetValue = when {
            !enabled -> if (checked) Color.White.copy(alpha = 0.7f) else Color(0xFF757575)
            checked -> Color.White
            else -> if (isDark) Color(0xFFA0A0A0) else Color.White
        },
        animationSpec = tween(durationMillis = 180),
        label = "cockpitSwitchThumbColor"
    )

    Box(
        modifier = modifier
            .size(trackWidth, trackHeight)
            .clip(RoundedCornerShape(percent = 50))
            .background(trackColor)
            .border(0.6.dp, trackBorderColor, RoundedCornerShape(percent = 50))
            .clickable(
                enabled = enabled,
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) {
                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                onCheckedChange?.invoke(!checked)
            }
            .semantics {
                this.role = Role.Switch
                contentDescription?.let { this.contentDescription = it }
                stateDescription?.let { this.stateDescription = it }
            },
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .offset(x = thumbOffset)
                .size(thumbSize)
                .clip(CircleShape)
                .background(thumbColor)
        )
    }
}

@Composable
fun ClimateToggle(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    contentDescription: String,
    stateDescription: String,
    modifier: Modifier = Modifier
) {
    CockpitSwitch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        contentDescription = contentDescription,
        stateDescription = stateDescription,
        modifier = modifier
    )
}
