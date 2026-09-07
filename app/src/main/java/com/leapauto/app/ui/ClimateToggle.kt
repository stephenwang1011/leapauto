package com.leapauto.app.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.offset
import com.leapauto.app.ui.theme.LeapBlue

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
    val visualState = ClimateToggleVisualSpec.state(checked)
    val animationSpec = tween<Color>(
        durationMillis = ClimateToggleVisualSpec.ANIMATION_DURATION_MS,
        easing = FastOutSlowInEasing
    )
    val thumbOffset by animateDpAsState(
        targetValue = visualState.thumbOffsetDp.dp,
        animationSpec = tween(
            durationMillis = ClimateToggleVisualSpec.ANIMATION_DURATION_MS,
            easing = FastOutSlowInEasing
        ),
        label = "climateToggleThumbOffset"
    )
    // 轨道：开启使用零跑 Leap Blue，关闭和禁用状态保持中性弱化。
    val trackColor by animateColorAsState(
        targetValue = when {
            checked && enabled -> LeapBlue
            checked -> LeapBlue.copy(alpha = 0.38f)
            enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.20f)
        },
        animationSpec = animationSpec,
        label = "climateToggleTrackColor"
    )
    // 滑块：开启时使用白色，与蓝色轨道形成清晰对比。
    val thumbColor by animateColorAsState(
        targetValue = when {
            checked && enabled -> Color.White
            checked -> Color.White.copy(alpha = 0.70f)
            else -> Color.White.copy(alpha = if (enabled) 1f else 0.9f)
        },
        animationSpec = animationSpec,
        label = "climateToggleThumbColor"
    )

    Box(
        modifier = modifier
            .size(48.dp)
            .clip(CircleShape)
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = { onCheckedChange?.invoke(it) }
            )
            .semantics {
                this.contentDescription = contentDescription
                this.stateDescription = stateDescription
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(
                    width = ClimateToggleVisualSpec.TRACK_WIDTH_DP.dp,
                    height = ClimateToggleVisualSpec.TRACK_HEIGHT_DP.dp
                )
                .clip(RoundedCornerShape(7.dp))
                .background(trackColor)
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .offset { IntOffset(thumbOffset.roundToPx(), 0) }
                    .size(ClimateToggleVisualSpec.THUMB_SIZE_DP.dp)
                    .shadow(
                        elevation = 2.dp,
                        shape = CircleShape,
                        clip = false
                    )
                    .background(thumbColor, CircleShape)
            )
        }
    }
}
