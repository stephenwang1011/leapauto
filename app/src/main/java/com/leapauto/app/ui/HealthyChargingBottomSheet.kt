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
    onApply: (healthyEnabled: Boolean, targetSoc: Int, scheduledEnabled: Boolean, startTime: String, endTime: String) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedSoc by remember(currentLimitSoc) { mutableFloatStateOf(currentLimitSoc.toFloat()) }
    var healthySwitchEnabled by remember(isHealthyChargeEnabled) { mutableStateOf(isHealthyChargeEnabled) }

    var scheduledChargeEnabled by remember(initialScheduledChargeEnabled) { mutableStateOf(initialScheduledChargeEnabled) }
    var scheduledStartTime by remember(initialScheduledStartTime) { mutableStateOf(initialScheduledStartTime) }
    var scheduledEndTime by remember(initialScheduledEndTime) { mutableStateOf(initialScheduledEndTime) }
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
        containerColor = MaterialTheme.glassSurface,
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
                color = MaterialTheme.glassInsetSurface,
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
                color = MaterialTheme.glassInsetSurface,
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
                color = MaterialTheme.glassInsetSurface,
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
                color = MaterialTheme.glassInsetSurface,
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
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TimeSelectionBox(
                                label = "开始充电",
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
                                label = "结束充电",
                                time = scheduledEndTime,
                                onClick = { showEndTimeDialog = true },
                                modifier = Modifier.weight(1f)
                            )
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

            // 6. 官方电池养护科普指引
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

            // 7. 保存并下发按钮
            Button(
                onClick = {
                    onApply(
                        healthySwitchEnabled,
                        selectedSoc.roundToInt(),
                        scheduledChargeEnabled,
                        scheduledStartTime,
                        scheduledEndTime
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
    val bgColor = if (selected) MaterialTheme.statusGood.copy(alpha = 0.12f) else Color.Transparent
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
        color = MaterialTheme.glassSurface,
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
