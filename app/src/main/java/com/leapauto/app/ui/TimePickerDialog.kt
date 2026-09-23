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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.leapauto.app.ui.theme.glassInsetSurface
import com.leapauto.app.ui.theme.glassSurface
import com.leapauto.app.ui.theme.statusGood

@Composable
fun SimpleTimePickerDialog(
    title: String,
    initialTime: String = "23:00",
    onDismissRequest: () -> Unit,
    onConfirm: (String) -> Unit
) {
    val initialParts = initialTime.split(":").mapNotNull { it.trim().toIntOrNull() }
    val initH = initialParts.getOrNull(0)?.coerceIn(0, 23) ?: 23
    val initM = initialParts.getOrNull(1)?.coerceIn(0, 59) ?: 0

    var selectedHour by remember { mutableIntStateOf(initH) }
    var selectedMinute by remember { mutableIntStateOf(initM) }

    val commonPresets = listOf(
        "22:00" to (22 to 0),
        "23:00" to (23 to 0),
        "00:00" to (0 to 0),
        "06:30" to (6 to 30),
        "07:00" to (7 to 0),
        "08:00" to (8 to 0)
    )

    AlertDialog(
        onDismissRequest = onDismissRequest,
        modifier = solidDialogModifier(shape = RoundedCornerShape(24.dp)),
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(24.dp),
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 1. 大字体数字微调调节器
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.40f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp, horizontal = 16.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 小时调节列
                        TimeStepperColumn(
                            value = selectedHour,
                            maxValue = 23,
                            onValueChange = { selectedHour = it }
                        )

                        Text(
                            text = ":",
                            fontSize = 36.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.statusGood,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )

                        // 分钟调节列
                        TimeStepperColumn(
                            value = selectedMinute,
                            maxValue = 59,
                            step = 5,
                            onValueChange = { selectedMinute = it }
                        )
                    }
                }

                // 2. 常用时段快捷选择
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "常用时段快捷选择：",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        commonPresets.take(3).forEach { (label, pair) ->
                            val isSelected = selectedHour == pair.first && selectedMinute == pair.second
                            PresetTimePill(
                                label = label,
                                selected = isSelected,
                                onClick = {
                                    selectedHour = pair.first
                                    selectedMinute = pair.second
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        commonPresets.drop(3).take(3).forEach { (label, pair) ->
                            val isSelected = selectedHour == pair.first && selectedMinute == pair.second
                            PresetTimePill(
                                label = label,
                                selected = isSelected,
                                onClick = {
                                    selectedHour = pair.first
                                    selectedMinute = pair.second
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val formatted = String.format(java.util.Locale.ROOT, "%02d:%02d", selectedHour, selectedMinute)
                    onConfirm(formatted)
                },
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text("确定", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismissRequest,
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("取消")
            }
        }
    )
}

@Composable
private fun TimeStepperColumn(
    value: Int,
    maxValue: Int,
    step: Int = 1,
    onValueChange: (Int) -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        IconButton(
            onClick = { onValueChange((value + step) % (maxValue + 1)) },
            modifier = Modifier.size(32.dp)
        ) {
            Text("▲", fontSize = 14.sp, color = MaterialTheme.statusGood, fontWeight = FontWeight.Bold)
        }
        Text(
            text = String.format(java.util.Locale.ROOT, "%02d", value),
            fontSize = 36.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        IconButton(
            onClick = { onValueChange((value - step + maxValue + 1) % (maxValue + 1)) },
            modifier = Modifier.size(32.dp)
        ) {
            Text("▼", fontSize = 14.sp, color = MaterialTheme.statusGood, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun PresetTimePill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val borderColor = if (selected) MaterialTheme.statusGood else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.50f)
    val bgColor = if (selected) MaterialTheme.statusGood.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surface
    val textColor = if (selected) MaterialTheme.statusGood else MaterialTheme.colorScheme.onSurface

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor)
            .border(if (selected) 1.2.dp else 0.6.dp, borderColor, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = textColor
        )
    }
}
