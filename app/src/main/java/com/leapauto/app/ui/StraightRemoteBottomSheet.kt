package com.leapauto.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.leapauto.app.R
import com.leapauto.app.bluetooth.BleConnectionPhase
import com.leapauto.app.bluetooth.BleStraightAction
import com.leapauto.app.bluetooth.BleStraightVehicleState
import com.leapauto.app.ui.theme.statusGood
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import com.leapauto.app.ui.theme.statusWarn

/**
 * 蓝牙直进直出（远程直线挪车）控制抽屉面板。
 * 遵循纯实色承载规范（containerColor = surface）与智能座舱微晶规范。
 * 安全红线：长按持续发送移动指令，松手/移出/取消立即 0 毫秒发送 Stop 刹停。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StraightRemoteBottomSheet(
    onDismissRequest: () -> Unit,
    canControl: Boolean,
    bluetoothPhase: BleConnectionPhase,
    onStartMoving: (BleStraightAction) -> Unit,
    onStopMoving: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val haptic = LocalHapticFeedback.current
    var isMoving by remember { mutableStateOf(false) }
    var currentDirection by remember { mutableStateOf<BleStraightAction?>(null) }

    val statusMessage = when {
        isMoving -> if (currentDirection == BleStraightAction.FORWARD) "正在向前直进中..." else "正在向后倒车中..."
        canControl -> "蓝牙钥匙已就绪，长按方向键挪车"
        bluetoothPhase in setOf(BleConnectionPhase.CONNECTING, BleConnectionPhase.DISCOVERING,
            BleConnectionPhase.SUBSCRIBING, BleConnectionPhase.AUTHENTICATING) -> "正在连接车辆蓝牙钥匙..."
        else -> "蓝牙钥匙未连接，请靠近车辆"
    }

    ModalBottomSheet(
        onDismissRequest = {
            if (isMoving) {
                onStopMoving()
                isMoving = false
                currentDirection = null
            }
            onDismissRequest()
        },
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 1. 顶部标题栏与状态指示
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_straight_remote),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "蓝牙直进直出",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = statusMessage,
                            style = MaterialTheme.typography.labelSmall,
                            color = when {
                                isMoving -> MaterialTheme.colorScheme.primary
                                canControl -> MaterialTheme.statusGood
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                // 右侧关闭按钮
                IconButton(
                    onClick = {
                        if (isMoving) {
                            onStopMoving()
                            isMoving = false
                            currentDirection = null
                        }
                        onDismissRequest()
                    }
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_phosphor_x),
                        contentDescription = "关闭",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // 2. 中部核心控制面板 (长按前进 / 俯视车模 / 长按后退)
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 20.dp, horizontal = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    // 前进按钮 (长按按压式)
                    HoldControlButton(
                        action = BleStraightAction.FORWARD,
                        enabled = canControl && (!isMoving || currentDirection == BleStraightAction.FORWARD),
                        isCurrentMoving = isMoving && currentDirection == BleStraightAction.FORWARD,
                        onStartMove = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            isMoving = true
                            currentDirection = BleStraightAction.FORWARD
                            onStartMoving(BleStraightAction.FORWARD)
                        },
                        onStopMove = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            isMoving = false
                            currentDirection = null
                            onStopMoving()
                        }
                    )

                    // 中间智能座舱级微晶俯视车模与导向光波
                    VehicleTopDownBlueprint(
                        isMoving = isMoving,
                        currentDirection = currentDirection,
                        vehicleState = if (canControl) BleStraightVehicleState.READY else BleStraightVehicleState.WAITING
                    )

                    // 后退按钮 (长按按压式)
                    HoldControlButton(
                        action = BleStraightAction.BACKWARD,
                        enabled = canControl && (!isMoving || currentDirection == BleStraightAction.BACKWARD),
                        isCurrentMoving = isMoving && currentDirection == BleStraightAction.BACKWARD,
                        onStartMove = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            isMoving = true
                            currentDirection = BleStraightAction.BACKWARD
                            onStartMoving(BleStraightAction.BACKWARD)
                        },
                        onStopMove = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            isMoving = false
                            currentDirection = null
                            onStopMoving()
                        }
                    )
                }
            }

            // 3. 安全说明底栏
            Text(
                text = "安全规范：请在可视距离内操作；手指松开按键或离开应用界面，车辆将立即自动刹停。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                lineHeight = 16.sp,
                modifier = Modifier.padding(bottom = 12.dp)
            )
        }
    }
}

/**
 * 具有工业级按压即走、松手即刹特性的长按按钮组件。
 */
