package com.leapauto.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.leapauto.app.OtaStatus
import com.leapauto.app.R
import com.leapauto.app.VehicleOtaInfo
import com.leapauto.app.VehicleOtaPolicy
import com.leapauto.app.VehicleOtaState
import com.leapauto.app.ui.theme.glassInsetSurface
import com.leapauto.app.ui.theme.statusGood
import com.leapauto.app.ui.theme.statusWarn
import kotlinx.coroutines.delay

@Composable
fun VehicleOtaCard(
    state: VehicleOtaState,
    isSubAccount: Boolean = false,
    onCheck: () -> Unit,
    onDownload: (String) -> Unit,
    onInstall: (String, String) -> Unit, // taskId, pin
    onSchedule: (String, String, String) -> Unit, // taskId, scheduleTime, pin
    modifier: Modifier = Modifier
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    var showSafetyDialog by remember { mutableStateOf(false) }
    var showScheduleDialog by remember { mutableStateOf(false) }

    val info = (state as? VehicleOtaState.Success)?.info
    val otaStatus = info?.status ?: OtaStatus.UNKNOWN

    val auraColor = when {
        isSubAccount -> MaterialTheme.statusWarn.copy(alpha = 0.08f)
        otaStatus == OtaStatus.UP_TO_DATE -> MaterialTheme.statusGood.copy(alpha = 0.10f)
        otaStatus == OtaStatus.UPDATE_AVAILABLE || otaStatus == OtaStatus.DOWNLOADED -> MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
        otaStatus == OtaStatus.DOWNLOADING || otaStatus == OtaStatus.INSTALLING -> MaterialTheme.statusWarn.copy(alpha = 0.14f)
        otaStatus == OtaStatus.FAILED || state is VehicleOtaState.Error -> MaterialTheme.colorScheme.error.copy(alpha = 0.12f)
        else -> null
    }

    val statusBadgeColor = when {
        isSubAccount -> MaterialTheme.statusWarn
        otaStatus == OtaStatus.UP_TO_DATE -> MaterialTheme.statusGood
        otaStatus == OtaStatus.UPDATE_AVAILABLE || otaStatus == OtaStatus.DOWNLOADED -> MaterialTheme.colorScheme.primary
        otaStatus == OtaStatus.DOWNLOADING || otaStatus == OtaStatus.INSTALLING -> MaterialTheme.statusWarn
        otaStatus == OtaStatus.FAILED || state is VehicleOtaState.Error -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.primary
    }

    val arrowRotation by animateFloatAsState(
        targetValue = if (expanded) 90f else 0f,
        animationSpec = tween(220),
        label = "otaCaretRotation"
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .frostedGlassCard(
                shape = RoundedCornerShape(16.dp),
                auraColor = auraColor,
                auraCenter = Offset(0.85f, 0.15f)
            ),
        shape = RoundedCornerShape(16.dp),
        color = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = glassCardBorder(),
        shadowElevation = 0.dp
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        expanded = !expanded
                        if (expanded && state is VehicleOtaState.Idle && !isSubAccount) {
                            onCheck()
                        }
                    },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    modifier = Modifier.size(36.dp),
                    shape = CircleShape,
                    color = statusBadgeColor.copy(alpha = 0.12f)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            painter = painterResource(R.drawable.ic_fota_cloud),
                            contentDescription = "车机OTA",
                            tint = statusBadgeColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "车机系统 OTA",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (info?.hasNewVersion == true) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .background(MaterialTheme.colorScheme.error, CircleShape)
                            )
                        }
                    }
                    val subtitleText = when {
                        isSubAccount && state is VehicleOtaState.Idle -> "当前为授权子账号 · 仅车主有权限"
                        state is VehicleOtaState.Idle -> "点击检查车机系统新固件"
                        state is VehicleOtaState.Checking -> "正在检查车机固件版本..."
                        state is VehicleOtaState.Error -> "检查失败：${state.message}"
                        state is VehicleOtaState.Success -> {
                            val ver = state.info.currentVersion.ifBlank { "--" }
                            val tag = when (otaStatus) {
                                OtaStatus.UP_TO_DATE -> "已是最新版本"
                                OtaStatus.UPDATE_AVAILABLE -> "发现新版本 ${state.info.newVersion}"
                                OtaStatus.DOWNLOADING -> "固件下载中 ${state.info.progressPercent ?: 0}%"
                                OtaStatus.DOWNLOADED -> "固件已就绪 · 待安装"
                                OtaStatus.INSTALLING -> "系统刷写升级中..."
                                OtaStatus.SCHEDULED -> "已预约定时升级"
                                OtaStatus.FAILED -> "升级异常"
                                OtaStatus.UNKNOWN -> "版本 $ver"
                            }
                            "当前：$ver · $tag"
                        }
                        else -> ""
                    }
                    Text(
                        text = subtitleText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }

                Icon(
                    painter = painterResource(R.drawable.ic_phosphor_caret_right),
                    contentDescription = if (expanded) "收起" else "展开",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .size(16.dp)
                        .rotate(arrowRotation)
                )
            }

            // Expanded Content
            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(0.5.dp)
                            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
                    )

                    if (isSubAccount) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.statusWarn.copy(alpha = 0.08f),
                            border = BorderStroke(0.5.dp, MaterialTheme.statusWarn.copy(alpha = 0.35f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_phosphor_car),
                                    contentDescription = "子账号提示",
                                    tint = MaterialTheme.statusWarn,
                                    modifier = Modifier.size(20.dp).padding(top = 1.dp)
                                )
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(
                                        text = VehicleOtaPolicy.SUB_ACCOUNT_OTA_HINT_TITLE,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.statusWarn
                                    )
                                    Text(
                                        text = VehicleOtaPolicy.SUB_ACCOUNT_OTA_HINT_DESC,
                                        style = MaterialTheme.typography.bodySmall.copy(lineHeight = 17.sp),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        if (state is VehicleOtaState.Error) {
                            Text(
                                text = state.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }

                        OutlinedButton(
                            onClick = onCheck,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("仍要尝试检查")
                        }
                    } else {
                        when (state) {
                            is VehicleOtaState.Idle -> {
                                OutlinedButton(
                                    onClick = onCheck,
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text("立即检查车机系统更新")
                                }
                            }
                            is VehicleOtaState.Checking -> {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 12.dp),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Text(
                                        text = "正在与车辆通讯并查询固件版本...",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            is VehicleOtaState.Error -> {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(
                                        text = state.message,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                    OutlinedButton(
                                        onClick = onCheck,
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Text("重新检查")
                                    }
                                }
                            }
                            is VehicleOtaState.Success -> {
                                val data = state.info
                                if (data.hasNewVersion) {
                                // Target Version Details
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.glassInsetSurface,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text(
                                            text = "目标版本：${data.newVersion.ifBlank { "新固件" }}",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            if (data.packageSize.isNotBlank()) {
                                                Text(
                                                    text = "固件体积：${data.packageSize}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            if (data.taskId.isNotBlank()) {
                                                Text(
                                                    text = "任务编号：#${data.taskId}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                        if (data.scheduledTime != null) {
                                            Text(
                                                text = "已预约安装：${data.scheduledTime}",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.statusGood
                                            )
                                        }
                                    }
                                }

                                // Download Progress Bar
                                if (data.isDownloading || (data.progressPercent != null && data.progressPercent in 1..99)) {
                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = "固件包下载进度",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Text(
                                                text = "${data.progressPercent ?: 0}%",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.statusWarn
                                            )
                                        }
                                        LinearProgressIndicator(
                                            progress = { (data.progressPercent ?: 0) / 100f },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(6.dp)
                                                .clip(RoundedCornerShape(3.dp)),
                                            color = MaterialTheme.statusWarn,
                                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                                        )
                                    }
                                }

                                // Release Notes
                                if (data.releaseNotes.isNotBlank()) {
                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(
                                            text = "更新日志",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Surface(
                                            shape = RoundedCornerShape(10.dp),
                                            color = MaterialTheme.glassInsetSurface,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text(
                                                text = data.releaseNotes,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.padding(10.dp)
                                            )
                                        }
                                    }
                                }

                                // Action Buttons
                                when (data.status) {
                                    OtaStatus.UPDATE_AVAILABLE -> {
                                        Button(
                                            onClick = { onDownload(data.taskId) },
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(10.dp)
                                        ) {
                                            Text("远程下载固件包")
                                        }
                                    }
                                    OtaStatus.DOWNLOADING -> {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            OutlinedButton(
                                                onClick = onCheck,
                                                modifier = Modifier.weight(1f),
                                                shape = RoundedCornerShape(10.dp)
                                            ) {
                                                Text("刷新下载进度")
                                            }
                                        }
                                    }
                                    OtaStatus.DOWNLOADED -> {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            OutlinedButton(
                                                onClick = { showScheduleDialog = true },
                                                modifier = Modifier.weight(1f),
                                                shape = RoundedCornerShape(10.dp)
                                            ) {
                                                Text("预约夜间安装")
                                            }
                                            Button(
                                                onClick = { showSafetyDialog = true },
                                                modifier = Modifier.weight(1f),
                                                shape = RoundedCornerShape(10.dp),
                                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                                            ) {
                                                Text("即刻开始升级")
                                            }
                                        }
                                    }
                                    OtaStatus.INSTALLING -> {
                                        Surface(
                                            shape = RoundedCornerShape(10.dp),
                                            color = MaterialTheme.statusWarn.copy(alpha = 0.12f),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text(
                                                text = "车机固件正在刷写升级中，全车高压下电，请勿移动车辆或上下车操作。",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.statusWarn,
                                                modifier = Modifier.padding(10.dp)
                                            )
                                        }
                                    }
                                    else -> {
                                        OutlinedButton(
                                            onClick = onCheck,
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(10.dp)
                                        ) {
                                            Text("重新检查")
                                        }
                                    }
                                }
                            } else {
                                // Up-to-date state
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    if (data.releaseNotes.isNotBlank()) {
                                        var notesExpanded by rememberSaveable { mutableStateOf(false) }
                                        val notesArrowRotation by animateFloatAsState(
                                            targetValue = if (notesExpanded) 90f else 0f,
                                            animationSpec = tween(200),
                                            label = "notesCaretRotation"
                                        )
                                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .clickable { notesExpanded = !notesExpanded }
                                                    .padding(vertical = 4.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                                    Text(
                                                        text = "当前版本功能与优化说明",
                                                        style = MaterialTheme.typography.labelMedium,
                                                        fontWeight = FontWeight.SemiBold
                                                    )
                                                    if (data.updateTime.isNotBlank()) {
                                                        Text(
                                                            text = "更新时间：${data.updateTime}",
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                                        )
                                                    }
                                                }
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                ) {
                                                    Text(
                                                        text = if (notesExpanded) "收起" else "查看",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.primary
                                                    )
                                                    Icon(
                                                        painter = painterResource(R.drawable.ic_phosphor_caret_right),
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier
                                                            .size(14.dp)
                                                            .rotate(notesArrowRotation)
                                                    )
                                                }
                                            }
                                            AnimatedVisibility(visible = notesExpanded) {
                                                Surface(
                                                    shape = RoundedCornerShape(10.dp),
                                                    color = MaterialTheme.glassInsetSurface,
                                                    modifier = Modifier.fillMaxWidth()
                                                ) {
                                                    Text(
                                                        text = data.releaseNotes,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        modifier = Modifier.padding(10.dp)
                                                    )
                                                }
                                            }
                                        }
                                    } else if (data.updateTime.isNotBlank()) {
                                        Text(
                                            text = "系统更新时间：${data.updateTime}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(start = 2.dp)
                                        )
                                    }

                                    OutlinedButton(
                                        onClick = onCheck,
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Text("检查更新")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

    // Safety Install Confirmation Dialog
    if (showSafetyDialog && info != null) {
        FotaSafetyConfirmDialog(
            targetVersion = info.newVersion.ifBlank { "新版本" },
            onDismiss = { showSafetyDialog = false },
            onConfirm = { pin ->
                showSafetyDialog = false
                onInstall(info.taskId, pin)
            }
        )
    }

    // Schedule Install Dialog
    if (showScheduleDialog && info != null) {
        FotaScheduleTimeDialog(
            onDismiss = { showScheduleDialog = false },
            onConfirm = { time, pin ->
                showScheduleDialog = false
                onSchedule(info.taskId, time, pin)
            }
        )
    }
}

