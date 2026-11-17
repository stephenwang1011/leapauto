package com.leapauto.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.TextButton
import com.leapauto.app.ui.theme.statusGood
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.CancellationException
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.leapauto.app.AirCircle
import com.leapauto.app.ChargePlanCyclesHelper
import com.leapauto.app.R
import com.leapauto.app.ScheduledAcMode
import com.leapauto.app.ScheduledAcPlan
import com.leapauto.app.ui.theme.LocalAppDarkTheme
import com.leapauto.app.ui.theme.glassInsetSurface
import com.leapauto.app.ui.theme.statusWarn

/**
 * 定时空调设置抽屉（cmdid=171）。
 * 默认值与官方抓包一致：制热 32℃、7 挡、内循环、前挡出风。
 */
@Composable
internal fun ScheduledAcPageContent(
    initialPlan: ScheduledAcPlan,
    onLoad: suspend () -> ScheduledAcPlan,
    temperatureRange: IntRange,
    fanRange: IntRange,
    onConfirm: (ScheduledAcPlan) -> Unit,
    onCancel: (ScheduledAcPlan) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var draft by remember {
        mutableStateOf(if (initialPlan.mode == ScheduledAcMode.AUTO) initialPlan.copy(mode = ScheduledAcMode.HOT) else initialPlan)
    }
    var loading by remember { mutableStateOf(true) }
    var loadFailed by remember { mutableStateOf(false) }
    var attempt by remember { mutableStateOf(0) }
    var draftDays by remember {
        mutableStateOf(ChargePlanCyclesHelper.parseToDaySet(initialPlan.days))
    }
    LaunchedEffect(attempt) {
        loading = true
        loadFailed = false
        try {
            val loaded = onLoad()
            draft = if (loaded.mode == ScheduledAcMode.AUTO) loaded.copy(mode = ScheduledAcMode.HOT) else loaded
            draftDays = ChargePlanCyclesHelper.parseToDaySet(draft.days)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            loadFailed = true
        } finally {
            loading = false
        }
    }

    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    val isDark = LocalAppDarkTheme.current
    val pageBgColor = if (isDark) Color(0xFF141414) else MaterialTheme.colorScheme.background
    val uncheckedTrack = if (isDark) Color(0xFF383838) else Color(0xFFDFDFDF)
    val uncheckedBorder = if (isDark) Color(0x26FFFFFF) else Color(0x1A000000)
    val greenSwitchColors = SwitchDefaults.colors(
        checkedThumbColor = Color.White,
        checkedTrackColor = MaterialTheme.statusGood,
        checkedBorderColor = Color.Transparent,
        uncheckedThumbColor = Color.White,
        uncheckedTrackColor = uncheckedTrack,
        uncheckedBorderColor = uncheckedBorder
    )

    fun syncIfEnabled(updatedDraft: ScheduledAcPlan, currentDays: Set<Int> = draftDays) {
        draft = updatedDraft
        onConfirm(updatedDraft.copy(days = ChargePlanCyclesHelper.toVehicleMask(currentDays)))
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = pageBgColor,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 10.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (loading) {
                Surface(
                    shape = RoundedCornerShape(percent = 50),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ) {
                    Text(
                        "正在读取服务器预约…",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else if (loadFailed) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "服务器预约读取失败，尚未确认当前状态",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                    TextButton(onClick = { attempt++ }, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)) {
                        Text("重试", style = MaterialTheme.typography.labelSmall)
                    }
                }
            } else {
                Surface(
                    shape = RoundedCornerShape(percent = 50),
                    color = if (draft.enabled) MaterialTheme.statusGood.copy(alpha = 0.14f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
                    border = BorderStroke(
                        0.5.dp,
                        if (draft.enabled) MaterialTheme.statusGood.copy(alpha = 0.40f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.40f)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .background(
                                    if (draft.enabled) MaterialTheme.statusGood else MaterialTheme.colorScheme.onSurfaceVariant,
                                    CircleShape
                                )
                        )
                        Text(
                            text = if (draft.enabled) "已读取服务器预约" else "未预约",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = if (draft.enabled) MaterialTheme.statusGood else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.glassInsetSurface,
                border = BorderStroke(0.6.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "启用定时空调",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "按设定星期与时间自动开启座舱空调",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = draft.enabled,
                        onCheckedChange = { isChecked ->
                            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                            val next = draft.copy(enabled = isChecked)
                            draft = next
                            if (isChecked) {
                                onConfirm(next.copy(days = ChargePlanCyclesHelper.toVehicleMask(draftDays)))
                            } else {
                                onCancel(next.copy(days = ChargePlanCyclesHelper.toVehicleMask(draftDays)))
                            }
                        },
                        colors = greenSwitchColors,
                        modifier = Modifier.scale(0.78f)
                    )
                }
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "开启时间",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                TimeWheelPicker(
                    timeString = draft.startTime,
                    onTimeChanged = { syncIfEnabled(draft.copy(startTime = it)) },
                    minuteStep = 1
                )
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "重复",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                val pills = listOf(
                    7 to "周日",
                    1 to "周一",
                    2 to "周二",
                    3 to "周三",
                    4 to "周四",
                    5 to "周五",
                    6 to "周六"
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    pills.forEach { (dayIndex, label) ->
                        OfficialDayPill(
                            label = label,
                            selected = draftDays.contains(dayIndex),
                            onClick = {
                                draftDays = if (draftDays.contains(dayIndex)) {
                                    if (draftDays.size > 1) draftDays - dayIndex else draftDays
                                } else {
                                    draftDays + dayIndex
                                }
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            StepperRow(
                label = "温度",
                value = "${draft.temperatureC} ℃",
                canDecrease = draft.temperatureC > temperatureRange.first,
                canIncrease = draft.temperatureC < temperatureRange.last,
                onDecrease = { syncIfEnabled(draft.copy(temperatureC = draft.temperatureC - 1)) },
                onIncrease = { syncIfEnabled(draft.copy(temperatureC = draft.temperatureC + 1)) }
            )

            StepperRow(
                label = "风量",
                value = "${draft.windLevel} 挡",
                canDecrease = draft.windLevel > fanRange.first,
                canIncrease = draft.windLevel < fanRange.last,
                onDecrease = { syncIfEnabled(draft.copy(windLevel = draft.windLevel - 1)) },
                onIncrease = { syncIfEnabled(draft.copy(windLevel = draft.windLevel + 1)) }
            )

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "模式",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(ScheduledAcMode.COLD, ScheduledAcMode.HOT).forEach { mode ->
                        ChoicePill(
                            label = mode.label,
                            selected = draft.mode == mode,
                            onClick = { syncIfEnabled(draft.copy(mode = mode)) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "循环",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    AirCircle.entries.forEach { circle ->
                        ChoicePill(
                            label = circle.displayLabel,
                            selected = draft.circle == circle,
                            onClick = { syncIfEnabled(draft.copy(circle = circle)) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // 主副驾驶座椅舒适（加热 / 通风）
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.glassInsetSurface,
                border = BorderStroke(0.6.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "全车座椅舒适",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    val crossDividerColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (isDark) 0.22f else 0.32f)

                    // 一排：主驾与副驾
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // 主驾驶位
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "主驾",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                ScheduledComfortPill(
                                    label = "加热",
                                    iconRes = R.drawable.ic_phosphor_sun,
                                    selected = draft.driverHeating,
                                    activeColor = MaterialTheme.statusWarn,
                                    onClick = {
                                        val next = !draft.driverHeating
                                        syncIfEnabled(
                                            draft.copy(
                                                driverHeating = next,
                                                driverVent = if (next) false else draft.driverVent
                                            )
                                        )
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                                ScheduledComfortPill(
                                    label = "通风",
                                    iconRes = R.drawable.ic_phosphor_snowflake,
                                    selected = draft.driverVent,
                                    activeColor = MaterialTheme.statusGood,
                                    onClick = {
                                        val next = !draft.driverVent
                                        syncIfEnabled(
                                            draft.copy(
                                                driverVent = next,
                                                driverHeating = if (next) false else draft.driverHeating
                                            )
                                        )
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }

                        // 纵向微晶分割线 (上半截)
                        Box(
                            modifier = Modifier
                                .width(0.6.dp)
                                .height(56.dp)
                                .background(crossDividerColor)
                        )

                        // 副驾驶位
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "副驾",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                ScheduledComfortPill(
                                    label = "加热",
                                    iconRes = R.drawable.ic_phosphor_sun,
                                    selected = draft.passengerHeating,
                                    activeColor = MaterialTheme.statusWarn,
                                    onClick = {
                                        val next = !draft.passengerHeating
                                        syncIfEnabled(
                                            draft.copy(
                                                passengerHeating = next,
                                                passengerVent = if (next) false else draft.passengerVent
                                            )
                                        )
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                                ScheduledComfortPill(
                                    label = "通风",
                                    iconRes = R.drawable.ic_phosphor_snowflake,
                                    selected = draft.passengerVent,
                                    activeColor = MaterialTheme.statusGood,
                                    onClick = {
                                        val next = !draft.passengerVent
                                        syncIfEnabled(
                                            draft.copy(
                                                passengerVent = next,
                                                passengerHeating = if (next) false else draft.passengerHeating
                                            )
                                        )
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }

                    // 横向微晶分割线
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 6.dp)
                            .height(0.6.dp)
                            .background(crossDividerColor)
                    )

                    // 二排：二排左与二排右
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // 二排左座
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "二排左座(主驾后)",
                                style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.5.sp),
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                ScheduledComfortPill(
                                    label = "加热",
                                    iconRes = R.drawable.ic_phosphor_sun,
                                    selected = draft.leftRearHeating,
                                    activeColor = MaterialTheme.statusWarn,
                                    onClick = {
                                        val next = !draft.leftRearHeating
                                        syncIfEnabled(
                                            draft.copy(
                                                leftRearHeating = next,
                                                leftRearVent = if (next) false else draft.leftRearVent
                                            )
                                        )
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                                ScheduledComfortPill(
                                    label = "通风",
                                    iconRes = R.drawable.ic_phosphor_snowflake,
                                    selected = draft.leftRearVent,
                                    activeColor = MaterialTheme.statusGood,
                                    onClick = {
                                        val next = !draft.leftRearVent
                                        syncIfEnabled(
                                            draft.copy(
                                                leftRearVent = next,
                                                leftRearHeating = if (next) false else draft.leftRearHeating
                                            )
                                        )
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }

                        // 纵向微晶分割线 (下半截)
                        Box(
                            modifier = Modifier
                                .width(0.6.dp)
                                .height(56.dp)
                                .background(crossDividerColor)
                        )

                        // 二排右座
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "二排右座(副驾驶后)",
                                style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.5.sp),
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                ScheduledComfortPill(
                                    label = "加热",
                                    iconRes = R.drawable.ic_phosphor_sun,
                                    selected = draft.rightRearHeating,
                                    activeColor = MaterialTheme.statusWarn,
                                    onClick = {
                                        val next = !draft.rightRearHeating
                                        syncIfEnabled(
                                            draft.copy(
                                                rightRearHeating = next,
                                                rightRearVent = if (next) false else draft.rightRearVent
                                            )
                                        )
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                                ScheduledComfortPill(
                                    label = "通风",
                                    iconRes = R.drawable.ic_phosphor_snowflake,
                                    selected = draft.rightRearVent,
                                    activeColor = MaterialTheme.statusGood,
                                    onClick = {
                                        val next = !draft.rightRearVent
                                        syncIfEnabled(
                                            draft.copy(
                                                rightRearVent = next,
                                                rightRearHeating = if (next) false else draft.rightRearHeating
                                            )
                                        )
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }

            // 方向盘加热
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.glassInsetSurface,
                border = BorderStroke(0.6.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "方向盘加热",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "定时到点时联动开启方向盘加热",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = draft.steeringWheelHeating,
                        onCheckedChange = {
                            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                            syncIfEnabled(draft.copy(steeringWheelHeating = it))
                        },
                        colors = greenSwitchColors,
                        modifier = Modifier.scale(0.78f)
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.glassInsetSurface,
                border = BorderStroke(0.6.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "关闭车窗",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "定时到点时联动关闭打开的车窗，防止冷热气外泄",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = draft.closeWindows,
                            onCheckedChange = {
                                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                                syncIfEnabled(draft.copy(closeWindows = it))
                            },
                            colors = greenSwitchColors,
                            modifier = Modifier.scale(0.78f)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "关闭遮阳帘",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "定时到点时联动关闭全景天幕遮阳帘，隔绝阳光直晒",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = draft.closeSunshade,
                            onCheckedChange = {
                                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                                syncIfEnabled(draft.copy(closeSunshade = it))
                            },
                            colors = greenSwitchColors,
                            modifier = Modifier.scale(0.78f)
                        )
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
internal fun ScheduledAcSheet(
    onDismissRequest: () -> Unit = {},
    initialPlan: ScheduledAcPlan,
    onLoad: suspend () -> ScheduledAcPlan,
    temperatureRange: IntRange,
    fanRange: IntRange,
    onConfirm: (ScheduledAcPlan) -> Unit
) {
    ScheduledAcPageContent(
        initialPlan = initialPlan,
        onLoad = onLoad,
        temperatureRange = temperatureRange,
        fanRange = fanRange,
        onConfirm = onConfirm
    )
}

@Composable
private fun StepperRow(
    label: String,
    value: String,
    canDecrease: Boolean,
    canIncrease: Boolean,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        RoundStepButton(text = "−", enabled = canDecrease, onClick = onDecrease)
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
        RoundStepButton(text = "+", enabled = canIncrease, onClick = onIncrease)
    }
}

@Composable
private fun RoundStepButton(
    text: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val isDark = LocalAppDarkTheme.current
    val btnBg = if (isDark) Color(0xFF282828) else Color(0xFFF0F0F0)
    val btnBorder = if (isDark) Color(0x33FFFFFF) else Color(0x28000000)
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = btnBg,
        border = BorderStroke(0.8.dp, btnBorder),
        modifier = Modifier
            .size(width = 42.dp, height = 36.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(enabled = enabled, onClick = onClick)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = text,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = if (enabled) {
                    if (isDark) Color.White else Color(0xFF202020)
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f)
                }
            )
        }
    }
}

@Composable
private fun ChoicePill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = LocalAppDarkTheme.current
    val unselectedBg = if (isDark) Color(0xFF282828) else Color(0xFFF2F2F2)
    val unselectedBorder = if (isDark) Color(0x33FFFFFF) else Color(0x22000000)
    val unselectedTextColor = if (isDark) Color(0xFFE2E2E2) else Color(0xFF2E2E2E)

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (selected) MaterialTheme.statusGood.copy(alpha = 0.16f) else unselectedBg,
        border = BorderStroke(
            0.8.dp,
            if (selected) MaterialTheme.statusGood.copy(alpha = 0.60f) else unselectedBorder
        ),
        modifier = modifier
            .height(36.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = label,
                fontSize = 12.5.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = if (selected) MaterialTheme.statusGood else unselectedTextColor
            )
        }
    }
}

@Composable
private fun ScheduledComfortPill(
    label: String,
    iconRes: Int,
    selected: Boolean,
    activeColor: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = LocalAppDarkTheme.current
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    val unselectedBg = if (isDark) Color(0xFF282828) else Color(0xFFF2F2F2)
    val unselectedBorder = if (isDark) Color(0x33FFFFFF) else Color(0x22000000)
    val unselectedContentColor = if (isDark) Color(0xFFE2E2E2) else Color(0xFF2E2E2E)

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (selected) activeColor.copy(alpha = 0.18f) else unselectedBg,
        border = BorderStroke(
            0.8.dp,
            if (selected) activeColor.copy(alpha = 0.65f) else unselectedBorder
        ),
        shadowElevation = 0.dp,
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable {
                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                onClick()
            }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = label,
                tint = if (selected) activeColor else unselectedContentColor,
                modifier = Modifier.size(15.dp)
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp),
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = if (selected) activeColor else unselectedContentColor,
                maxLines = 1
            )
        }
    }
}
