package com.leapauto.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.leapauto.app.R
import com.leapauto.app.bluetooth.BatteryOptimizationHelper
import com.leapauto.app.bluetooth.BleAccessPolicy
import com.leapauto.app.bluetooth.BleCalibration
import com.leapauto.app.bluetooth.BleCloudSaveStatus
import com.leapauto.app.bluetooth.BleCloudSyncState
import com.leapauto.app.bluetooth.BleConnectionPhase
import com.leapauto.app.bluetooth.BleConnectionState
import com.leapauto.app.bluetooth.BleLockAction
import com.leapauto.app.bluetooth.BleNearbyDevice
import com.leapauto.app.bluetooth.BlePassiveConfiguration
import com.leapauto.app.bluetooth.BleVehicleMetadata
import com.leapauto.app.ui.theme.LocalAppDarkTheme
import com.leapauto.app.ui.theme.statusGood

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
    onControl: (BleLockAction) -> Unit = {},
    onOpenSettings: () -> Unit,
    onCopyDiagnostics: () -> Unit = {},
    onShareDiagnostics: () -> Unit = {},
    onClearDiagnostics: () -> Unit = {},
    onDismiss: () -> Unit,
    metadata: BleVehicleMetadata? = null,
    metadataLoading: Boolean = false,
    metadataMessage: String = "",
    cloudState: BleCloudSyncState = BleCloudSyncState(),
    onRetryCloudSync: () -> Unit = {},
    calibration: BleCalibration = BleCalibration.DEFAULT,
    calibrationApplied: Boolean = false,
    calibrationPending: Boolean = false,
    onSaveCalibration: (BleCalibration?) -> Unit = {},
    carType: String? = null
) {
    val context = LocalContext.current
    val isBatteryIgnored = remember { BatteryOptimizationHelper.isIgnoringBatteryOptimizations(context) }
    val manualConnectionAllowed = !backgroundRunning && !configuration.enabled
    var devicesExpanded by rememberSaveable { mutableStateOf(false) }

    BackHandler(onBack = onDismiss)

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            BluetoothDialogHeader(
                onDismiss = onDismiss
            )
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                    item {
                        Column(Modifier.fillMaxWidth().widthIn(max = 640.dp)) {
                            BluetoothConnectionHeroCard(
                                state = state,
                                certificateReady = certificateReady,
                                certificateLoading = certificateLoading,
                                backgroundRunning = backgroundRunning,
                                backgroundEnabled = configuration.enabled,
                                reconnectDevice = reconnectDevice,
                                metadata = metadata,
                                devicesExpanded = devicesExpanded,
                                onExpandedChange = { devicesExpanded = it },
                                canConnect = manualConnectionAllowed && BleAccessPolicy.canConnect(state.phase, certificateLoading, certificateReady),
                                onSyncCertificate = onSyncCertificate,
                                onDisconnect = onDisconnect,
                                onResumeBackground = onResumeBackground,
                                onConnect = onConnect,
                                onScan = onScan,
                                onControl = onControl
                            )
                        }
                    }
                    if (!BluetoothKeyPresentation.setupComplete(
                            permissionsGranted = permissionsGranted,
                            certificateReady = certificateReady,
                            bound = bound
                        )
                    ) {
                        item {
                            BluetoothSetupChecklist(
                                permissionsGranted = permissionsGranted,
                                certificateReady = certificateReady,
                                certificateLoading = certificateLoading,
                                bound = bound,
                                certificateMessage = certificateMessage,
                                onOpenSettings = onOpenSettings,
                                onSyncCertificate = onSyncCertificate,
                                onScan = onScan,
                                modifier = Modifier.fillMaxWidth().widthIn(max = 640.dp)
                            )
                        }
                    }
                    item {
                        Surface(
                            modifier = Modifier.fillMaxWidth().widthIn(max = 640.dp).frostedGlassCard(RoundedCornerShape(16.dp)),
                            shape = RoundedCornerShape(16.dp),
                            color = androidx.compose.ui.graphics.Color.Transparent,
                            border = glassCardBorder(),
                            shadowElevation = 0.dp
                        ) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
                                    onResumeBackground = onResumeBackground,
                                    carType = carType
                                )
                            }
                        }
                    }
                    if (!isBatteryIgnored && configuration.enabled) {
                        item {
                            BluetoothBatteryOptimizationNotice(
                                onOpen = { BatteryOptimizationHelper.requestIgnoreBatteryOptimization(context) },
                                modifier = Modifier.fillMaxWidth().widthIn(max = 640.dp)
                            )
                        }
                    }
                }
            }
        }
    }

