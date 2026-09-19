package com.leapauto.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.leapauto.app.R
import com.leapauto.app.bluetooth.BleAccessPolicy
import com.leapauto.app.bluetooth.BleCalibration
import com.leapauto.app.bluetooth.BleCloudSaveStatus
import com.leapauto.app.bluetooth.BleCloudSyncState
import com.leapauto.app.bluetooth.BleConnectionPhase
import com.leapauto.app.bluetooth.BleConnectionState
import com.leapauto.app.bluetooth.BleDiagnosticEntry
import com.leapauto.app.bluetooth.BleLockAction
import com.leapauto.app.bluetooth.BleNearbyDevice
import com.leapauto.app.bluetooth.BlePassiveConfiguration
import com.leapauto.app.bluetooth.BleVehicleMetadata
import com.leapauto.app.ui.theme.statusGood
import java.text.DateFormat
import java.util.Date

@Composable
internal fun BluetoothKeyEntry(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            painterResource(R.drawable.ic_phosphor_key),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )
        Text(
            "蓝牙数字钥匙",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f)
        )
        Icon(
            painterResource(R.drawable.ic_phosphor_caret_right),
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun BluetoothKeyDialog(
    state: BleConnectionState,
    permissionsGranted: Boolean,
    certificateReady: Boolean,
    certificateLoading: Boolean,
    certificateMessage: String,
    reconnectDevice: BleNearbyDevice?,
    configuration: BlePassiveConfiguration,
    appliedConfiguration: BlePassiveConfiguration?,
    configurationRequested: Boolean,
    configurationPending: Boolean,
    backgroundRunning: Boolean,
    bound: Boolean,
    protocolMinor: Int?,
    onApplyConfiguration: (BlePassiveConfiguration) -> Unit,
    onResumeBackground: () -> Unit,
    onSyncCertificate: () -> Unit,
    onScan: () -> Unit,
    onConnect: (BleNearbyDevice) -> Unit,
    onDisconnect: () -> Unit,
    onControl: (BleLockAction) -> Unit,
    onOpenSettings: () -> Unit,
    onCopyDiagnostics: () -> Unit,
    onShareDiagnostics: () -> Unit,
    onClearDiagnostics: () -> Unit,
    onDismiss: () -> Unit,
    metadata: BleVehicleMetadata? = null,
    metadataLoading: Boolean = false,
    metadataMessage: String = "",
    cloudState: BleCloudSyncState = BleCloudSyncState(),
    onRetryCloudSync: () -> Unit = {},
    calibration: BleCalibration = BleCalibration.DEFAULT,
    calibrationApplied: Boolean = false,
    calibrationPending: Boolean = false,
    onSaveCalibration: (BleCalibration?) -> Unit = {}
) {
    val screenConfiguration = LocalConfiguration.current
    val dialogWidth = (screenConfiguration.screenWidthDp * 0.92f).dp.coerceAtMost(560.dp)
    val dialogHeight = (screenConfiguration.screenHeightDp * 0.88f).dp
    val manualConnectionAllowed = !backgroundRunning && !configuration.enabled

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier
                .width(dialogWidth)
                .heightIn(max = dialogHeight)
                .then(solidDialogModifier(RoundedCornerShape(20.dp))),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp
        ) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                BluetoothDialogHeader(onDismiss)
                Column(
                    Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    BluetoothCertificateStatus(
                        state, certificateReady, certificateLoading, certificateMessage, backgroundRunning, onSyncCertificate
                    )
                    BluetoothMetadataStatus(metadata, metadataLoading, metadataMessage)
                    HorizontalDivider()
                    BluetoothConnectionStatus(state)
                    HorizontalDivider()
                    BluetoothNearbyVehicles(
                        state = state,
                        certificateLoading = certificateLoading,
                        backgroundRunning = backgroundRunning,
                        backgroundEnabled = configuration.enabled,
                        reconnectDevice = reconnectDevice,
                        canConnect = manualConnectionAllowed && BleAccessPolicy.canConnect(state.phase, certificateLoading, certificateReady),
                        metadata = metadata,
                        onScan = onScan,
                        onDisconnect = onDisconnect,
                        onResumeBackground = onResumeBackground,
                        onConnect = onConnect
                    )
                    HorizontalDivider()
                    BluetoothPassiveSettings(
                        configuration = configuration,
                        appliedConfiguration = appliedConfiguration,
                        configurationRequested = configurationRequested,
                        configurationPending = configurationPending,
                        backgroundRunning = backgroundRunning,
                        bound = bound,
                        protocolMinor = protocolMinor,
                        busy = state.isBusy || state.phase == BleConnectionPhase.SCANNING || certificateLoading,
                        onApplyConfiguration = onApplyConfiguration,
                        onResumeBackground = onResumeBackground
                    )
                    BluetoothCloudStatus(cloudState, onRetryCloudSync)
                    TextButton(onClick = onOpenSettings, modifier = Modifier.align(Alignment.End)) {
                        Text(if (permissionsGranted) "系统权限设置" else "开启附近设备权限")
                    }
                    HorizontalDivider()
                    BluetoothManualControlSection(state, onControl)
                    HorizontalDivider()
                    BluetoothCalibrationEditor(
                        calibration = calibration,
                        applied = calibrationApplied,
                        pending = calibrationPending,
                        busy = state.isBusy || state.phase == BleConnectionPhase.SCANNING || certificateLoading ||
                            cloudState.calibration == BleCloudSaveStatus.SAVING,
                        onSave = onSaveCalibration
                    )
                    HorizontalDivider()
                    BluetoothDiagnosticsSection(state, onCopyDiagnostics, onShareDiagnostics, onClearDiagnostics)
                }
            }
        }
    }
}

