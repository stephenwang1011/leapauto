package com.leapauto.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
    onResumeBackground: () -> Unit
) {
    var draft by remember(configuration) { mutableStateOf(configuration) }
    val changed = draft != configuration
    val supportsButton = protocolMinor != null && protocolMinor >= 9
    val canSave = changed && !busy && (!draft.buttonEnabled || supportsButton)
    val canEditSwitches = !busy

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        BluetoothPassiveHeader(
            configuration = configuration,
            appliedConfiguration = appliedConfiguration,
            configurationRequested = configurationRequested,
            configurationPending = configurationPending,
            backgroundRunning = backgroundRunning
        )
        if (!bound) {
            Text("尚未绑定车辆，请先连接并完成认证", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        // 核心日常开关 1：靠近自动解锁
        BluetoothPassiveSwitchRow("靠近自动解锁", draft.autoUnlock, canEditSwitches) {
            draft = draft.copy(autoUnlock = it, enabled = true)
        }
        // 核心日常开关 2：远离自动锁车
        BluetoothPassiveSwitchRow("远离自动锁车", draft.autoLock, canEditSwitches) {
            draft = draft.copy(autoLock = it, enabled = true)
        }

        // 更多钥匙选项 (微动开关 / 后台常驻) 默认优雅折叠收纳
        var showAdvancedKeyOptions by rememberSaveable { mutableStateOf(false) }
        Text(
            text = if (showAdvancedKeyOptions) "收起更多钥匙选项 ▴" else "更多钥匙选项 (微动开关 / 后台常驻) ▾",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.clickable { showAdvancedKeyOptions = !showAdvancedKeyOptions }.padding(vertical = 4.dp)
        )
        if (showAdvancedKeyOptions) {
            BluetoothPassiveSwitchRow(
                label = "微动开关控锁",
                checked = draft.buttonEnabled,
                enabled = canEditSwitches && supportsButton,
                detail = when {
                    protocolMinor == null -> "车辆能力尚未确认"
                    !supportsButton -> "当前车辆不支持"
                    else -> "按压门把手物理按键解锁或落锁"
                }
            ) { draft = draft.copy(buttonEnabled = it, enabled = true) }

            BluetoothPassiveSwitchRow(
                label = "后台蓝牙钥匙",
                checked = draft.enabled,
                enabled = canEditSwitches,
                detail = "保持后台常驻以实现免掏手机无感开锁"
            ) { enabled ->
                draft = if (enabled) draft.copy(enabled = true, buttonEnabled = supportsButton)
                else BlePassiveConfiguration(enabled = false, buttonEnabled = false, calibration = draft.calibration)
            }
        }

        if (changed) {
            Text("有未保存的修改", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Button(onClick = { onApplyConfiguration(draft) }, enabled = canSave,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Text("保存设置")
        }
        if (configurationPending) {
            OutlinedButton(onClick = { onApplyConfiguration(configuration) }, enabled = bound && !busy,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text("重试同步")
            }
        }
        if (configuration.enabled && !backgroundRunning) {
            OutlinedButton(onClick = onResumeBackground, enabled = bound && !busy,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text("恢复连接")
            }
        }
    }
}

@Composable
private fun BluetoothPassiveHeader(
    configuration: BlePassiveConfiguration,
    appliedConfiguration: BlePassiveConfiguration?,
    configurationRequested: Boolean,
    configurationPending: Boolean,
    backgroundRunning: Boolean
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
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("智能无感感应", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(
                text = if (backgroundRunning) "后台守护中 · 靠近自动感应" else "靠近或离开车辆时自动操作",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Surface(
            shape = RoundedCornerShape(6.dp),
            color = if (synced) MaterialTheme.statusGood.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant,
            contentColor = if (synced) MaterialTheme.statusGood else MaterialTheme.colorScheme.onSurfaceVariant
        ) {
            Text(
                text = vehicleStatus,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
            )
        }
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
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(label, style = MaterialTheme.typography.bodyLarge,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
            detail?.let { Text(it, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        Switch(checked = checked, enabled = enabled, onCheckedChange = onCheckedChange,
            modifier = Modifier.semantics { contentDescription = label })
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
                        if (configuration.buttonEnabled) add("微动开关控锁")
                    }
                    if (actions.isEmpty()) {
                        Text("本次未开启自动解锁、自动锁车或微动开关控锁。")
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
