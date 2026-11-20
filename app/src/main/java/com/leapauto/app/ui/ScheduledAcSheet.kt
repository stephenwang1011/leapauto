package com.leapauto.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.TextButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.geometry.Offset
import com.leapauto.app.ui.theme.statusGood
import com.leapauto.app.ui.theme.statusWarn
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
                .padding(horizontal = 4.dp, vertical = 6.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // ====== 1. 【一体化微晶定时主舱 (Scheduled Ac Master Pod)】 ======
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .frostedGlassCard(
                        shape = RoundedCornerShape(16.dp),
                        auraColor = if (draft.enabled) MaterialTheme.statusGood.copy(alpha = 0.12f) else null,
                        auraCenter = Offset(0.85f, 0.15f),
                        topSpecularLine = true
                    )
                    .border(glassCardBorder(), shape = RoundedCornerShape(16.dp))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                    // 主控状态与开关行：左侧同步状态指示；右侧微晶 CockpitSwitch
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        if (loading) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(12.dp),
                                    strokeWidth = 1.5.dp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "正在同步服务器预约…",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else if (loadFailed) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .background(MaterialTheme.colorScheme.error, CircleShape)
                                )
                                Text(
                                    text = "同步失败",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.error
                                )
                                Text(
                                    text = "重试",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.clickable { attempt++ }
                                )
                            }
                        } else {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .background(
                                            if (draft.enabled) MaterialTheme.statusGood else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                            CircleShape
                                        )
                                    )
                                Text(
                                    text = if (draft.enabled) "已同步云端 · 到点自动开启" else "未启用 · 云端已就绪",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (draft.enabled) MaterialTheme.statusGood else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }

                        CockpitSwitch(
                            checked = draft.enabled,
                            onCheckedChange = { isChecked ->
                                val next = draft.copy(enabled = isChecked)
                                draft = next
                                if (isChecked) {
                                    onConfirm(next.copy(days = ChargePlanCyclesHelper.toVehicleMask(draftDays)))
                                } else {
                                    onCancel(next.copy(days = ChargePlanCyclesHelper.toVehicleMask(draftDays)))
                                }
                            }
                        )
                    }

                    // 开启时间微晶内凹仪表舱（消除空洞留白与突兀感）
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.glassInsetSurface,
                        border = glassInsetBorder(),
                        shadowElevation = 0.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 10.dp, horizontal = 12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "开启时间",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            TimeWheelPicker(
                                timeString = draft.startTime,
                                onTimeChanged = { syncIfEnabled(draft.copy(startTime = it)) },
                                minuteStep = 1
                            )
                        }
                    }

                    // 重复周期
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "重复周期",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = ChargePlanCyclesHelper.formatSummary(draftDays).substringBefore(" ("),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Medium,
                                color = if (draft.enabled) MaterialTheme.statusGood else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
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
                                        syncIfEnabled(draft, draftDays)
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }

            // ====== 2. 【座舱温控工况微晶舱】 ======
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .frostedGlassCard(
                        shape = RoundedCornerShape(16.dp),
                        topSpecularLine = true
                    )
                    .border(glassCardBorder(), shape = RoundedCornerShape(16.dp))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                    Text(
                        text = "座舱温控设定",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    StepperRow(
                        label = "设定温度",
                        value = "${draft.temperatureC} ℃",
                        canDecrease = draft.temperatureC > temperatureRange.first,
                        canIncrease = draft.temperatureC < temperatureRange.last,
                        onDecrease = { syncIfEnabled(draft.copy(temperatureC = draft.temperatureC - 1)) },
                        onIncrease = { syncIfEnabled(draft.copy(temperatureC = draft.temperatureC + 1)) }
                    )

                    StepperRow(
                        label = "出风风量",
                        value = "${draft.windLevel} 挡",
                        canDecrease = draft.windLevel > fanRange.first,
                        canIncrease = draft.windLevel < fanRange.last,
                        onDecrease = { syncIfEnabled(draft.copy(windLevel = draft.windLevel - 1)) },
                        onIncrease = { syncIfEnabled(draft.copy(windLevel = draft.windLevel + 1)) }
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(0.6.dp)
                            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.20f))
                    )

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "运行模式",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
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
                            text = "空气循环",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
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
                }

            // ====== 3. 【舒适与全车联动微晶舱】 ======
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .frostedGlassCard(
                        shape = RoundedCornerShape(16.dp),
                        topSpecularLine = true
                    )
                    .border(glassCardBorder(), shape = RoundedCornerShape(16.dp))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                        text = "全车座椅舒适",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
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
                                text = "二排右座(副驾后)",
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

                    // 微晶细分界线
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(0.6.dp)
                            .background(crossDividerColor)
                    )

                    // 方向盘加热
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
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
                        CockpitSwitch(
                            checked = draft.steeringWheelHeating,
                            onCheckedChange = {
                                syncIfEnabled(draft.copy(steeringWheelHeating = it))
                            }
                        )
                    }

                    // 微晶细分界线
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(0.6.dp)
                            .background(crossDividerColor)
                    )

                    // 门窗与遮阳帘联动
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
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
                            CockpitSwitch(
                                checked = draft.closeWindows,
                                onCheckedChange = {
                                    syncIfEnabled(draft.copy(closeWindows = it))
                                }
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
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
                            CockpitSwitch(
                                checked = draft.closeSunshade,
                                onCheckedChange = {
                                syncIfEnabled(draft.copy(closeSunshade = it))
                            }
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
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = btnBg,
        border = glassInsetBorder(),
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
    val unselectedTextColor = if (isDark) Color(0xFFE2E2E2) else Color(0xFF2E2E2E)

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (selected) MaterialTheme.statusGood.copy(alpha = 0.16f) else unselectedBg,
        border = if (selected) {
            BorderStroke(0.8.dp, MaterialTheme.statusGood.copy(alpha = 0.60f))
        } else {
            glassInsetBorder()
        },
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
    val unselectedContentColor = if (isDark) Color(0xFFE2E2E2) else Color(0xFF2E2E2E)

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (selected) activeColor.copy(alpha = 0.18f) else unselectedBg,
        border = if (selected) {
            BorderStroke(0.8.dp, activeColor.copy(alpha = 0.65f))
        } else {
            glassInsetBorder()
        },
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