@Composable
private fun BluetoothManualControlSection(state: BleConnectionState, onAction: (BleLockAction) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { expanded = !expanded },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("手动锁控测试", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Text(if (expanded) "收起" else "展开", style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (expanded) {
            Text("用于验证蓝牙连接和车辆回执，不影响靠近自动解锁、远离自动锁车设置。",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            BluetoothLockControls(state, onAction)
        }
    }
}

@Composable
private fun BluetoothNearbyVehicles(
    state: BleConnectionState,
    certificateLoading: Boolean,
    backgroundRunning: Boolean,
    backgroundEnabled: Boolean,
    reconnectDevice: BleNearbyDevice?,
    canConnect: Boolean,
    metadata: BleVehicleMetadata?,
    onScan: () -> Unit,
    onDisconnect: () -> Unit,
    onResumeBackground: () -> Unit,
    onConnect: (BleNearbyDevice) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("附近车辆", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            TextButton(onClick = onScan, enabled = !backgroundRunning && BleAccessPolicy.canScan(state.phase, certificateLoading)) {
                Text("重新扫描")
            }
        }
        BluetoothConnectionActions(state, certificateLoading, backgroundRunning, backgroundEnabled,
            onScan, onDisconnect, onResumeBackground)
        reconnectDevice?.takeIf { previous ->
            state.devices.any { it.address == previous.address } &&
                state.phase in setOf(BleConnectionPhase.IDLE, BleConnectionPhase.FAILED)
        }?.let { device ->
            OutlinedButton(
                onClick = { onConnect(device) },
                enabled = canConnect,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
            ) {
                Icon(painterResource(R.drawable.ic_phosphor_arrow_clockwise), null, Modifier.size(18.dp))
                Text("重新连接 ${device.name.ifBlank { "车辆" }}", Modifier.padding(start = 8.dp), maxLines = 1,
                    overflow = TextOverflow.Ellipsis)
            }
        }
        BluetoothDeviceList(state, canConnect, metadata, onConnect)
    }
}

@Composable
private fun BluetoothDialogHeader(onDismiss: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("蓝牙数字钥匙", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        }
        IconButton(onClick = onDismiss, modifier = Modifier.size(48.dp)) {
            Icon(painterResource(R.drawable.ic_phosphor_x), contentDescription = "收起蓝牙钥匙")
        }
    }
}

