package com.leapauto.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.leapauto.app.R
import com.leapauto.app.bluetooth.BleCalibration
import com.leapauto.app.ui.theme.statusGood
import java.math.BigDecimal

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
            Text("高级标定设置", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Icon(painterResource(R.drawable.ic_phosphor_caret_right),
                contentDescription = if (expanded) "收起标定设置" else "展开标定设置",
                modifier = Modifier.size(18.dp).rotate(if (expanded) 90f else 0f))
        }
        if (expanded) {
            Text(bluetoothCalibrationVehicleStatus(applied, pending), style = MaterialTheme.typography.bodyMedium,
                color = if (applied && !pending) MaterialTheme.statusGood else MaterialTheme.colorScheme.onSurfaceVariant)
            Text("协议标定参数，非米制距离。", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
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
            if (parsed == null) {
                Text("参数超出编码范围或格式无效", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error)
            }
            Button(onClick = { parsed?.let(onSave) }, enabled = !busy && parsed != null && parsed != calibration,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text("保存标定参数")
            }
            OutlinedButton(onClick = { onSave(null) }, enabled = !busy,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text("清除自定义标定")
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
