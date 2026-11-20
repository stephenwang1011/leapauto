package com.leapauto.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.leapauto.app.R
import com.leapauto.app.bluetooth.BleKeyCertificate
import com.leapauto.app.bluetooth.CallCarController
import com.leapauto.app.bluetooth.CallCarState
import com.leapauto.app.bluetooth.StraightMoveDirection
import com.leapauto.app.ui.theme.statusGood
import com.leapauto.app.ui.theme.statusWarn

/**
 * 直进直出（遥控挪车）微晶安全控制抽屉。
 *
 * 遵循智能座舱微晶规范与人车控制安全红线：
 * 1. 采用高对比度实色背景，防透光防视线干扰；
 * 2. 独立指示座舱状态（准备中/已就绪/行进中/避障暂停/异常熔断）；
 * 3. 严格遵循「按住持续挪车、松手即刹」手势模型；
 * 4. 显式红色紧急制动按钮，确保任何时刻单手一触即停。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CallCarBottomSheet(
    vin: String,
    certificate: BleKeyCertificate?,
    accountId: String,
    deviceId: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var currentState by remember { mutableStateOf<CallCarState>(CallCarState.Idle) }

    val controller = remember {
        CallCarController(context) { state ->
            currentState = state
        }
    }

    LaunchedEffect(vin, certificate) {
        if (certificate != null && vin.isNotBlank()) {
            controller.prepare(vin, certificate, accountId, deviceId)
        } else {
            currentState = CallCarState.Failed("未找到当前车辆的蓝牙钥匙证书，请先在设置中同步")
        }
    }

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_PAUSE ||
                event == androidx.lifecycle.Lifecycle.Event.ON_STOP
            ) {
                controller.setApplicationActive(false)
            } else if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                controller.setApplicationActive(true)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            controller.close()
        }
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = {
            controller.close()
            onDismiss()
        },
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. 顶部关闭按键 (无冗余重复标题)
            Box(
                modifier = Modifier.fillMaxWidth()
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier
                        .size(32.dp)
                        .align(Alignment.CenterEnd)
                        .clip(CircleShape)
                        .clickable {
                            controller.close()
                            onDismiss()
                        }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            painter = painterResource(R.drawable.ic_phosphor_x),
                            contentDescription = "关闭",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            // 2. 状态指示主舱
            CallCarStatusHeaderCard(
                state = currentState,
                onResume = { controller.resumeMove() },
                onRetry = {
                    if (certificate != null && vin.isNotBlank()) {
                        controller.prepare(vin, certificate, accountId, deviceId)
                    }
                }
            )

            // 3. 双向按住挪车跑道槽
            val canOperate = currentState == CallCarState.Ready || currentState is CallCarState.Moving
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                PressAndHoldMoveButton(
                    direction = StraightMoveDirection.FORWARD,
                    enabled = canOperate,
                    isCurrentMoving = (currentState as? CallCarState.Moving)?.direction == StraightMoveDirection.FORWARD,
                    onPressStart = { controller.startMove(StraightMoveDirection.FORWARD) },
                    onPressEnd = { controller.stopMove() },
                    modifier = Modifier.weight(1f)
                )

                PressAndHoldMoveButton(
                    direction = StraightMoveDirection.BACKWARD,
                    enabled = canOperate,
                    isCurrentMoving = (currentState as? CallCarState.Moving)?.direction == StraightMoveDirection.BACKWARD,
                    onPressStart = { controller.startMove(StraightMoveDirection.BACKWARD) },
                    onPressEnd = { controller.stopMove() },
                    modifier = Modifier.weight(1f)
                )
            }

            // 5. 紧急制动 / 刹停按键
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.error.copy(alpha = 0.12f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.45f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .clickable {
                        controller.stopMove()
                    }
            ) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_phosphor_warning),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "紧急制动 / 刹停",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            // 底部安全准则提示
            Text(
                text = "安全须知：请在视线良好的平坦路面使用，保持与车辆在 5 米安全视线内；检测到行人和障碍物时请立即松手刹停。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                textAlign = TextAlign.Center,
                lineHeight = 16.sp,
                modifier = Modifier.padding(bottom = 12.dp)
            )
        }
    }
}

@Composable
private fun CallCarStatusHeaderCard(
    state: CallCarState,
    onResume: () -> Unit,
    onRetry: () -> Unit
) {
    val statusColor = when (state) {
        CallCarState.Ready, is CallCarState.Moving -> MaterialTheme.statusGood
        CallCarState.Idle, CallCarState.Scanning, is CallCarState.Connecting, CallCarState.Preparing -> MaterialTheme.colorScheme.primary
        is CallCarState.Paused -> MaterialTheme.statusWarn
        is CallCarState.Failed -> MaterialTheme.colorScheme.error
    }

    val statusTitle = when (state) {
        CallCarState.Idle -> "准备中"
        CallCarState.Scanning -> "正在扫描车辆座舱蓝牙..."
        is CallCarState.Connecting -> "已发现车辆，建立握手中..."
        CallCarState.Preparing -> "正在完成座舱安全自检..."
        CallCarState.Ready -> "车辆就绪 · 按住按键开始挪车"
        is CallCarState.Moving -> "${state.direction.label}中 · 松手即刹"
        is CallCarState.Paused -> "挪车已暂停 ${if (state.reason.isNotBlank()) "(${state.reason})" else ""}"
        is CallCarState.Failed -> "连接中断"
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = statusColor.copy(alpha = 0.08f),
        border = BorderStroke(1.dp, statusColor.copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (state == CallCarState.Scanning || state is CallCarState.Connecting || state == CallCarState.Preparing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = statusColor
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(statusColor, CircleShape)
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = statusTitle,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = statusColor
                    )
                    if (state is CallCarState.Failed) {
                        Text(
                            text = state.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            if (state is CallCarState.Paused) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.statusWarn.copy(alpha = 0.20f),
                    border = BorderStroke(1.dp, MaterialTheme.statusWarn.copy(alpha = 0.60f)),
                    modifier = Modifier.clickable { onResume() }
                ) {
                    Text(
                        text = "恢复就绪",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.statusWarn
                    )
                }
            } else if (state is CallCarState.Failed) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.20f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.60f)),
                    modifier = Modifier.clickable { onRetry() }
                ) {
                    Text(
                        text = "重新连接",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

/**
 * 严格按照按住持续触发、松手/取消即刹停的手势按键。
 */
