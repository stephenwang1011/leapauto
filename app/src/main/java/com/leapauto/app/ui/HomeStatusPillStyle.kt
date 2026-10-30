package com.leapauto.app.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal object HomeStatusPillStyle {
    val fontSize = 12.sp
    val lineHeight = 16.sp
    val horizontalPadding = 9.dp
    val verticalPadding = 3.dp
    val iconSize = 12.dp
    val dotSize = 6.dp
}

internal fun homeStatusPillBackground(isHeroDark: Boolean): Color =
    if (isHeroDark) Color(0xFF303030).copy(alpha = 0.68f)
    else Color(0xFFE1E1E1).copy(alpha = 0.65f)