@Composable
private fun HoldControlButton(
    action: BleStraightAction,
    enabled: Boolean,
    isCurrentMoving: Boolean,
    onStartMove: () -> Unit,
    onStopMove: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isForward = action == BleStraightAction.FORWARD
    val currentOnStartMove by rememberUpdatedState(onStartMove)
    val currentOnStopMove by rememberUpdatedState(onStopMove)
    val scale by animateFloatAsState(
        targetValue = if (isCurrentMoving) 0.95f else 1.0f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "holdBtnScale"
    )
    val btnColor = when {
        isCurrentMoving -> MaterialTheme.colorScheme.primary
        enabled -> MaterialTheme.colorScheme.surfaceVariant
        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = btnColor,
        border = BorderStroke(
            1.dp,
            if (isCurrentMoving) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        ),
        modifier = modifier
            .fillMaxWidth(0.72f)
            .height(56.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                awaitPointerEventScope {
                    while (true) {
                        awaitFirstDown(requireUnconsumed = false)
                        currentOnStartMove()
                        waitForUpOrCancellation()
                        currentOnStopMove()
                    }
                }
            }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_phosphor_caret_right),
                contentDescription = null,
                tint = if (isCurrentMoving) MaterialTheme.colorScheme.onPrimary
                else if (enabled) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier
                    .size(20.dp)
                    .rotate(if (isForward) -90f else 90f)
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = if (isCurrentMoving) "正在${action.label} (按住中)" else "按住 ${action.label}",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (isCurrentMoving) FontWeight.Bold else FontWeight.SemiBold,
                color = if (isCurrentMoving) MaterialTheme.colorScheme.onPrimary
                else if (enabled) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
        }
    }
}

/**
 * 智能座舱级微晶俯视全景车模（纯 Compose Canvas 硬件加速矢量绘制）
 * 包含空气动力学车身轮廓、外后视镜、全景微晶天幕、贯穿式灯带与动态导向光波。
 */
