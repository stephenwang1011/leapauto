package com.leapauto.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.leapauto.app.Commands
import com.leapauto.app.FridgeControlCommand
import com.leapauto.app.FridgeMode
import com.leapauto.app.FridgeStatus
import com.leapauto.app.FridgeStyle
import com.leapauto.app.R
import com.leapauto.app.ui.theme.statusGood
import com.leapauto.app.ui.theme.statusWarn
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 首页车载冰箱微晶毛玻璃概览卡片。
 * 遵循智能座舱真微晶毛玻璃设计规范，支持紧凑双子舱（60dp）与全宽独立（56dp）自适应模式。
 */
@Composable
fun FridgeOverviewCard(
    fridgeStatus: FridgeStatus?,
    onOpenFridgeControl: () -> Unit,
    onToggleFridge: (Boolean) -> Unit,
    controlBusy: Boolean = false,
    compact: Boolean = false,
    modifier: Modifier = Modifier,
    seamless: Boolean = false
) {
    if (fridgeStatus == null) return

    val isEnabled = fridgeStatus.enabled
    val isHot = fridgeStatus.mode == FridgeMode.HOT
    val isParkRunning = fridgeStatus.isParkRunning

    val activeThemeColor = if (isHot) MaterialTheme.statusWarn else MaterialTheme.colorScheme.primary
    val displayColor = if (isEnabled) activeThemeColor else MaterialTheme.colorScheme.onSurfaceVariant

    val statusText = when {
        !isEnabled -> "车载冰箱 · 已关闭"
        isHot -> "制热中 · 50 °C"
        fridgeStatus.style == FridgeStyle.TURBO -> "急速制冷 · ${fridgeStatus.targetTemp} °C"
        else -> "制冷中 · ${fridgeStatus.targetTemp} °C"
    }

    val ambientBrush = if (isEnabled) {
        Brush.horizontalGradient(
            listOf(
                activeThemeColor.copy(alpha = 0.08f),
                Color.Transparent
            )
        )
    } else null

    Surface(
        shape = if (!seamless) RoundedCornerShape(16.dp) else androidx.compose.ui.graphics.RectangleShape,
        color = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = if (!seamless && isEnabled) {
            BorderStroke(1.dp, activeThemeColor.copy(alpha = 0.45f))
        } else if (!seamless) {
            glassCardBorder()
        } else null,
        shadowElevation = 0.dp,
        modifier = modifier
            .height(if (compact) 60.dp else 56.dp)
            .then(
                if (!seamless) Modifier.frostedGlassCard(
                    shape = RoundedCornerShape(16.dp),
                    auraColor = if (isEnabled) activeThemeColor.copy(alpha = 0.16f) else null,
                    auraCenter = Offset(0.06f, 0.5f),
                    auraRadiusRatio = 0.5f
                ) else Modifier
            )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(if (ambientBrush != null) Modifier.background(ambientBrush) else Modifier)
        ) {
            if (compact) {
                // 并排紧凑双子舱布局：带触控热区物理微晶隔断
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(start = 6.dp, end = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable(onClick = onOpenFridgeControl),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_phosphor_fridge),
                            contentDescription = "车载冰箱",
                            modifier = Modifier.size(20.dp),
                            tint = displayColor
                        )
                        Spacer(Modifier.width(8.dp))
                        Column(verticalArrangement = Arrangement.Center) {
                            Text(
                                if (isEnabled) {
                                    if (isHot) "冰箱 · 50°C" else "冰箱 · ${fridgeStatus.targetTemp}°C"
                                } else "车载冰箱",
                                style = MaterialTheme.typography.labelMedium.energyStyle(),
                                fontWeight = FontWeight.Bold,
                                color = if (isEnabled) activeThemeColor else MaterialTheme.colorScheme.onSurface,
                                maxLines = 1
                            )
                            Spacer(Modifier.height(1.dp))
                            Text(
                                when {
                                    !isEnabled -> "已关闭"
                                    isParkRunning -> "离车保持中"
                                    isHot -> "恒温暖饮"
                                    fridgeStatus.style == FridgeStyle.TURBO -> "急速制冷"
                                    else -> "智能制冷"
                                },
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                fontWeight = if (isParkRunning) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (isParkRunning) MaterialTheme.statusGood else MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }
                    }

                    Spacer(Modifier.width(4.dp))

                    // 右侧开关微晶安全热区
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                            .padding(horizontal = 4.dp, vertical = 2.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (controlBusy) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = activeThemeColor
                            )
                        } else {
                            ClimateToggle(
                                checked = isEnabled,
                                onCheckedChange = { onToggleFridge(!isEnabled) },
                                contentDescription = "车载冰箱开关",
                                stateDescription = if (isEnabled) "已开启" else "已关闭"
                            )
                        }
                    }
                }
            } else {
                // 原有全宽独立布局
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(start = 12.dp, end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 左侧可点击主体区
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(16.dp))
                            .clickable(onClick = onOpenFridgeControl),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_phosphor_fridge),
                            contentDescription = "车载冰箱",
                            modifier = Modifier.size(22.dp),
                            tint = displayColor
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            statusText,
                            style = MaterialTheme.typography.labelLarge.energyStyle(),
                            fontWeight = if (isEnabled) FontWeight.Bold else FontWeight.Medium,
                            color = if (isEnabled) activeThemeColor else MaterialTheme.colorScheme.onSurface
                        )
                        if (isParkRunning) {
                            Spacer(Modifier.width(8.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(MaterialTheme.statusGood.copy(alpha = 0.16f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    "离车运行中",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.statusGood
                                )
                            }
                        }
                    }

                    // 右侧状态与开关
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            if (isEnabled) "ON" else "OFF",
                            style = MaterialTheme.typography.labelMedium.energyStyle(),
                            fontWeight = FontWeight.SemiBold,
                            color = if (isEnabled) activeThemeColor else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (controlBusy) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = activeThemeColor
                            )
                        } else {
                            ClimateToggle(
                                checked = isEnabled,
                                onCheckedChange = { onToggleFridge(!isEnabled) },
                                contentDescription = "车载冰箱开关",
                                stateDescription = if (isEnabled) "已开启" else "已关闭"
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 车载冰箱控制抽屉面板（ModalBottomSheet）。
 * 遵循纯实色承载规范（containerColor = surface），融合式温控主盘与微晶毛玻璃结构。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FridgeControlBottomSheet(
    onDismissRequest: () -> Unit,
    fridgeStatus: FridgeStatus?,
    onApplyFridgeControl: (FridgeControlCommand) -> Unit,
    busy: Boolean = false
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val haptic = LocalHapticFeedback.current

    // 本地编辑状态
    var currentMode by remember(fridgeStatus) {
        mutableStateOf(fridgeStatus?.mode ?: FridgeMode.COLD)
    }
    var currentTemp by remember(fridgeStatus) {
        mutableIntStateOf(
            if (fridgeStatus?.mode == FridgeMode.HOT) Commands.FRIDGE_HOT_TEMP
            else (fridgeStatus?.targetTemp ?: Commands.FRIDGE_DEFAULT_TEMP).coerceIn(-6, 15)
        )
    }
    var currentStyle by remember(fridgeStatus) {
        mutableStateOf(fridgeStatus?.style ?: FridgeStyle.NORMAL)
    }
    var parkEnable by remember(fridgeStatus) {
        mutableStateOf(fridgeStatus?.parkEnable ?: false)
    }
    var parkDurationHours by remember(fridgeStatus) {
        mutableIntStateOf(fridgeStatus?.parkDurationHours?.coerceAtLeast(1) ?: 1)
    }
    var parkCycles by remember(fridgeStatus) {
        mutableIntStateOf(fridgeStatus?.parkCycles ?: 0)
    }

    val isHot = currentMode == FridgeMode.HOT
    val activeColor = if (isHot) MaterialTheme.statusWarn else MaterialTheme.colorScheme.primary

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. 顶部标题栏
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        painter = painterResource(R.drawable.ic_phosphor_fridge),
                        contentDescription = null,
                        tint = activeColor,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "智能车载冰箱",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                IconButton(
                    onClick = onDismissRequest,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_phosphor_x),
                        contentDescription = "关闭",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // 2. 融合式温控主盘微晶卡片（大字 + 两侧步进触控 + 常用预设胶囊）
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color.Transparent,
                border = BorderStroke(1.dp, activeColor.copy(alpha = 0.35f)),
                shadowElevation = 0.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .frostedGlassCard(
                        shape = RoundedCornerShape(20.dp),
                        auraColor = activeColor.copy(alpha = 0.18f),
                        auraCenter = Offset(0.5f, 0.4f),
                        auraRadiusRatio = 0.6f
                    )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 18.dp, horizontal = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // 温度主展示区：左右分布步进触控
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // 降温步进按钮（制冷模式下激活）
                        if (!isHot) {
                            IconButton(
                                onClick = {
                                    if (currentTemp > -6) {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        currentTemp--
                                    }
                                },
                                enabled = currentTemp > -6,
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                    .border(1.dp, activeColor.copy(alpha = 0.25f), CircleShape)
                            ) {
                                Text(
                                    "-",
                                    fontSize = 26.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (currentTemp > -6) activeColor else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                )
                            }
                        } else {
                            Spacer(Modifier.size(46.dp))
                        }

                        // 中央温度大字
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Row(
                                verticalAlignment = Alignment.Bottom,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = if (isHot) "50" else "$currentTemp",
                                    style = MaterialTheme.typography.displayLarge.energyStyle(),
                                    fontSize = 48.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = activeColor
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = "°C",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = activeColor,
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )
                            }
                            Text(
                                text = if (isHot) "恒温暖饮 · 50°C 保温" else "智能制冷 · 纯鲜冷藏",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // 升温步进按钮（制冷模式下激活）
                        if (!isHot) {
                            IconButton(
                                onClick = {
                                    if (currentTemp < 15) {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        currentTemp++
                                    }
                                },
                                enabled = currentTemp < 15,
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                    .border(1.dp, activeColor.copy(alpha = 0.25f), CircleShape)
                            ) {
                                Text(
                                    "+",
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (currentTemp < 15) activeColor else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                )
                            }
                        } else {
                            Spacer(Modifier.size(46.dp))
                        }
                    }

                    // 常用温度预设胶囊（制冷模式紧随温控下方）
                    AnimatedVisibility(
                        visible = !isHot,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        Column {
                            Spacer(Modifier.height(16.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                listOf(
                                    0 to "冰镇 0°C",
                                    4 to "冷饮 4°C",
                                    8 to "果蔬 8°C",
                                    12 to "保鲜 12°C"
                               ).forEach { (t, label) ->
                                    val isSelected = currentTemp == t
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(
                                                if (isSelected) activeColor.copy(alpha = 0.18f)
                                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                                            )
                                            .border(
                                                width = 1.dp,
                                                color = if (isSelected) activeColor else Color.Transparent,
                                                shape = RoundedCornerShape(8.dp)
                                            )
                                            .clickable {
                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                currentTemp = t
                                            }
                                            .padding(vertical = 7.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            label,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            color = if (isSelected) activeColor else MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    // 辅助状态标签组（运行风格 + 离车倒计时）
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                if (currentStyle == FridgeStyle.TURBO) "急速模式" else "标准节能",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        if (fridgeStatus?.isParkRunning == true && fridgeStatus.parkEndTimeEpochSeconds > 0) {
                            val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
                            val endFormatted = timeFormat.format(Date(fridgeStatus.parkEndTimeEpochSeconds * 1000L))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.statusGood.copy(alpha = 0.16f))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    "离车保持至 $endFormatted",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.statusGood
                                )
                            }
                        }
                    }
                }
            }

            // 3. 工作模式选择（制冷 vs 制热）
            Text(
                "工作模式",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                FridgeSegmentButton(
                    selected = !isHot,
                    iconRes = R.drawable.ic_phosphor_snowflake,
                    label = "智能制冷",
                    activeColor = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        currentMode = FridgeMode.COLD
                        currentTemp = 4
                    }
                )
                FridgeSegmentButton(
                    selected = isHot,
                    iconRes = R.drawable.ic_phosphor_sun,
                    label = "恒温暖饮 (50°C)",
                    activeColor = MaterialTheme.statusWarn,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        currentMode = FridgeMode.HOT
                        currentTemp = Commands.FRIDGE_HOT_TEMP
                    }
                )
            }

            // 4. 运行模式风格切换（标准 vs 急速）
            Text(
                "运行模式",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                FridgeSegmentButton(
                    selected = currentStyle == FridgeStyle.NORMAL,
                    iconRes = R.drawable.ic_phosphor_wind,
                    label = "标准节能",
                    activeColor = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        currentStyle = FridgeStyle.NORMAL
                    }
                )
                FridgeSegmentButton(
                    selected = currentStyle == FridgeStyle.TURBO,
                    iconRes = R.drawable.ic_phosphor_fan,
                    label = "急速温控",
                    activeColor = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        currentStyle = FridgeStyle.TURBO
                    }
                )
            }

            // 5. 离车持续运行卡片
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color.Transparent,
                border = glassCardBorder(),
                shadowElevation = 0.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .frostedGlassCard(shape = RoundedCornerShape(16.dp))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                "离车持续运行",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                "车辆熄火下电后，冰箱继续保持工作",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = parkEnable,
                            onCheckedChange = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                parkEnable = it
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = MaterialTheme.statusGood
                            )
                        )
                    }

                    AnimatedVisibility(
                        visible = parkEnable,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                "保持运行时长",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            // 常用 4 档时长自适应等分
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                listOf(1, 2, 4, 8).forEach { hours ->
                                    val isSelected = parkDurationHours == hours
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(
                                                if (isSelected) MaterialTheme.statusGood.copy(alpha = 0.16f)
                                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                            )
                                            .border(
                                                width = 1.dp,
                                                color = if (isSelected) MaterialTheme.statusGood else Color.Transparent,
                                                shape = RoundedCornerShape(8.dp)
                                            )
                                            .clickable {
                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                parkDurationHours = hours
                                            }
                                            .padding(vertical = 8.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            "${hours}小时",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            color = if (isSelected) MaterialTheme.statusGood else MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }

                            Spacer(Modifier.height(4.dp))

                            Text(
                                "运行频次",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(
                                            if (parkCycles == 0) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                        )
                                        .border(
                                            width = 1.dp,
                                            color = if (parkCycles == 0) MaterialTheme.colorScheme.primary else Color.Transparent,
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                        .clickable {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            parkCycles = 0
                                        }
                                        .padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        "仅单次生效",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = if (parkCycles == 0) FontWeight.Bold else FontWeight.Medium,
                                        color = if (parkCycles == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                    )
                                }

                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(
                                            if (parkCycles == 1) MaterialTheme.statusGood.copy(alpha = 0.16f)
                                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                        )
                                        .border(
                                            width = 1.dp,
                                            color = if (parkCycles == 1) MaterialTheme.statusGood else Color.Transparent,
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                        .clickable {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            parkCycles = 1
                                        }
                                        .padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        "每次离车自动开启",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = if (parkCycles == 1) FontWeight.Bold else FontWeight.Medium,
                                        color = if (parkCycles == 1) MaterialTheme.statusGood else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 6. 底部操作按钮栏
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp, bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (fridgeStatus?.enabled == true) {
                    Button(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onApplyFridgeControl(
                                FridgeControlCommand(
                                    enable = false,
                                    mode = currentMode,
                                    temp = currentTemp,
                                    style = currentStyle,
                                    parkEnable = false
                                )
                            )
                            onDismissRequest()
                        },
                        enabled = !busy,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f),
                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(0.4f)
                            .height(48.dp)
                    ) {
                        Text("关闭冰箱", fontWeight = FontWeight.SemiBold)
                    }
                }

                Button(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onApplyFridgeControl(
                            FridgeControlCommand(
                                enable = true,
                                mode = currentMode,
                                temp = currentTemp,
                                style = currentStyle,
                                parkEnable = parkEnable,
                                durationSeconds = parkDurationHours * 3600,
                                cycles = if (parkCycles == 1) "2" else "1"
                            )
                        )
                        onDismissRequest()
                    },
                    enabled = !busy,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(if (fridgeStatus?.enabled == true) 0.6f else 1f)
                        .height(48.dp)
                ) {
                    if (busy) {
                        CircularProgressIndicator(
                            color = Color.White,
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(
                            if (fridgeStatus?.enabled == true) "应用设置" else "开启车载冰箱",
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

/**
 * 分段选择微晶按钮
 */
@Composable
private fun FridgeSegmentButton(
    selected: Boolean,
    iconRes: Int,
    label: String,
    activeColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .height(46.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (selected) activeColor.copy(alpha = 0.16f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            )
            .border(
                width = 1.dp,
                color = if (selected) activeColor else Color.Transparent,
                shape = RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = if (selected) activeColor else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(8.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = if (selected) activeColor else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