@Composable
private fun BluetoothSetupChecklist(
    permissionsGranted: Boolean,
    certificateReady: Boolean,
    certificateLoading: Boolean,
    bound: Boolean,
    certificateMessage: String,
    onOpenSettings: () -> Unit,
    onSyncCertificate: () -> Unit,
    onScan: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.frostedGlassCard(RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        color = androidx.compose.ui.graphics.Color.Transparent,
        border = glassCardBorder(),
        shadowElevation = 0.dp
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("开始使用", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            BluetoothSetupRow("附近设备权限", permissionsGranted, if (permissionsGranted) null else "去开启", onOpenSettings)
            BluetoothSetupRow(
                "数字钥匙凭证",
                certificateReady,
                if (certificateReady || certificateLoading) null else "同步",
                onSyncCertificate,
                certificateMessage.ifBlank { if (certificateLoading) "正在同步" else null }
            )
            BluetoothSetupRow("连接并绑定当前车辆", bound, if (bound) null else "连接", onScan)
        }
    }
}

@Composable
private fun BluetoothSetupRow(
    label: String,
    complete: Boolean,
    actionLabel: String?,
    onAction: () -> Unit,
    detail: String? = null
) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Surface(
            shape = CircleShape,
            color = if (complete) MaterialTheme.statusGood.copy(alpha = 0.12f)
            else MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.size(26.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    painterResource(if (complete) R.drawable.ic_phosphor_check else R.drawable.ic_phosphor_info_circle),
                    contentDescription = null,
                    tint = if (complete) MaterialTheme.statusGood else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(5.dp)
                )
            }
        }
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            detail?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        actionLabel?.let { TextButton(onClick = onAction) { Text(it) } }
    }
}

@Composable
private fun BluetoothBatteryOptimizationNotice(onOpen: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = BorderStroke(0.6.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_phosphor_info_circle),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "后台运行可能受限，建议将电池设为无限制",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            TextButton(
                onClick = onOpen,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Text("去开启", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            }
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
private fun BluetoothDialogHeader(onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onDismiss, modifier = Modifier.size(48.dp)) {
            Icon(painterResource(R.drawable.ic_phosphor_arrow_left), contentDescription = "返回")
        }
        Text(
            "蓝牙数字钥匙",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.size(48.dp))
    }
}

/**
 * 智能座舱级微晶连接状态与一键主控卡片
 * 直观呈现连接状态、呼吸灯、设备名、一键连接/断开与安全凭证状态。
 */