@Composable
private fun FotaSafetyConfirmDialog(
    targetVersion: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var pin by remember { mutableStateOf("") }
    var countdown by remember { mutableIntStateOf(3) }

    LaunchedEffect(Unit) {
        while (countdown > 0) {
            delay(1000)
            countdown--
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = solidDialogModifier(),
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        shape = RoundedCornerShape(20.dp),
        title = {
            Text(
                text = "整车升级安全确认",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.10f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "⚠ 升级过程全车电脑将重启且断电，耗时约 30~60 分钟，期间车辆无法启动、开门或驾驶。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Text(
                    text = "请确认满足以下条件：\n• 车辆处于 P 挡驻车闭锁状态\n• 动力电池电量大于 25%\n• 车内无人或宠物且无需立即用车",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = pin,
                    onValueChange = { if (it.length <= 4 && it.all { c -> c.isDigit() }) pin = it },
                    label = { Text("4 位操控密码") },
                    placeholder = { Text("请输入4位控车密码") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                enabled = countdown == 0 && pin.length == 4,
                onClick = { onConfirm(pin) },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Text(if (countdown > 0) "确认升级 (${countdown}s)" else "确认开始升级")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

@Composable
private fun FotaScheduleTimeDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit
) {
    var selectedOption by remember { mutableStateOf("02:00:00") }
    var pin by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = solidDialogModifier(),
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        shape = RoundedCornerShape(20.dp),
        title = {
            Text(
                text = "预约夜间升级",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "推荐选择夜间用车低谷期（车辆驻车且已充电完成时段），系统将在指定时间自动执行固件安装：",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val times = listOf("02:00:00" to "凌晨 02:00", "03:00:00" to "凌晨 03:00", "04:00:00" to "凌晨 04:00")
                    times.forEach { (t, label) ->
                        val isSel = selectedOption == t
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { selectedOption = t },
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSel) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else MaterialTheme.glassInsetSurface,
                            border = BorderStroke(
                                1.dp,
                                if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                            )
                        ) {
                            Box(
                                modifier = Modifier.padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = pin,
                    onValueChange = { if (it.length <= 4 && it.all { c -> c.isDigit() }) pin = it },
                    label = { Text("4 位操控密码") },
                    placeholder = { Text("请输入4位控车密码") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                enabled = pin.length == 4,
                onClick = { onConfirm(selectedOption, pin) }
            ) {
                Text("确定预约")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}
