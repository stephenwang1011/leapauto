package com.leapauto.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.leapauto.app.R
import com.leapauto.app.VehicleHomeStatus
import com.leapauto.app.VehicleStatus
import com.leapauto.app.ui.theme.glassInsetSurface
import com.leapauto.app.ui.theme.glassSurface
import com.leapauto.app.ui.theme.statusGood
import com.leapauto.app.ui.theme.statusWarn
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HealthyChargingBottomSheet(
    onDismissRequest: () -> Unit,
    status: VehicleStatus?,
    currentLimitSoc: Int,
    isHealthyChargeEnabled: Boolean,
    initialScheduledChargeEnabled: Boolean = false,
    initialScheduledStartTime: String = "23:00",
    initialScheduledEndTime: String = "07:00",
    initialContinueUntilLimit: Boolean = true,
    initialScheduledCirculation: Int = 1,
    initialScheduledCycles: String = "1,2,3,4,5,6,7",
    onApply: (
        healthyEnabled: Boolean,
        targetSoc: Int,
        scheduledEnabled: Boolean,
        startTime: String,
        endTime: String,
        continueUntilLimit: Boolean,
        circulation: Int,
        cycles: String
    ) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedSoc by remember(currentLimitSoc) { mutableFloatStateOf(currentLimitSoc.toFloat()) }
    var healthySwitchEnabled by remember(isHealthyChargeEnabled) { mutableStateOf(isHealthyChargeEnabled) }

    var scheduledChargeEnabled by remember(initialScheduledChargeEnabled) { mutableStateOf(initialScheduledChargeEnabled) }
    var scheduledStartTime by remember(initialScheduledStartTime) { mutableStateOf(initialScheduledStartTime) }
    var scheduledEndTime by remember(initialScheduledEndTime) { mutableStateOf(initialScheduledEndTime) }
    var continueUntilLimit by remember(initialContinueUntilLimit) { mutableStateOf(initialContinueUntilLimit) }
    var scheduledCirculation by remember(initialScheduledCirculation) { mutableIntStateOf(initialScheduledCirculation) }
    var scheduledCycles by remember(initialScheduledCycles) { mutableStateOf(initialScheduledCycles.ifBlank { "1,2,3,4,5,6,7" }) }
    var showStartTimeDialog by remember { mutableStateOf(false) }
    var showEndTimeDialog by remember { mutableStateOf(false) }

    if (showStartTimeDialog) {
        SimpleTimePickerDialog(
            title = "设定充电开始时间",
            initialTime = scheduledStartTime,
            onDismissRequest = { showStartTimeDialog = false },
            onConfirm = {
                scheduledStartTime = it
                showStartTimeDialog = false
            }
        )
    }

    if (showEndTimeDialog) {
        SimpleTimePickerDialog(
            title = "设定充电结束时间",
            initialTime = scheduledEndTime,
            onDismissRequest = { showEndTimeDialog = false },
            onConfirm = {
                scheduledEndTime = it
                showEndTimeDialog = false
            }
        )
    }

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
            // 1. 标题行
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_phosphor_battery_charging),
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.statusGood
                    )
                    Text(
                        text = "充电上限与电池健康管理",
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
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // 2. 实时动力电池简况卡片
            val normalizedSoc = VehicleHomeStatus.resolvedSoc(status?.preciseSoc, status?.soc)
            val powerSummary = VehicleHomeStatus.powerSummary(
                chargeState = status?.chargeState,
                speed = status?.speed,
                isDriving = status?.isDriving,
                batteryVoltage = status?.batteryVoltage,
                batteryCurrent = status?.batteryCurrent
            )
            val remainTime = status?.chargeRemainTime?.trim().takeUnless { it.isNullOrBlank() } ?: "--"

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.40f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp, horizontal = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ChargingMetricCell("当前电量", if (normalizedSoc != null) "$normalizedSoc%" else "--", MaterialTheme.statusGood)
                    ChargingMetricCell("充电功率", powerSummary, MaterialTheme.colorScheme.onSurface)
                    ChargingMetricCell("最低温", status?.minBatteryTemp ?: "--", MaterialTheme.colorScheme.onSurface)
                    ChargingMetricCell("剩余时间", remainTime, MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            // 3. 目标电量上限调节
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.40f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "目标充电限额",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "${selectedSoc.roundToInt()}%",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.statusGood
                        )
                    }

                    // 滑块
                    Slider(
                        value = selectedSoc,
                        onValueChange = { selectedSoc = (it / 5).roundToInt() * 5f },
                        valueRange = 50f..100f,
                        steps = 9, // 50, 55, 60, 65, 70, 75, 80, 85, 90, 95, 100
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.statusGood,
                            activeTrackColor = MaterialTheme.statusGood,
                            inactiveTrackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.60f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    // 快捷预设档位胶囊
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        QuickSocPresetChip(
                            label = "日常通勤",
                            soc = 80,
                            selected = selectedSoc.roundToInt() == 80,
                            onClick = { selectedSoc = 80f },
                            modifier = Modifier.weight(1f)
                        )
                        QuickSocPresetChip(
                            label = "均衡推荐",
                            soc = 90,
                            selected = selectedSoc.roundToInt() == 90,
                            onClick = { selectedSoc = 90f },
                            modifier = Modifier.weight(1f)
                        )
                        QuickSocPresetChip(
                            label = "长途满电",
                            soc = 100,
                            selected = selectedSoc.roundToInt() == 100,
                            onClick = { selectedSoc = 100f },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // 4. 健康充电自动保护开关
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.40f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "健康充电保护",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "慢充达到预设限额自动停止，避免动力电池长时间处于高压满电态，延长寿命",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 16.sp
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Switch(
                        checked = healthySwitchEnabled,
                        onCheckedChange = { healthySwitchEnabled = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = MaterialTheme.statusGood
                        )
                    )
                }
            }

            // 5. 谷电预约充电（按时段充电）
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.40f)),
                modifier = Modifier.fillMaxWidth()
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
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "谷电预约充电",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "开启后插枪将自动在设定的谷电时间段内充电",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Switch(
                            checked = scheduledChargeEnabled,
                            onCheckedChange = { scheduledChargeEnabled = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = MaterialTheme.statusGood
                            )
                        )
                    }

                    if (scheduledChargeEnabled) {
                        // 1) 循环方式切换 (circulation: 1=周期重复, 0=单次执行)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CirculationModeTab(
                                title = "🔁 周期重复",
                                subtitle = "按星期循环 (circulation=1)",
                                selected = scheduledCirculation == 1,
                                onClick = { scheduledCirculation = 1 },
                                modifier = Modifier.weight(1f)
                            )
                            CirculationModeTab(
                                title = "🔂 仅一次",
                                subtitle = "仅下次生效 (circulation=0)",
                                selected = scheduledCirculation == 0,
                                onClick = { scheduledCirculation = 0 },
                                modifier = Modifier.weight(1f)
                            )
                        }

                        // 2) 重复周期选择 (cycles: 星期1~7逗号分隔) - 仅在周期重复时呈现
                        if (scheduledCirculation == 1) {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                val currentDays = remember(scheduledCycles) {
                                    scheduledCycles.split(",")
                                        .mapNotNull { it.trim().toIntOrNull() }
                                        .filter { it in 1..7 }
                                        .toSet()
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "重复周期 (cycles)",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = formatCyclesSummary(scheduledCycles),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.statusGood,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                // 快捷预设：每天、工作日、周末
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    CyclePresetChip(
                                        label = "每天",
                                        selected = currentDays == setOf(1, 2, 3, 4, 5, 6, 7),
                                        onClick = { scheduledCycles = "1,2,3,4,5,6,7" },
                                        modifier = Modifier.weight(1f)
                                    )
                                    CyclePresetChip(
                                        label = "工作日",
                                        selected = currentDays == setOf(1, 2, 3, 4, 5),
                                        onClick = { scheduledCycles = "1,2,3,4,5" },
                                        modifier = Modifier.weight(1f)
                                    )
                                    CyclePresetChip(
                                        label = "周末",
                                        selected = currentDays == setOf(6, 7),
                                        onClick = { scheduledCycles = "6,7" },
                                        modifier = Modifier.weight(1f)
                                    )
                                }

                                // 周一到周日 7 颗独立多选按钮
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    val daysList = listOf(
                                        1 to "一",
                                        2 to "二",
                                        3 to "三",
                                        4 to "四",
                                        5 to "五",
                                        6 to "六",
                                        7 to "日"
                                    )
                                    daysList.forEach { (dayIdx, dayName) ->
                                        WeekDayChip(
                                            dayName = dayName,
                                            dayIndex = dayIdx,
                                            selected = currentDays.contains(dayIdx),
                                            onToggle = { idx ->
                                                val updated = if (currentDays.contains(idx)) {
                                                    if (currentDays.size > 1) currentDays - idx else currentDays
                                                } else {
                                                    currentDays + idx
                                                }
                                                scheduledCycles = updated.sorted().joinToString(",")
                                            },
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                            }
                        }

                        // 3) 开始时间与结束时间 (starttime, endtime)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TimeSelectionBox(
                                label = "开始充电 (starttime)",
                                time = scheduledStartTime,
                                onClick = { showStartTimeDialog = true },
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = "至",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            TimeSelectionBox(
                                label = "结束充电 (endtime)",
                                time = scheduledEndTime,
                                onClick = { showEndTimeDialog = true },
                                modifier = Modifier.weight(1f)
                            )
                        }

                        // 4) 未达上限继续充电开关 (recharge)
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Text(
                                        text = "未达上限继续充电 (recharge)",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = "到达停止时间若未达充电上限，将继续充电直至达到目标电量",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 10.sp,
                                        lineHeight = 14.sp
                                    )
                                }
                                Spacer(Modifier.width(8.dp))
                                Switch(
                                    checked = continueUntilLimit,
                                    onCheckedChange = { continueUntilLimit = it },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color.White,
                                        checkedTrackColor = MaterialTheme.statusGood
                                    )
                                )
                            }
                        }

                        Text(
                            text = "💡 默认匹配全国 23:00 ~ 次日 07:00 谷电低价时段，插枪后车辆保持待机，到点自动开充。",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 16.sp
                        )
                    }
                }
            }

            // 6. 协议参数实时预览（与车机 T-Box cmdid=190 原生字段 100% 对齐）
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                border = BorderStroke(0.6.dp, MaterialTheme.statusGood.copy(alpha = 0.35f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "📋 充电计划协议参数 (cmdid=190)",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.statusGood
                        )
                        Text(
                            text = if (scheduledChargeEnabled) "预约就绪" else "预约关闭",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (scheduledChargeEnabled) MaterialTheme.statusGood else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "• chargeEnable: ${if (scheduledChargeEnabled) 1 else 0}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "• chargesoc: ${selectedSoc.roundToInt()}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.statusGood,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "• recharge: ${if (continueUntilLimit) 1 else 0}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "• circulation: ${if (scheduledChargeEnabled) scheduledCirculation else 0} (${if (scheduledCirculation == 1 && scheduledChargeEnabled) "周期" else "单次"})",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "• cycles: \"${if (scheduledChargeEnabled && scheduledCirculation == 1) scheduledCycles else ""}\"",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Text(
                        text = "• 时段: $scheduledStartTime ~ $scheduledEndTime (starttime / endtime)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // 7. 官方电池养护科普指引
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.statusGood.copy(alpha = 0.08f),
                border = BorderStroke(0.5.dp, MaterialTheme.statusGood.copy(alpha = 0.25f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "💡 电池健康养护小贴士",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.statusGood
                    )
                    Text(
                        text = "• 三元锂车型：日常通勤建议设置 80%~90%，长途出行前充至 100%；\n• 磷酸铁锂车型：日常建议 90%，建议每月至少慢充充满一次以完成 BMS 压差均衡。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 18.sp
                    )
                }
            }

            // 8. 保存并下发按钮
            Button(
                onClick = {
                    onApply(
                        healthySwitchEnabled,
                        selectedSoc.roundToInt(),
                        scheduledChargeEnabled,
                        scheduledStartTime,
                        scheduledEndTime,
                        continueUntilLimit,
                        scheduledCirculation,
                        scheduledCycles
                    )
                },
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Text(
                    text = "保存并下发设置",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun ChargingMetricCell(
    label: String,
    value: String,
    valueColor: Color
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = valueColor,
            maxLines = 1
        )
    }
}

@Composable
private fun QuickSocPresetChip(
    label: String,
    soc: Int,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val borderColor = if (selected) MaterialTheme.statusGood else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.50f)
    val bgColor = if (selected) MaterialTheme.statusGood.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surface
    val textColor = if (selected) MaterialTheme.statusGood else MaterialTheme.colorScheme.onSurface

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(bgColor)
            .border(if (selected) 1.2.dp else 0.6.dp, borderColor, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "$soc%",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = textColor
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = if (selected) MaterialTheme.statusGood else MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 10.sp
            )
        }
    }
}