@Composable
private fun BluetoothCertificateStatus(
    state: BleConnectionState,
    ready: Boolean,
    loading: Boolean,
    message: String,
    backgroundRunning: Boolean,
    onSync: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("车辆钥匙", style = MaterialTheme.typography.titleSmall)
        Text(
            message.ifBlank {
                when {
                    loading -> "正在同步钥匙"
                    ready -> "钥匙已就绪"
                    else -> "尚未同步钥匙"
                }
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        OutlinedButton(
            onClick = onSync,
            enabled = !backgroundRunning && BleAccessPolicy.canSyncCertificate(state.phase, loading),
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
        ) {
            if (loading) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            } else {
                Icon(painterResource(R.drawable.ic_phosphor_arrow_clockwise), contentDescription = null, modifier = Modifier.size(18.dp))
            }
            Text(if (loading) "同步中" else "同步钥匙", modifier = Modifier.padding(start = 8.dp))
        }
    }
}

@Composable
private fun BluetoothMetadataStatus(metadata: BleVehicleMetadata?, loading: Boolean, message: String) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("车辆蓝牙配置", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            if (loading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
        }
        Text(message.ifBlank {
            when {
                loading -> "正在获取车辆配置"
                metadata != null -> "已获取车辆配置"
                else -> "尚未获取车辆配置"
            }
        }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        metadata?.let {
            if (it.address.isNotBlank()) {
                Text("配置地址 ${maskBluetoothAddress(it.address)}", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (it.controllerVersion.isNotBlank()) {
                Text("控制器版本 ${it.controllerVersion}", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (it.fetchedAtMillis > 0) {
                val fetchedAt = remember(it.fetchedAtMillis) {
                    DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(it.fetchedAtMillis))
                }
                Text("获取时间 $fetchedAt", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun BluetoothCloudStatus(state: BleCloudSyncState, onRetry: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("云端保存", style = MaterialTheme.typography.titleSmall)
        BluetoothCloudStatusRow("自动操作设置", state.configuration)
        BluetoothCloudStatusRow("标定参数", state.calibration)
        if (BluetoothKeyPresentation.canRetryCloud(state)) {
            OutlinedButton(onClick = onRetry, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Icon(painterResource(R.drawable.ic_phosphor_arrow_clockwise), null, Modifier.size(18.dp))
                Text("重试云端保存", Modifier.padding(start = 8.dp))
            }
        }
    }
}

@Composable
private fun BluetoothCloudStatusRow(label: String, status: BleCloudSaveStatus) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        if (status == BleCloudSaveStatus.SAVING) {
            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
        }
        Text(BluetoothKeyPresentation.cloudLabel(status), style = MaterialTheme.typography.bodySmall,
            color = when (status) {
                BleCloudSaveStatus.SAVED -> MaterialTheme.statusGood
                BleCloudSaveStatus.FAILED -> MaterialTheme.colorScheme.error
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            })
    }
}

internal object BluetoothKeyPresentation {
    fun matchesTarget(address: String, targetAddress: String?): Boolean {
        val target = targetAddress?.let(BleVehicleMetadata::normalizeAddress) ?: return false
        return BleVehicleMetadata.normalizeAddress(address) == target
    }

    fun sortedCandidates(devices: List<BleNearbyDevice>, targetAddress: String?): List<BleNearbyDevice> =
        devices.sortedWith(compareByDescending<BleNearbyDevice> { matchesTarget(it.address, targetAddress) }
            .thenByDescending { it.rssi })

    fun candidateLabel(matchesTarget: Boolean): String =
        if (matchesTarget) "与车辆配置一致" else "身份待核对"

    fun cloudLabel(status: BleCloudSaveStatus): String = when (status) {
        BleCloudSaveStatus.UNSAVED -> "未保存"
        BleCloudSaveStatus.PENDING -> "待上传"
        BleCloudSaveStatus.SAVING -> "保存中"
        BleCloudSaveStatus.SAVED -> "已保存"
        BleCloudSaveStatus.FAILED -> "保存失败"
    }

    fun canRetryCloud(state: BleCloudSyncState): Boolean =
        listOf(state.configuration, state.calibration).let { statuses ->
            BleCloudSaveStatus.SAVING !in statuses && statuses.any {
                it == BleCloudSaveStatus.FAILED || it == BleCloudSaveStatus.PENDING
            }
        }
}

@Composable
private fun BluetoothConnectionStatus(state: BleConnectionState) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (state.isBusy || state.phase == BleConnectionPhase.SCANNING) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            }
            Text(
                state.phaseLabel,
                style = MaterialTheme.typography.titleSmall,
                color = when (state.phase) {
                    BleConnectionPhase.READY -> MaterialTheme.statusGood
                    BleConnectionPhase.FAILED -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.onSurface
                }
            )
        }
        if (state.deviceName.isNotBlank()) {
            Text(state.deviceName, style = MaterialTheme.typography.bodyMedium)
        }
        state.detailMessage?.let { message ->
            Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun BluetoothConnectionActions(
    state: BleConnectionState,
    certificateLoading: Boolean,
    backgroundRunning: Boolean,
    backgroundEnabled: Boolean,
    onScan: () -> Unit,
    onDisconnect: () -> Unit,
    onResumeBackground: () -> Unit
) {
    val active = state.phase !in setOf(BleConnectionPhase.IDLE, BleConnectionPhase.FAILED)
    OutlinedButton(
        onClick = when {
            active || backgroundRunning -> onDisconnect
            backgroundEnabled -> onResumeBackground
            else -> onScan
        },
        enabled = active || backgroundRunning || BleAccessPolicy.canScan(state.phase, certificateLoading),
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
    ) {
        Text(
            when {
                backgroundRunning -> "暂停后台连接"
                state.phase == BleConnectionPhase.SCANNING -> "停止扫描"
                state.isBusy && state.phase != BleConnectionPhase.SENDING -> "取消连接"
                active -> "断开连接"
                backgroundEnabled -> "恢复后台连接"
                state.devices.isNotEmpty() || state.phase == BleConnectionPhase.FAILED -> "重新扫描"
                else -> "扫描车辆"
            }
        )
    }
}

@Composable
private fun BluetoothDeviceList(
    state: BleConnectionState,
    canConnect: Boolean,
    metadata: BleVehicleMetadata?,
    onConnect: (BleNearbyDevice) -> Unit
) {
    val selecting = state.phase in setOf(BleConnectionPhase.IDLE, BleConnectionPhase.SCANNING, BleConnectionPhase.FAILED)
    if (!selecting) return
    if (state.devices.isEmpty()) {
        if (state.phase == BleConnectionPhase.SCANNING) {
            Text("暂未发现车辆", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 224.dp)) {
            items(BluetoothKeyPresentation.sortedCandidates(state.devices, metadata?.address), key = { it.address }) { device ->
                BluetoothDeviceRow(device, enabled = canConnect,
                    matchesTarget = BluetoothKeyPresentation.matchesTarget(device.address, metadata?.address),
                    onClick = { onConnect(device) })
            }
        }
    }
}

@Composable
private fun BluetoothDeviceRow(device: BleNearbyDevice, enabled: Boolean, matchesTarget: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onClick).padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                device.name.ifBlank { "未命名车辆" },
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(maskBluetoothAddress(device.address), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(BluetoothKeyPresentation.candidateLabel(matchesTarget), style = MaterialTheme.typography.labelSmall,
                color = if (matchesTarget) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            Text("信号 ${device.rssi} dBm", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(painterResource(R.drawable.ic_phosphor_caret_right), contentDescription = "连接车辆", modifier = Modifier.size(18.dp))
    }
}

private fun maskBluetoothAddress(address: String): String {
    val parts = address.split(':')
    return if (parts.size == 6) "**:**:**:${parts[3]}:${parts[4]}:${parts[5]}" else "已连接设备"
}

@Composable
private fun BluetoothLockControls(state: BleConnectionState, onAction: (BleLockAction) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("车锁", style = MaterialTheme.typography.titleSmall)
        if (state.canControl) {
            state.confirmedAction?.let { action ->
                Text(
                    if (action == BleLockAction.LOCK) "车辆已确认上锁" else "车辆已确认解锁",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.statusGood
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = { onAction(BleLockAction.UNLOCK) },
                enabled = state.canControl,
                modifier = Modifier.weight(1f).heightIn(min = 48.dp)
            ) {
                Icon(painterResource(R.drawable.ic_phosphor_lock_open), contentDescription = null, modifier = Modifier.size(18.dp))
                Text("解锁", Modifier.padding(start = 8.dp))
            }
            Button(
                onClick = { onAction(BleLockAction.LOCK) },
                enabled = state.canControl,
                modifier = Modifier.weight(1f).heightIn(min = 48.dp)
            ) {
                Icon(painterResource(R.drawable.ic_phosphor_lock), contentDescription = null, modifier = Modifier.size(18.dp))
                Text("上锁", Modifier.padding(start = 8.dp))
            }
        }
    }
}

@Composable
private fun BluetoothDiagnosticsSection(
    state: BleConnectionState,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onClear: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { expanded = !expanded },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("连接诊断", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Text("${state.diagnostics.size} 条", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Icon(
                painterResource(R.drawable.ic_phosphor_caret_right),
                contentDescription = if (expanded) "收起诊断" else "展开诊断",
                modifier = Modifier.size(18.dp).rotate(if (expanded) 90f else 0f)
            )
        }
        if (expanded) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onCopy, enabled = state.diagnostics.isNotEmpty(), modifier = Modifier.weight(1f)) {
                    Icon(painterResource(R.drawable.ic_phosphor_copy), null, Modifier.size(16.dp))
                    Text("复制", Modifier.padding(start = 6.dp))
                }
                TextButton(onClick = onShare, enabled = state.diagnostics.isNotEmpty() && !state.isBusy, modifier = Modifier.weight(1f)) {
                    Text("分享")
                }
                TextButton(onClick = onClear, enabled = state.diagnostics.isNotEmpty(), modifier = Modifier.weight(1f)) {
                    Text("清空")
                }
            }
            if (state.diagnostics.isEmpty()) {
                Text("暂无诊断记录", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 280.dp)) {
                    items(state.diagnostics.indices.toList(), key = { index ->
                        "$index:${state.diagnostics[index].elapsedMillis}"
                    }) { index ->
                        BluetoothDiagnosticRow(state.diagnostics[index])
                    }
                }
            }
        }
    }
}

@Composable
private fun BluetoothDiagnosticRow(entry: BleDiagnosticEntry) {
    val millis = entry.elapsedMillis.coerceAtLeast(0)
    val elapsed = "${millis / 1_000}.${(millis % 1_000).toString().padStart(3, '0')} s"
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(elapsed, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(72.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(entry.description, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
fun BluetoothActionConfirmation(
    action: BleLockAction,
    enabled: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    val label = if (action == BleLockAction.LOCK) "上锁" else "解锁"
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = solidDialogModifier(),
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        shape = RoundedCornerShape(24.dp),
        title = { Text("确认蓝牙$label") },
        text = { Text("当前连接车辆将执行$label。") },
        confirmButton = { TextButton(onClick = onConfirm, enabled = enabled) { Text("确认$label") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}
