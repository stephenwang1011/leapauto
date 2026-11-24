package com.leapauto.app.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.leapauto.app.AirCircle
import com.leapauto.app.ChargePlanCyclesHelper
import com.leapauto.app.R
import com.leapauto.app.ScheduledAcMode
import com.leapauto.app.ScheduledAcPlan
import com.leapauto.app.ui.theme.LocalAppDarkTheme
import com.leapauto.app.ui.theme.glassInsetSurface
import com.leapauto.app.ui.theme.statusGood
import com.leapauto.app.ui.theme.statusWarn
import kotlinx.coroutines.CancellationException
import kotlin.math.roundToInt

/**
 * 定时空调设置页面（cmdid=171）。
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
    var lastSyncedWindLevel by remember {
        mutableIntStateOf(draft.windLevel.coerceIn(fanRange))
    }
    var loading by remember { mutableStateOf(true) }
    var loadFailed by remember { mutableStateOf(false) }
    var attempt by remember { mutableIntStateOf(0) }
    var draftDays by remember {
        mutableStateOf(ChargePlanCyclesHelper.parseToDaySet(initialPlan.days))
    }
    LaunchedEffect(attempt) {
        loading = true
        loadFailed = false
        try {
            val loaded = onLoad()
            draft = if (loaded.mode == ScheduledAcMode.AUTO) loaded.copy(mode = ScheduledAcMode.HOT) else loaded
            lastSyncedWindLevel = draft.windLevel.coerceIn(fanRange)
            draftDays = ChargePlanCyclesHelper.parseToDaySet(draft.days)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            loadFailed = true
        } finally {
            loading = false
        }
    }

    val view = LocalView.current
    val context = LocalContext.current
    val isDark = LocalAppDarkTheme.current
    val pageBgColor = if (isDark) Color(0xFF141414) else MaterialTheme.colorScheme.background
    val modeAccentColor = if (draft.mode == ScheduledAcMode.HOT) MaterialTheme.statusWarn else MaterialTheme.statusGood
    val hairlineColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.20f)

    fun syncIfEnabled(updatedDraft: ScheduledAcPlan, currentDays: Set<Int> = draftDays) {
        draft = updatedDraft
        lastSyncedWindLevel = updatedDraft.windLevel.coerceIn(fanRange)
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
                .padding(horizontal = 10.dp, vertical = 6.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ====== 1. 【一体化微晶定时主舱 (Scheduled Ac Master Pod)】 ======
            val masterBorder = if (draft.enabled) {
                BorderStroke(1.dp, modeAccentColor.copy(alpha = 0.45f))
            } else {
                glassCardBorder()
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .frostedGlassCard(
                        shape = RoundedCornerShape(20.dp),
                        auraColor = if (draft.enabled) modeAccentColor.copy(alpha = 0.14f) else null,
                        auraCenter = Offset(0.85f, 0.15f),
                        auraRadiusRatio = 0.70f,
                        topSpecularLine = true
                    )
                    .border(masterBorder, shape = RoundedCornerShape(20.dp))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 主控状态与开关行
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
                                text = "同步中…",
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
                                modifier = Modifier.clickable {
                                    HapticFeedbackHelper.performMechanicalClick(view, context)
                                    attempt++
                                }
                            )
                        }
                    } else {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(7.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .background(
                                        if (draft.enabled) MaterialTheme.statusGood else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                                        CircleShape
                                    )
                            )
                            Text(
                                text = if (draft.enabled) "已开启 · ${draft.startTime} 自动启动" else "定时未开启",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = if (draft.enabled) MaterialTheme.statusGood else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    CockpitSwitch(
                        checked = draft.enabled,
                        onCheckedChange = { isChecked ->
                            HapticFeedbackHelper.performMechanicalClick(view, context)
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

                // 开启时间微晶内凹仪表舱
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
                            .padding(vertical = 8.dp, horizontal = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(2.dp)
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

                // 重复周期（单字胶囊，选中即亮能量绿）
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
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.statusGood
                        )
                    }
                    val pills = listOf(
                        7 to "日",
                        1 to "一",
                        2 to "二",
                        3 to "三",
                        4 to "四",
                        5 to "五",
                        6 to "六"
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
                                    HapticFeedbackHelper.performMechanicalClick(view, context)
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

            // ====== 2. 【座舱温控设定（对齐空调页精密仪表语言）】 ======
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .frostedGlassCard(
                        shape = RoundedCornerShape(16.dp),
                        topSpecularLine = true
                    )
                    .border(glassCardBorder(), shape = RoundedCornerShape(16.dp))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "座舱温控设定",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${draft.mode.label} · ${draft.temperatureC}℃",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = modeAccentColor
                    )
                }

                // 雕塑感温度步进台
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ClimateTempStepButton(
                        symbol = "−",
                        contentDescription = "降低设定温度",
                        enabled = draft.temperatureC > temperatureRange.first,
                        activeColor = modeAccentColor,
                        onClick = {
                            HapticFeedbackHelper.performMechanicalClick(view, context)
                            syncIfEnabled(draft.copy(temperatureC = (draft.temperatureC - 1).coerceAtLeast(temperatureRange.first)))
                        }
                    )

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Row(verticalAlignment = Alignment.Top) {
                            Text(
                                text = "${draft.temperatureC}",
                                fontSize = 38.sp,
                                lineHeight = 40.sp,
                                fontWeight = FontWeight.Bold,
                                color = modeAccentColor
                            )
                            Text(
                                text = "°C",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = modeAccentColor.copy(alpha = 0.80f),
                                modifier = Modifier.padding(start = 2.dp, top = 4.dp)
                            )
                        }
                        Text(
                            text = "设定温度",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    ClimateTempStepButton(
                        symbol = "+",
                        contentDescription = "升高设定温度",
                        enabled = draft.temperatureC < temperatureRange.last,
                        activeColor = modeAccentColor,
                        onClick = {
                            HapticFeedbackHelper.performMechanicalClick(view, context)
                            syncIfEnabled(draft.copy(temperatureC = (draft.temperatureC + 1).coerceAtMost(temperatureRange.last)))
                        }
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(0.6.dp)
                        .background(hairlineColor)
                )

                // 出风风量（与空调页统一的精密滑块）
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "出风风量",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "${draft.windLevel}挡",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = modeAccentColor
                        )
                    }
                    if (fanRange.first < fanRange.last) {
                        ThinClimateSlider(
                            value = draft.windLevel.coerceIn(fanRange).toFloat(),
                            onValueChange = { nextVal ->
                                val snapped = nextVal.roundToInt().coerceIn(fanRange)
                                if (snapped != draft.windLevel) {
                                    HapticFeedbackHelper.performMechanicalClick(view, context)
                                    draft = draft.copy(windLevel = snapped)
                                }
                            },
                            onValueChangeFinished = {
                                if (draft.windLevel != lastSyncedWindLevel) {
                                    syncIfEnabled(draft)
                                }
                            },
                            onValueChangeCancelled = {
                                draft = draft.copy(windLevel = lastSyncedWindLevel)
                            },
                            enabled = true,
                            valueRange = fanRange.first.toFloat()..fanRange.last.toFloat(),
                            steps = (fanRange.last - fanRange.first - 1).coerceAtLeast(0),
                            activeColor = modeAccentColor,
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        Text(
                            text = "固定挡位",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(0.6.dp)
                        .background(hairlineColor)
                )

                // 运行模式（微晶圆角分段槽：制冷能量绿 / 制热暖琥珀）
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "运行模式",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.glassInsetSurface,
                        border = BorderStroke(0.6.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(3.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            ClimateOutletSegmentItem(
                                icon = R.drawable.ic_phosphor_snowflake,
                                label = ScheduledAcMode.COLD.label,
                                selected = draft.mode == ScheduledAcMode.COLD,
                                acOn = true,
                                activeColor = MaterialTheme.statusGood,
                                enabled = true,
                                onClick = {
                                    HapticFeedbackHelper.performMechanicalClick(view, context)
                                    syncIfEnabled(draft.copy(mode = ScheduledAcMode.COLD))
                                },
                                modifier = Modifier.weight(1f)
                            )
                            ClimateOutletSegmentItem(
                                icon = R.drawable.ic_phosphor_sun,
                                label = ScheduledAcMode.HOT.label,
                                selected = draft.mode == ScheduledAcMode.HOT,
                                acOn = true,
                                activeColor = MaterialTheme.statusWarn,
                                enabled = true,
                                onClick = {
                                    HapticFeedbackHelper.performMechanicalClick(view, context)
                                    syncIfEnabled(draft.copy(mode = ScheduledAcMode.HOT))
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                // 空气循环（微晶圆角分段槽）
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "空气循环",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.glassInsetSurface,
                        border = BorderStroke(0.6.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(3.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            AirCircle.entries.forEach { circle ->
                                val iconRes = if (circle == AirCircle.INNER) {
                                    R.drawable.ic_phosphor_arrow_clockwise
                                } else {
                                    R.drawable.ic_airflow
                                }
                                ClimateOutletSegmentItem(
                                    icon = iconRes,
                                    label = circle.displayLabel,
                                    selected = draft.circle == circle,
                                    acOn = true,
                                    activeColor = MaterialTheme.statusGood,
                                    enabled = true,
                                    onClick = {
                                        HapticFeedbackHelper.performMechanicalClick(view, context)
                                        syncIfEnabled(draft.copy(circle = circle))
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }

            // ====== 3. 【全车座椅舒适（与空调页同构无嵌套双列）】 ======
            val selectedComfortCount = listOf(
                draft.driverHeating, draft.driverVent,
                draft.passengerHeating, draft.passengerVent,
                draft.leftRearHeating, draft.leftRearVent,
                draft.rightRearHeating, draft.rightRearVent,
                draft.steeringWheelHeating
            ).count { it }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .frostedGlassCard(
                        shape = RoundedCornerShape(16.dp),
                        topSpecularLine = true
                    )
                    .border(glassCardBorder(), shape = RoundedCornerShape(16.dp))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "全车座椅舒适",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (selectedComfortCount > 0) {
                        Text(
                            text = "${selectedComfortCount}项已选",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.statusGood
                        )
                    }
                }

                // 第一排：主驾与副驾
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "主驾驶位",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
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

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "副驾驶位",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
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

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(0.6.dp)
                        .background(hairlineColor)
                )

                // 第二排：左后与右后
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "二排左座",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "主驾后",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
                            )
                        }
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

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "二排右座",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "副驾后",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
                            )
                        }
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

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(0.6.dp)
                        .background(hairlineColor)
                )

                // 方向盘加热（紧凑微晶胶囊）
                ScheduledComfortPill(
                    label = "方向盘加热",
                    iconRes = R.drawable.ic_phosphor_sun,
                    selected = draft.steeringWheelHeating,
                    activeColor = MaterialTheme.statusWarn,
                    onClick = {
                        syncIfEnabled(draft.copy(steeringWheelHeating = !draft.steeringWheelHeating))
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // ====== 4. 【到点联动（独立清爽收口卡）】 ======
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .frostedGlassCard(
                        shape = RoundedCornerShape(16.dp),
                        topSpecularLine = true
                    )
                    .border(glassCardBorder(), shape = RoundedCornerShape(16.dp))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "到点联动",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

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
                            text = "防止冷热气外泄",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    CockpitSwitch(
                        checked = draft.closeWindows,
                        onCheckedChange = {
                            HapticFeedbackHelper.performMechanicalClick(view, context)
                            syncIfEnabled(draft.copy(closeWindows = it))
                        }
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(0.6.dp)
                        .background(hairlineColor)
                )

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
                            text = "隔绝阳光直晒",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    CockpitSwitch(
                        checked = draft.closeSunshade,
                        onCheckedChange = {
                            HapticFeedbackHelper.performMechanicalClick(view, context)
                            syncIfEnabled(draft.copy(closeSunshade = it))
                        }
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
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
private fun ScheduledComfortPill(
    label: String,
    iconRes: Int,
    selected: Boolean,
    activeColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val view = LocalView.current
    val context = LocalContext.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "scheduled-comfort-pill-press"
    )
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (selected) activeColor.copy(alpha = 0.16f) else MaterialTheme.glassInsetSurface,
        border = BorderStroke(
            0.7.dp,
            if (selected) activeColor.copy(alpha = 0.55f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
        ),
        shadowElevation = 0.dp,
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null
            ) {
                HapticFeedbackHelper.performMechanicalClick(view, context)
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
                tint = if (selected) activeColor else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(15.dp)
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.5.sp),
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = if (selected) activeColor else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}