@Composable
private fun VehicleTopDownBlueprint(
    isMoving: Boolean,
    currentDirection: BleStraightAction?,
    vehicleState: BleStraightVehicleState,
    modifier: Modifier = Modifier
) {
    val isForward = currentDirection == BleStraightAction.FORWARD
    val primaryColor = MaterialTheme.colorScheme.primary
    val statusGoodColor = MaterialTheme.statusGood
    val outlineColor = MaterialTheme.colorScheme.outlineVariant
    val surfaceVariantColor = MaterialTheme.colorScheme.surfaceVariant
    val textColor = MaterialTheme.colorScheme.onSurface

    val infiniteTransition = rememberInfiniteTransition(label = "straight_motion_ripple")
    val waveOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000, easing = LinearEasing)
        ),
        label = "wave_offset"
    )

    Box(
        modifier = modifier
            .width(140.dp)
            .height(170.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val cx = w / 2f
            val cy = h / 2f

            val carW = 68.dp.toPx()
            val carH = 118.dp.toPx()
            val left = cx - carW / 2f
            val right = cx + carW / 2f
            val top = cy - carH / 2f
            val bottom = cy + carH / 2f

            // 1. 动态移动光波与极光导向箭头
            if (isMoving) {
                val motionColor = if (isForward) primaryColor else statusGoodColor
                val dirSign = if (isForward) -1f else 1f
                val baseDistance = if (isForward) top - 4.dp.toPx() else bottom + 4.dp.toPx()

                for (i in 0..1) {
                    val phase = (waveOffset + i * 0.5f) % 1f
                    val y = baseDistance + dirSign * (phase * 24.dp.toPx())
                    val alpha = (1f - phase) * 0.75f
                    val arrowWidth = (22.dp.toPx() - phase * 6.dp.toPx()).coerceAtLeast(8.dp.toPx())

                    val arrowPath = Path().apply {
                        moveTo(cx - arrowWidth, y - dirSign * 6.dp.toPx())
                        lineTo(cx, y)
                        lineTo(cx + arrowWidth, y - dirSign * 6.dp.toPx())
                    }
                    drawPath(
                        path = arrowPath,
                        color = motionColor.copy(alpha = alpha),
                        style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                    )
                }
            }

            // 2. 外后视镜 (左右各一)
            val mirrorY = top + carH * 0.32f
            val mirrorW = 7.dp.toPx()
            val mirrorH = 4.dp.toPx()
            drawRoundRect(
                color = outlineColor.copy(alpha = 0.55f),
                topLeft = Offset(left - mirrorW + 1.dp.toPx(), mirrorY),
                size = Size(mirrorW, mirrorH),
                cornerRadius = CornerRadius(2.dp.toPx())
            )
            drawRoundRect(
                color = outlineColor.copy(alpha = 0.55f),
                topLeft = Offset(right - 1.dp.toPx(), mirrorY),
                size = Size(mirrorW, mirrorH),
                cornerRadius = CornerRadius(2.dp.toPx())
            )

            // 3. 车身外廓 Path (空气动力学微弧)
            val bodyPath = Path().apply {
                moveTo(cx, top)
                cubicTo(
                    right - 10.dp.toPx(), top,
                    right, top + 8.dp.toPx(),
                    right, top + 24.dp.toPx()
                )
                cubicTo(
                    right - 2.dp.toPx(), top + carH * 0.45f,
                    right + 2.dp.toPx(), top + carH * 0.7f,
                    right, bottom - 18.dp.toPx()
                )
                cubicTo(
                    right, bottom - 3.dp.toPx(),
                    right - 8.dp.toPx(), bottom,
                    cx, bottom
                )
                cubicTo(
                    left + 8.dp.toPx(), bottom,
                    left, bottom - 3.dp.toPx(),
                    left, bottom - 18.dp.toPx()
                )
                cubicTo(
                    left - 2.dp.toPx(), top + carH * 0.7f,
                    left + 2.dp.toPx(), top + carH * 0.45f,
                    left, top + 24.dp.toPx()
                )
                cubicTo(
                    left, top + 8.dp.toPx(),
                    left + 10.dp.toPx(), top,
                    cx, top
                )
                close()
            }

            // 填充微晶车身底色
            drawPath(
                path = bodyPath,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        surfaceVariantColor.copy(alpha = 0.55f),
                        surfaceVariantColor.copy(alpha = 0.30f),
                        surfaceVariantColor.copy(alpha = 0.55f)
                    ),
                    startY = top,
                    endY = bottom
                )
            )

            // 车身边缘晶线描边
            drawPath(
                path = bodyPath,
                color = if (isMoving) {
                    if (isForward) primaryColor.copy(alpha = 0.75f) else statusGoodColor.copy(alpha = 0.75f)
                } else outlineColor.copy(alpha = 0.4f),
                style = Stroke(width = 1.4.dp.toPx())
            )

            // 4. 全景微晶天幕座舱
            val cabinW = carW * 0.72f
            val cabinLeft = cx - cabinW / 2f
            val cabinTop = top + carH * 0.22f
            val cabinH = carH * 0.54f
            val cabinPath = Path().apply {
                addRoundRect(
                    RoundRect(
                        rect = Rect(cabinLeft, cabinTop, cabinLeft + cabinW, cabinTop + cabinH),
                        topLeft = CornerRadius(9.dp.toPx(), 12.dp.toPx()),
                        topRight = CornerRadius(9.dp.toPx(), 12.dp.toPx()),
                        bottomLeft = CornerRadius(7.dp.toPx(), 9.dp.toPx()),
                        bottomRight = CornerRadius(7.dp.toPx(), 9.dp.toPx())
                    )
                )
            }
            drawPath(
                path = cabinPath,
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color(0xFF1E2638).copy(alpha = 0.45f),
                        Color(0xFF0F1524).copy(alpha = 0.65f)
                    ),
                    start = Offset(cabinLeft, cabinTop),
                    end = Offset(cabinLeft + cabinW, cabinTop + cabinH)
                )
            )
            drawPath(
                path = cabinPath,
                color = outlineColor.copy(alpha = 0.25f),
                style = Stroke(width = 0.8.dp.toPx())
            )

            // 5. 前后贯穿式灯带
            val frontLightY = top + 3.dp.toPx()
            val frontLightW = carW * 0.62f
            val frontLightColor = if (isMoving && isForward) primaryColor else Color.White.copy(alpha = 0.55f)
            drawLine(
                color = frontLightColor,
                start = Offset(cx - frontLightW / 2f, frontLightY),
                end = Offset(cx + frontLightW / 2f, frontLightY),
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round
            )

            val rearLightY = bottom - 3.dp.toPx()
            val rearLightW = carW * 0.64f
            val rearLightColor = if (isMoving && !isForward) statusGoodColor else Color(0xFFFF453A).copy(alpha = 0.65f)
            drawLine(
                color = rearLightColor,
                start = Offset(cx - rearLightW / 2f, rearLightY),
                end = Offset(cx + rearLightW / 2f, rearLightY),
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round
            )
        }

        // 6. 车身正中央悬浮微晶状态胶囊
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.90f),
            border = BorderStroke(
                1.dp,
                if (isMoving) {
                    if (isForward) primaryColor.copy(alpha = 0.6f) else statusGoodColor.copy(alpha = 0.6f)
                } else outlineColor.copy(alpha = 0.35f)
            ),
            modifier = Modifier.padding(horizontal = 6.dp)
        ) {
            Text(
                text = when {
                    isMoving -> if (isForward) "▲ 前进中" else "▼ 后退中"
                    vehicleState == BleStraightVehicleState.READY -> "就绪待命"
                    vehicleState == BleStraightVehicleState.PAUSED -> "已暂停"
                    else -> "连接中..."
                },
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = if (isMoving) {
                    if (isForward) primaryColor else statusGoodColor
                } else textColor,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
            )
        }
    }
}
