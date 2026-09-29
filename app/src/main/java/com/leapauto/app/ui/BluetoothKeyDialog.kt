package com.leapauto.app.ui

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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.leapauto.app.R
import com.leapauto.app.bluetooth.BatteryOptimizationHelper
import com.leapauto.app.bluetooth.BleAccessPolicy
import com.leapauto.app.bluetooth.BleCalibration
import com.leapauto.app.bluetooth.BleCloudSaveStatus
import com.leapauto.app.bluetooth.BleCloudSyncState
import com.leapauto.app.bluetooth.BleConnectionPhase
import com.leapauto.app.bluetooth.BleConnectionState
import com.leapauto.app.bluetooth.BleDiagnosticEntry
import com.leapauto.app.bluetooth.BleKeyService
import com.leapauto.app.bluetooth.BleLockAction
import com.leapauto.app.bluetooth.BleNearbyDevice
import com.leapauto.app.bluetooth.BlePassiveConfiguration
import com.leapauto.app.bluetooth.BleVehicleMetadata
import com.leapauto.app.ui.theme.statusGood
import com.leapauto.app.ui.theme.statusWarn
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
    onControl: (BleLockAction) -> Unit = {},
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
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                BluetoothDialogHeader(onDismiss)
                Column(
                    Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // 1. 核心状态与一键连接卡片 (极简智能座舱微晶舱)
                    BluetoothConnectionHeroCard(
                        state = state,
                        certificateReady = certificateReady,
                        certificateLoading = certificateLoading,
                        backgroundRunning = backgroundRunning,
                        backgroundEnabled = configuration.enabled,
                        reconnectDevice = reconnectDevice,
                        canConnect = manualConnectionAllowed && BleAccessPolicy.canConnect(state.phase, certificateLoading, certificateReady),
                        onSyncCertificate = onSyncCertificate,
                        onDisconnect = onDisconnect,
                        onResumeBackground = onResumeBackground,
                        onConnect = onConnect,
                        onScan = onScan,
                        onControl = onControl
                    )

                    // 2. 智能无感钥匙日常开关舱 (微晶卡片)
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
                        }
                    }

                    // 3. 高级设置与排障诊断中心 (默认优雅折叠)
                    var advancedExpanded by rememberSaveable { mutableStateOf(false) }
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.20f),
                        border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { advancedExpanded = !advancedExpanded },
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_settings_gear),
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = "高级设置与排障诊断",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = if (advancedExpanded) "收起" else "展开",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Icon(
                                        painter = painterResource(R.drawable.ic_phosphor_caret_right),
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp).rotate(if (advancedExpanded) 90f else 0f),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            if (advancedExpanded) {
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                                // 搜索附近车辆与设备列表
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
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                                // 车辆控制器硬件配置元数据
                                BluetoothMetadataStatus(metadata, metadataLoading, metadataMessage)
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                                // 感应标定参数精细调优
                                BluetoothCalibrationEditor(
                                    calibration = calibration,
                                    applied = calibrationApplied,
                                    pending = calibrationPending,
                                    busy = state.isBusy || state.phase == BleConnectionPhase.SCANNING || certificateLoading ||
                                        cloudState.calibration == BleCloudSaveStatus.SAVING,
                                    onSave = onSaveCalibration
                                )
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                                // 系统后台防杀与全天候保活状态卡片
                                val context = LocalContext.current
                                val isBatteryIgnored = remember { BatteryOptimizationHelper.isIgnoringBatteryOptimizations(context) }
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                                    border = BorderStroke(0.6.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                            Text(
                                                text = if (isBatteryIgnored) "全天候无感保活：已就绪" else "后台保活受限 (可能被系统查杀)",
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.SemiBold,
                                                color = if (isBatteryIgnored) MaterialTheme.statusGood else MaterialTheme.colorScheme.error
                                            )
                                            Text(
                                                text = if (isBatteryIgnored) "已开启电池无限制，锁屏放兜里依然能稳定拉门开锁" else "建议开启「无限制」与「允许自启动」，杜绝息屏被杀",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        if (!isBatteryIgnored) {
                                            TextButton(onClick = { BatteryOptimizationHelper.requestIgnoreBatteryOptimization(context) }) {
                                                Text("去开启")
                                            }
                                        }
                                    }
                                }
                                val isOfficialAppInstalled = remember(context) {
                                    runCatching { context.packageManager.getPackageInfo("com.dahua.leapmotor", 0) != null }.getOrDefault(false)
                                }
                                if (isOfficialAppInstalled) {
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = MaterialTheme.statusWarn.copy(alpha = 0.10f),
                                        border = BorderStroke(0.6.dp, MaterialTheme.statusWarn.copy(alpha = 0.35f)),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Icon(
                                                painter = painterResource(R.drawable.ic_phosphor_warning),
                                                contentDescription = null,
                                                tint = MaterialTheme.statusWarn,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Text(
                                                text = "检测到本机已安装官方零跑 App。若遇到蓝牙连接频繁断开，建议在官方 App 中关闭其无感钥匙，避免两端争抢同一物理信道。",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                lineHeight = 15.sp
                                            )
                                        }
                                    }
                                }
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                                // 云端设置同步状态
                                BluetoothCloudStatus(cloudState, onRetryCloudSync)
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    TextButton(onClick = { BleKeyService.openNotificationSettings(context) }) {
                                        Text("关闭通知栏提醒", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    TextButton(onClick = onOpenSettings) {
                                        Text(if (permissionsGranted) "系统权限设置" else "开启附近设备权限")
                                    }
                                }
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                                // 连接诊断日志
                                BluetoothDiagnosticsSection(state, onCopyDiagnostics, onShareDiagnostics, onClearDiagnostics)
                            }
                        }
                    }
                }
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
    val active = state.phase !in setOf(BleConnectionPhase.IDLE, BleConnectionPhase.FAILED)

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 第 1 行：大号状态图标 + 状态文字 + 主控按钮
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 呼吸状态徽标
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                isConnected -> MaterialTheme.statusGood.copy(alpha = 0.14f)
                                isConnecting -> MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                state.phase == BleConnectionPhase.FAILED -> MaterialTheme.colorScheme.error.copy(alpha = 0.12f)
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
                                state.phase == BleConnectionPhase.FAILED -> MaterialTheme.colorScheme.error
                                else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            },
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                // 状态主标题与说明
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        text = when {
                            isConnected -> "蓝牙钥匙已就绪"
                            isConnecting -> "正在连接车辆..."
                            state.phase == BleConnectionPhase.FAILED -> "连接未成功"
                            else -> "蓝牙钥匙未连接"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = when {
                            isConnected -> MaterialTheme.statusGood
                            state.phase == BleConnectionPhase.FAILED -> MaterialTheme.colorScheme.error
                            else -> MaterialTheme.colorScheme.onSurface
                        }
                    )
                    Text(
                        text = when {
                            isConnected -> state.deviceName.ifBlank { "已连接并处于待命状态" }
                            isConnecting -> state.detailMessage ?: "正在进行安全握手..."
                            state.phase == BleConnectionPhase.FAILED -> state.detailMessage ?: "请确认车辆在附近并重试"
                            else -> "待命模式 · 靠近车辆自动感应"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // 主控快捷按钮
                if (active || backgroundRunning) {
                    OutlinedButton(
                        onClick = onDisconnect,
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Text("断开", style = MaterialTheme.typography.labelMedium)
                    }
                } else {
                    Button(
                        onClick = {
                            if (reconnectDevice != null && canConnect) {
                                onConnect(reconnectDevice)
                            } else if (backgroundEnabled) {
                                onResumeBackground()
                            } else {
                                onScan()
                            }
                        },
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = if (backgroundEnabled) "恢复连接" else "连接车辆",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            // 蓝牙一键控锁快捷面板 (就绪待命或发送中时呈现)
            val canOperate = state.canControl
            val isSending = state.phase == BleConnectionPhase.SENDING
            val isUnlocking = isSending && state.pendingAction == BleLockAction.UNLOCK
            val isLocking = isSending && state.pendingAction == BleLockAction.LOCK
            if (canOperate || isSending) {
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

            // 第 2 行：安全凭证状态微晶指示条
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_phosphor_key),
                        contentDescription = null,
                        tint = if (certificateReady) MaterialTheme.statusGood else MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        text = if (certificateReady) "数字钥匙安全凭证：有效" else "数字钥匙安全凭证：尚未同步",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium,
                        color = if (certificateReady) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error
                    )
                }

                if (!certificateReady || certificateLoading) {
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

@Composable
private fun BluetoothCertificateStatus(
    state: BleConnectionState,
    ready: Boolean,
    loading: Boolean,
    message: String,
    backgroundRunning: Boolean,
    onSync: () -> Unit
) {
    val canSync = !backgroundRunning && BleAccessPolicy.canSyncCertificate(state.phase, loading)
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
            Icon(
                painter = painterResource(R.drawable.ic_phosphor_key),
                contentDescription = null,
                tint = if (ready) MaterialTheme.statusGood else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
            Column {
                Text(
                    text = "车辆安全凭证",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = message.ifBlank {
                        when {
                            loading -> "正在同步钥匙凭证..."
                            ready -> "安全凭证已就绪"
                            else -> "尚未同步钥匙凭证"
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (ready) MaterialTheme.statusGood else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        OutlinedButton(
            onClick = onSync,
            enabled = canSync,
            shape = RoundedCornerShape(10.dp),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
        ) {
            if (loading) {
                CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 1.8.dp)
                Spacer(Modifier.width(6.dp))
            } else {
                Icon(
                    painter = painterResource(R.drawable.ic_phosphor_arrow_clockwise),
                    contentDescription = null,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(Modifier.width(4.dp))
            }
            Text(if (loading) "同步中" else "同步凭证", style = MaterialTheme.typography.labelSmall)
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
    val isConnected = state.phase == BleConnectionPhase.READY || state.phase == BleConnectionPhase.SENDING
    val isConnecting = state.isBusy || state.phase == BleConnectionPhase.SCANNING

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(
                    when {
                        isConnected -> MaterialTheme.statusGood.copy(alpha = 0.12f)
                        isConnecting -> MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
                        state.phase == BleConnectionPhase.FAILED -> MaterialTheme.colorScheme.error.copy(alpha = 0.12f)
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            if (isConnecting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary
                )
            } else {
                Icon(
                    painter = painterResource(R.drawable.ic_bluetooth_key_hero),
                    contentDescription = null,
                    tint = when {
                        isConnected -> MaterialTheme.statusGood
                        state.phase == BleConnectionPhase.FAILED -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    },
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = if (isConnected) "蓝牙钥匙已就绪" else state.phaseLabel,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = when {
                    isConnected -> MaterialTheme.statusGood
                    state.phase == BleConnectionPhase.FAILED -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.onSurface
                }
            )
            val desc = state.detailMessage ?: state.deviceName.takeIf { it.isNotBlank() }
            if (!desc.isNullOrBlank()) {
                Text(
                    text = desc,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
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
