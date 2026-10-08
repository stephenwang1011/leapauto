package com.leapauto.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.leapauto.app.bluetooth.BleCalibration
import com.leapauto.app.ui.theme.statusGood
import java.math.BigDecimal

enum class CalibrationPreset(val label: String, val desc: String) {
    CLOSE("贴近车身", "极近感应 · 适合车门旁防误开"),
    STANDARD("标准距离", "平衡感应 · 官方平衡推荐"),
    FAR("较远感应", "远距感应 · 靠近提前迎宾")
}

internal fun getPresetCalibration(preset: CalibrationPreset, isC16: Boolean): BleCalibration = when (preset) {
    CalibrationPreset.CLOSE -> if (isC16) BleCalibration(69, 100, 2, 21) else BleCalibration(56, 200, 4, 16)
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
    onSave: (BleCalibration?) -> Unit,
    carType: String? = null
) {
    var draft by remember(calibration) { mutableStateOf(BluetoothCalibrationInput.from(calibration)) }
    val parsed = draft.parseOrNull()

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                "四元组参数微调",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                bluetoothCalibrationVehicleStatus(applied, pending),
                style = MaterialTheme.typography.labelSmall,
                color = if (applied && !pending) MaterialTheme.statusGood else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            BluetoothCalibrationField(
                label = "信号基准(A)",
                value = draft.signal,
                range = "0-255",
                busy = busy,
                decimal = false,
                modifier = Modifier.weight(1f)
            ) { draft = draft.copy(signal = it) }

            BluetoothCalibrationField(
                label = "衰减系数(n)",
                value = draft.coefficient,
                range = "0.00-655.35",
                busy = busy,
                decimal = true,
                modifier = Modifier.weight(1f)
            ) { draft = draft.copy(coefficient = it) }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            BluetoothCalibrationField(
                label = "解锁阈值",
                value = draft.unlock,
                range = "0-255 (越小越近)",
                busy = busy,
                decimal = false,
                modifier = Modifier.weight(1f)
            ) { draft = draft.copy(unlock = it) }

            BluetoothCalibrationField(
                label = "上锁阈值",
                value = draft.lock,
                range = "0-255 (越大越远)",
                busy = busy,
                decimal = false,
                modifier = Modifier.weight(1f)
            ) { draft = draft.copy(lock = it) }
        }

        if (parsed == null) {
            Text(
                "参数超出编码范围或格式无效",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = { onSave(null) },
                enabled = !busy,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.weight(1f).height(40.dp)
            ) {
                Text("恢复默认参数")
            }

            Button(
                onClick = { parsed?.let(onSave) },
                enabled = !busy && parsed != null,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.weight(1f).height(40.dp)
            ) {
                Text("应用并保存")
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
    modifier: Modifier = Modifier,
    onChange: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = { if (it.length <= 6) onChange(it) },
        enabled = !busy,
        modifier = modifier,
        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
        singleLine = true,
        supportingText = { Text(range, style = MaterialTheme.typography.labelSmall) },
        keyboardOptions = KeyboardOptions(keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number)
    )
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
