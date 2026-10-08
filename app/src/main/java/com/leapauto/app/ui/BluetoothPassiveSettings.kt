package com.leapauto.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.leapauto.app.R
import com.leapauto.app.bluetooth.BleCalibration
import com.leapauto.app.bluetooth.BlePassiveConfiguration
import com.leapauto.app.ui.theme.statusGood

@Composable
internal fun BluetoothPassiveSettings(
    configuration: BlePassiveConfiguration,
    appliedConfiguration: BlePassiveConfiguration?,
    configurationRequested: Boolean,
    configurationPending: Boolean,
    backgroundRunning: Boolean,
    bound: Boolean,
    protocolMinor: Int?,
    busy: Boolean,
    onApplyConfiguration: (BlePassiveConfiguration) -> Unit,
    onResumeBackground: () -> Unit,
    carType: String? = null
) {
    val supportsButton = protocolMinor == null || protocolMinor >= 9
    var draft by remember(configuration) {
        val initial = if (configuration.enabled && supportsButton && !configuration.buttonEnabled) {
            configuration.copy(buttonEnabled = true)
        } else {
            configuration
        }
        mutableStateOf(initial)
    }
    val changed = draft != configuration
    val canSave = changed && !busy
    val canEditSwitches = !busy
    val isC16 = carType?.uppercase()?.let { it.contains("C16") || it.contains("C10") } == true ||
        draft.calibration.distanceCalibration == 69 || draft.calibration == BleCalibration.C16_DEFAULT

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // 顶部一体化标题栏：智能无感总开关与同步状态合并
        BluetoothPassiveHeaderRow(
            enabled = draft.enabled,
            canToggle = canEditSwitches,
            configuration = configuration,
            appliedConfiguration = appliedConfiguration,
            configurationRequested = configurationRequested,
            configurationPending = configurationPending,
            backgroundRunning = backgroundRunning,
            onToggleEnabled = { enabled ->
                draft = if (enabled) {
                    draft.copy(enabled = true, buttonEnabled = supportsButton)
                } else {
                    BlePassiveConfiguration(enabled = false, buttonEnabled = supportsButton, calibration = draft.calibration)
                }
            }
        )

        if (!bound) {
            Text(
                "尚未绑定车辆，请先连接并完成认证",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // 无感子项（靠近解锁、远离锁车、感应距离、微动开关）
        AnimatedVisibility(
            visible = draft.enabled,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))

                BluetoothPassiveSwitchRow(
                    label = "靠近自动解锁",
                    checked = draft.autoUnlock,
                    enabled = canEditSwitches && draft.enabled
                ) {
                    draft = draft.copy(autoUnlock = it)
                }

                BluetoothPassiveSwitchRow(
                    label = "远离自动锁车",
                    checked = draft.autoLock,
                    enabled = canEditSwitches && draft.enabled
                ) {
                    draft = draft.copy(autoLock = it)
                }

                // 常用感应距离三档快捷选择外显
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "感应距离档位",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        val currentPreset = CalibrationPreset.entries.firstOrNull {
                            draft.calibration == getPresetCalibration(it, isC16)
                        }
                        Text(
                            text = currentPreset?.desc ?: "自定义参数",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        CalibrationPreset.entries.forEach { preset ->
                            val targetCalib = getPresetCalibration(preset, isC16)
                            val isSelected = draft.calibration == targetCalib
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    if (canEditSwitches) {
                                        draft = draft.copy(calibration = targetCalib)
                                    }
                                },
                                label = {
                                    Box(
                                        modifier = Modifier.fillMaxWidth(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = preset.label,
                                            style = MaterialTheme.typography.labelSmall,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                    selectedLabelColor = MaterialTheme.colorScheme.primary
                                ),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }

        // 仅当配置发生修改时，平滑展开保存操作按钮，不占多余常驻空间
        AnimatedVisibility(
            visible = changed,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Column(
                modifier = Modifier.padding(top = 4.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Button(
                    onClick = { onApplyConfiguration(draft) },
                    enabled = canSave,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                ) {
                    Text("保存并同步至车辆", fontWeight = FontWeight.SemiBold)
                }
            }
        }

        // 状态补偿辅助按钮 (待同步/未恢复连接)
        if (configurationPending && !changed) {
            OutlinedButton(
                onClick = { onApplyConfiguration(configuration) },
                enabled = bound && !busy,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
            ) {
                Text("重试同步车端设置")
            }
        }
        if (configuration.enabled && !backgroundRunning && !changed) {
            OutlinedButton(
                onClick = onResumeBackground,
                enabled = bound && !busy,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
            ) {
                Text("恢复后台连接")
            }
        }
    }
}

@Composable
private fun BluetoothPassiveHeaderRow(
    enabled: Boolean,
    canToggle: Boolean,
    configuration: BlePassiveConfiguration,
    appliedConfiguration: BlePassiveConfiguration?,
    configurationRequested: Boolean,
    configurationPending: Boolean,
    backgroundRunning: Boolean,
    onToggleEnabled: (Boolean) -> Unit
) {
    val synced = configurationRequested && !configurationPending && appliedConfiguration == configuration
    val vehicleStatus = when {
        !configurationRequested && !configurationPending -> "未启用"
        synced -> "已同步车端"
        configuration.enabled -> "待车端同步"
        else -> "关闭待同步"
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "智能无感钥匙",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = if (synced) MaterialTheme.statusGood.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant,
                contentColor = if (synced) MaterialTheme.statusGood else MaterialTheme.colorScheme.onSurfaceVariant
            ) {
                Text(
                    text = vehicleStatus,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
        ClimateToggle(
            checked = enabled,
            onCheckedChange = if (canToggle) onToggleEnabled else null,
            contentDescription = "智能无感钥匙总开关",
            stateDescription = if (enabled) "已开启" else "已关闭"
        )
    }
}

@Composable
private fun BluetoothPassiveSwitchRow(
    label: String,
    checked: Boolean,
    enabled: Boolean,
    detail: String? = null,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
            )
            detail?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        ClimateToggle(
            checked = checked,
            onCheckedChange = if (enabled) onCheckedChange else null,
            contentDescription = label,
            stateDescription = if (checked) "已开启" else "已关闭"
        )
    }
}

@Composable
fun BluetoothConfigurationConfirmation(
    configuration: BlePassiveConfiguration,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = solidDialogModifier(),
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        shape = RoundedCornerShape(24.dp),
        title = { Text(if (configuration.enabled) "确认后台蓝牙设置" else "确认关闭后台蓝牙") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (configuration.enabled) {
                    Text("启用后会显示后台连接通知，并在后台连接车辆。")
                    val actions = buildList {
                        if (configuration.autoUnlock) add("靠近车辆时自动解锁")
                        if (configuration.autoLock) add("远离车辆时自动锁车")
                    }
                    if (actions.isEmpty()) {
                        Text("本次未开启靠近自动解锁或远离自动锁车。")
                    } else {
                        Text("本次将向车辆同步：${actions.joinToString("、")}。")
                    }
                } else {
                    Text("关闭设置经车辆确认后，将停止后台连接。同步完成前，车辆可能保留原来的自动操作设置。")
                }
            }
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text("确认保存") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}