@Composable
private fun TimeSelectionBox(
    label: String,
    time: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(0.6.dp, MaterialTheme.statusGood.copy(alpha = 0.45f)),
        modifier = modifier.clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp
            )
            Text(
                text = time,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.statusGood
            )
        }
    }
}

private fun formatCyclesSummary(cycles: String): String {
    val days = cycles.split(",").mapNotNull { it.trim().toIntOrNull() }.filter { it in 1..7 }.toSet()
    return when {
        days == setOf(1, 2, 3, 4, 5, 6, 7) -> "每天"
        days == setOf(1, 2, 3, 4, 5) -> "工作日 (周一至周五)"
        days == setOf(6, 7) -> "周末 (周六、周日)"
        days.isEmpty() -> "未选择"
        else -> {
            val names = mapOf(1 to "一", 2 to "二", 3 to "三", 4 to "四", 5 to "五", 6 to "六", 7 to "日")
            "周" + days.sorted().mapNotNull { names[it] }.joinToString("、")
        }
    }
}

@Composable
private fun CirculationModeTab(
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val borderColor = if (selected) MaterialTheme.statusGood else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
    val bgColor = if (selected) MaterialTheme.statusGood.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface
    val titleColor = if (selected) MaterialTheme.statusGood else MaterialTheme.colorScheme.onSurface

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(bgColor)
            .border(if (selected) 1.2.dp else 0.6.dp, borderColor, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                color = titleColor
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = if (selected) MaterialTheme.statusGood.copy(alpha = 0.85f) else MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 10.sp,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun CyclePresetChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bgColor = if (selected) MaterialTheme.statusGood.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surface
    val contentColor = if (selected) MaterialTheme.statusGood else MaterialTheme.colorScheme.onSurfaceVariant
    val borderColor = if (selected) MaterialTheme.statusGood else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.40f)

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor)
            .border(if (selected) 1.dp else 0.6.dp, borderColor, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = contentColor
        )
    }
}

@Composable
private fun WeekDayChip(
    dayName: String,
    dayIndex: Int,
    selected: Boolean,
    onToggle: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val bgColor = if (selected) MaterialTheme.statusGood else MaterialTheme.colorScheme.surface
    val contentColor = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
    val borderColor = if (selected) MaterialTheme.statusGood else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.40f)

    Box(
        modifier = modifier
            .height(34.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor)
            .border(0.6.dp, borderColor, RoundedCornerShape(8.dp))
            .clickable { onToggle(dayIndex) },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = dayName,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = contentColor
        )
    }
}
