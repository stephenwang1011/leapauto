package com.leapauto.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.leapauto.app.R
import com.leapauto.app.bluetooth.BleCalibration
import com.leapauto.app.ui.theme.statusGood
import java.math.BigDecimal

enum class CalibrationPreset(val label: String, val desc: String) {
    CLOSE("贴近车身", "约1米 · 避免路过误弹把手"),
    STANDARD("标准距离", "约1.5米 · 官方推荐"),
    FAR("较远感应", "约2.5米 · 靠近提前解锁")
}

private fun getPresetCalibration(preset: CalibrationPreset, isC16: Boolean): BleCalibration = when (preset) {
    CalibrationPreset.CLOSE -> if (isC16) BleCalibration(69, 100, 3, 21) else BleCalibration(56, 200, 6, 16)
    CalibrationPreset.STANDARD -> if (isC16) BleCalibration.C16_DEFAULT else BleCalibration.DEFAULT
    CalibrationPreset.FAR -> if (isC16) BleCalibration(69, 100, 7, 21) else BleCalibration(56, 200, 11, 16)
}

internal data class BluetoothCalibrationInput(
    val signal: String,
    val coefficient: String,
    val unlock: String,
    val lock: String
) {
    fun parseOrNull(): BleCalibration? {
        if (!INTEGER.matches(signal) || !DECIMAL.matches(coefficient) ||
            !INTEGER.matches(unlock) || !INTEGER.matches(lock)) return null
        return runCatching {
            BleCalibration(signal.toInt(), BigDecimal(coefficient).movePointRight(2).intValueExact(),
                unlock.toInt(), lock.toInt())
        }.getOrNull()
    }

    companion object {
        private val INTEGER = Regex("[0-9]{1,3}")
        private val DECIMAL = Regex("[0-9]{1,3}(?:\\.[0-9]{0,2})?")

        fun from(calibration: BleCalibration): BluetoothCalibrationInput = BluetoothCalibrationInput(
            calibration.distanceCalibration.toString(),
            "${calibration.coefficientHundredths / 100}." +
                (calibration.coefficientHundredths % 100).toString().padStart(2, '0'),
            calibration.unlockCalibration.toString(), calibration.lockCalibration.toString()
        )
    }
}

internal fun bluetoothCalibrationVehicleStatus(applied: Boolean, pending: Boolean): String = when {
    pending -> "等待车辆应用"
    applied -> "车辆已应用"
    else -> "车辆尚未确认"
}

@Composable
internal fun BluetoothCalibrationEditor(
    calibration: BleCalibration,
    applied: Boolean,
    pending: Boolean,
    busy: Boolean,
    onSave: (BleCalibration?) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var draft by remember(calibration) { mutableStateOf(BluetoothCalibrationInput.from(calibration)) }
    val parsed = draft.parseOrNull()
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { expanded = !expanded },
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f)) {
                Text("感应距离标定", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text("按车主习惯调节靠近解锁与闭锁灵敏度", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(painterResource(R.drawable.ic_phosphor_caret_right),
                contentDescription = if (expanded) "收起标定设置" else "展开标定设置",
                modifier = Modifier.size(18.dp).rotate(if (expanded) 90f else 0f))
        }
        if (expanded) {
            Text(bluetoothCalibrationVehicleStatus(applied, pending), style = MaterialTheme.typography.bodyMedium,
                color = if (applied && !pending) MaterialTheme.statusGood else MaterialTheme.colorScheme.onSurfaceVariant)

            // 三档分段预设档位
            val isC16 = calibration.distanceCalibration == 69 || calibration == BleCalibration.C16_DEFAULT
            Text("推荐预设档位", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                CalibrationPreset.entries.forEach { preset ->
                    val targetCalib = getPresetCalibration(preset, isC16)
                    val isSelected = parsed == targetCalib
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            if (!busy) {
                                draft = BluetoothCalibrationInput.from(targetCalib)
                            }
                        },
                        label = {
                            Text(preset.label, style = MaterialTheme.typography.labelSmall)
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                            selectedLabelColor = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            var showRawFields by remember { mutableStateOf(false) }
            Text(
                text = if (showRawFields) "收起底层标定参数 ▴" else "展开底层标定参数微调 ▾",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable { showRawFields = !showRawFields }.padding(vertical = 4.dp)
            )

            if (showRawFields) {
                BluetoothCalibrationField("信号标定参数", draft.signal, "0-255", busy, false) {
                    draft = draft.copy(signal = it)
                }
                BluetoothCalibrationField("标定系数", draft.coefficient, "0.00-655.35", busy, true) {
                    draft = draft.copy(coefficient = it)
                }
                BluetoothCalibrationField("解锁标定参数", draft.unlock, "0-255", busy, false) {
                    draft = draft.copy(unlock = it)
                }
                BluetoothCalibrationField("上锁标定参数", draft.lock, "0-255", busy, false) {
                    draft = draft.copy(lock = it)
                }
            }
            if (parsed == null) {
                Text("参数超出编码范围或格式无效", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error)
            }
            Button(onClick = { parsed?.let(onSave) }, enabled = !busy && parsed != null,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text("应用并保存标定")
            }
            OutlinedButton(onClick = { onSave(null) }, enabled = !busy,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text("恢复默认标定")
            }
        }
    }
}

@Composable
private fun BluetoothCalibrationField(
    label: String,
    value: String,
    range: String,
    busy: Boolean,
    decimal: Boolean,
    onChange: (String) -> Unit
) {
    OutlinedTextField(value = value, onValueChange = { if (it.length <= 6) onChange(it) }, enabled = !busy,
        modifier = Modifier.fillMaxWidth(), label = { Text(label) }, singleLine = true,
        supportingText = { Text("编码范围 $range") },
        keyboardOptions = KeyboardOptions(keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number))
}

@Composable
fun BluetoothCalibrationConfirmation(calibration: BleCalibration?, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = solidDialogModifier(),
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        shape = RoundedCornerShape(24.dp),
        title = { Text(if (calibration == null) "清除自定义标定" else "确认标定参数") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (calibration == null) {
                    Text("清除本机自定义标定并删除云端记录。后续连接使用本应用默认参数，车辆应用状态以回执为准。")
                } else {
                    val values = BluetoothCalibrationInput.from(calibration)
                    Text("信号标定参数：${values.signal}\n标定系数：${values.coefficient}\n" +
                        "解锁标定参数：${values.unlock}\n上锁标定参数：${values.lock}")
                    Text("保存到本机并提交云端。参数可能影响感应触发，车辆应用状态以回执为准。")
                }
            }
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text("确认") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}