@Composable
private fun BluetoothConnectionHeroCard(
    state: BleConnectionState,
    certificateReady: Boolean,
    certificateLoading: Boolean,
    backgroundRunning: Boolean,
    backgroundEnabled: Boolean,
    reconnectDevice: BleNearbyDevice?,
    metadata: BleVehicleMetadata? = null,
    devicesExpanded: Boolean = false,
    onExpandedChange: (Boolean) -> Unit = {},
    canConnect: Boolean,
    onSyncCertificate: () -> Unit,
    onDisconnect: () -> Unit,
    onResumeBackground: () -> Unit,
    onConnect: (BleNearbyDevice) -> Unit,
    onScan: () -> Unit,
    onControl: (BleLockAction) -> Unit = {}
) {
    val isConnected = state.phase == BleConnectionPhase.READY || state.phase == BleConnectionPhase.SENDING
    val isConnecting = state.isBusy || state.phase == BleConnectionPhase.SCANNING
    val isFailed = state.phase == BleConnectionPhase.FAILED

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 第 1 行：大号状态图标 + 状态文字 + 主控按钮与切换设备
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 呼吸状态徽标
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                isConnected -> MaterialTheme.statusGood.copy(alpha = 0.14f)
                                isConnecting -> MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                isFailed -> MaterialTheme.colorScheme.error.copy(alpha = 0.12f)
                                else -> MaterialTheme.colorScheme.surfaceVariant
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (isConnecting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            strokeWidth = 2.2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        Icon(
                            painter = painterResource(R.drawable.ic_bluetooth_key_hero),
                            contentDescription = null,
                            tint = when {
                                isConnected -> MaterialTheme.statusGood
                                isFailed -> MaterialTheme.colorScheme.error
                                else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            },
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                // 车辆名称与状态主说明
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    val targetVehicleName = state.deviceName.ifBlank {
                        reconnectDevice?.name?.ifBlank { null } ?: "零跑车辆"
                    }
                    Text(
                        text = targetVehicleName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Text(
                        text = when {
                            isConnected -> "已就绪 · 待命模式中"
                            isConnecting -> state.detailMessage ?: "正在进行安全握手..."
                            isFailed -> state.detailMessage ?: "未在车辆附近或连接超时"
                            backgroundRunning -> "待命模式 · 靠近车辆自动感应"
                            else -> "未连接 · 点击连接车辆"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = when {
                            isConnected -> MaterialTheme.statusGood
                            isConnecting -> MaterialTheme.colorScheme.primary
                            isFailed -> MaterialTheme.colorScheme.error
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // 主控快捷按钮与切换设备入口
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    when {
                        isConnected -> {
                            OutlinedButton(
                                onClick = onDisconnect,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.height(32.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                            ) {
                                Text("断开", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                        isConnecting -> {
                            OutlinedButton(
                                onClick = onDisconnect,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.height(32.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                            ) {
                                Text("取消", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                        else -> {
                            FilledTonalButton(
                                onClick = {
                                    if (reconnectDevice != null && canConnect) {
                                        onConnect(reconnectDevice)
                                    } else if (backgroundEnabled) {
                                        onResumeBackground()
                                    } else {
                                        onScan()
                                    }
                                },
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.height(32.dp),
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                    contentColor = MaterialTheme.colorScheme.primary
                                ),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                            ) {
                                Text(
                                    text = if (backgroundEnabled || reconnectDevice != null) "重连" else "连接",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }

                    Text(
                        text = if (devicesExpanded) "收起设备 ▴" else "切换设备 ▾",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .clickable { onExpandedChange(!devicesExpanded) }
                            .padding(vertical = 2.dp)
                    )
                }
            }

            // 展开附近车辆与扫描设备列表
            if (devicesExpanded) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
                BluetoothNearbyVehicles(
                    state = state,
                    certificateLoading = certificateLoading,
                    backgroundRunning = backgroundRunning,
                    backgroundEnabled = backgroundEnabled,
                    reconnectDevice = reconnectDevice,
                    canConnect = canConnect,
                    metadata = metadata,
                    onScan = onScan,
                    onDisconnect = onDisconnect,
                    onResumeBackground = onResumeBackground,
                    onConnect = onConnect
                )
            }

            // 蓝牙一键控锁快捷面板 (就绪待命或发送中时呈现)
            val canOperate = state.canControl
            val isSending = state.phase == BleConnectionPhase.SENDING
            val isUnlocking = isSending && state.pendingAction == BleLockAction.UNLOCK
            val isLocking = isSending && state.pendingAction == BleLockAction.LOCK
            if (canOperate || isSending) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    FilledTonalButton(
                        onClick = { onControl(BleLockAction.UNLOCK) },
                        enabled = canOperate,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f).height(40.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                            contentColor = MaterialTheme.colorScheme.primary
                        ),
                        contentPadding = PaddingValues(horizontal = 8.dp)
                    ) {
                        if (isUnlocking) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(6.dp))
                            Text("解锁中...", style = MaterialTheme.typography.labelMedium)
                        } else {
                            Icon(painterResource(R.drawable.ic_phosphor_lock_open), contentDescription = null, modifier = Modifier.size(17.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("蓝牙解锁", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    FilledTonalButton(
                        onClick = { onControl(BleLockAction.LOCK) },
                        enabled = canOperate,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f).height(40.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                            contentColor = MaterialTheme.colorScheme.primary
                        ),
                        contentPadding = PaddingValues(horizontal = 8.dp)
                    ) {
                        if (isLocking) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(6.dp))
                            Text("上锁中...", style = MaterialTheme.typography.labelMedium)
                        } else {
                            Icon(painterResource(R.drawable.ic_phosphor_lock), contentDescription = null, modifier = Modifier.size(17.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("蓝牙上锁", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            // 安全凭证异常或同步中时展示提示条 (正常状态保持静默不打扰)
            if (BluetoothKeyPresentation.shouldShowCertificateAlert(certificateReady, certificateLoading)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.45f))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(if (certificateLoading) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                        )
                        Text(
                            text = if (certificateLoading) "数字钥匙安全凭证同步中..." else "数字钥匙安全凭证未同步",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (certificateLoading) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                        )
                    }

                    TextButton(
                        onClick = onSyncCertificate,
                        enabled = !backgroundRunning && !certificateLoading,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        if (certificateLoading) {
                            CircularProgressIndicator(Modifier.size(12.dp), strokeWidth = 1.6.dp)
                            Spacer(Modifier.width(4.dp))
                        }
                        Text(if (certificateLoading) "同步中" else "立即同步", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

internal object BluetoothKeyPresentation {
    fun setupComplete(permissionsGranted: Boolean, certificateReady: Boolean, bound: Boolean): Boolean =
        permissionsGranted && certificateReady && bound

    fun shouldShowCertificateAlert(certificateReady: Boolean, certificateLoading: Boolean): Boolean =
        !certificateReady || certificateLoading

    fun shouldShowWarningOnToggle(target: Boolean): Boolean = target

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

    fun maskBluetoothAddress(address: String): String {
        val parts = address.split(':')
        return if (parts.size == 6) "**:**:**:${parts[3]}:${parts[4]}:${parts[5]}" else "已连接设备"
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
            Text(BluetoothKeyPresentation.maskBluetoothAddress(device.address), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(BluetoothKeyPresentation.candidateLabel(matchesTarget), style = MaterialTheme.typography.labelSmall,
                color = if (matchesTarget) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            Text("信号 ${device.rssi} dBm", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(painterResource(R.drawable.ic_phosphor_caret_right), contentDescription = "连接车辆", modifier = Modifier.size(18.dp))
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