@Composable
private fun PressAndHoldMoveButton(
    direction: StraightMoveDirection,
    enabled: Boolean,
    isCurrentMoving: Boolean,
    onPressStart: () -> Unit,
    onPressEnd: () -> Unit,
    modifier: Modifier = Modifier
) {
    val baseColor = if (direction == StraightMoveDirection.FORWARD) {
        MaterialTheme.statusGood
    } else {
        MaterialTheme.colorScheme.primary
    }

    val containerColor = when {
        isCurrentMoving -> baseColor
        enabled -> baseColor.copy(alpha = 0.15f)
        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
    }

    val contentColor = when {
        isCurrentMoving -> Color.White
        enabled -> baseColor
        else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
    }

    val border = if (enabled) {
        BorderStroke(1.2.dp, baseColor.copy(alpha = if (isCurrentMoving) 0.9f else 0.5f))
    } else {
        glassCardBorder()
    }

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = containerColor,
        border = border,
        modifier = modifier
            .height(84.dp)
            .clip(RoundedCornerShape(18.dp))
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    onPressStart()
                    waitForUpOrCancellation()
                    onPressEnd()
                }
            }
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = if (direction == StraightMoveDirection.FORWARD) "▲" else "▼",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = contentColor
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = direction.label,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = contentColor
            )
            Text(
                text = "按住行驶 · 松手即刹",
                style = MaterialTheme.typography.labelSmall,
                fontSize = 10.sp,
                color = contentColor.copy(alpha = 0.8f)
            )
        }
    }
}
