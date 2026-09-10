package com.leapauto.app.ui

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.leapauto.app.HealthCheckItem
import com.leapauto.app.HealthCheckLevel
import com.leapauto.app.HealthSystemReport
import com.leapauto.app.R
import com.leapauto.app.VehicleAppearance
import com.leapauto.app.VehicleHealthDiagnostics
import com.leapauto.app.VehicleStatus
import com.leapauto.app.ui.theme.LeapBlue
import com.leapauto.app.ui.theme.LocalAppDarkTheme
import com.leapauto.app.ui.theme.glassInsetSurface
import com.leapauto.app.ui.theme.glassSurface
import com.leapauto.app.ui.theme.statusGood
import com.leapauto.app.ui.theme.statusWarn
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VehicleHealthCheckBottomSheet(
    status: VehicleStatus?,
    vehicleAppearance: VehicleAppearance,
    vehicleNickname: String,
    remoteBitmap: Bitmap? = null,
    onControl: (String) -> Unit,
    onRefreshStatus: () -> Unit,
    onDismissRequest: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val isDark = LocalAppDarkTheme.current
    val pageBgColor = if (isDark) Color(0xFF131822) else Color(0xFFF3F5F9)

    var isScanning by remember { mutableStateOf(true) }
    var scanKey by remember { mutableStateOf(0) }
    val report = remember(status, scanKey) {
        VehicleHealthDiagnostics.evaluate(status)
    }

    LaunchedEffect(scanKey) {
        isScanning = true
        delay(1600)
        isScanning = false
    }

    val animatedScore by animateIntAsState(
        targetValue = if (isScanning) 0 else report.score,
        animationSpec = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
        label = "scoreCountUp"
    )

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = pageBgColor,
        contentColor = MaterialTheme.colorScheme.onSurface,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 4.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // ====== 1. 顶栏：返回按钮 + 标题 + 重新体检 ======
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(
                    onClick = {
                        scope.launch {
                            sheetState.hide()
                            onDismissRequest()
                        }
                    },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_phosphor_x),
                        contentDescription = "关闭",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Text(
                    text = "全车健康诊断",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Surface(
                    shape = RoundedCornerShape(percent = 50),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                    border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.40f)),
                    modifier = Modifier.clickable {
                        onRefreshStatus()
                        scanKey++
                    }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_phosphor_arrow_clockwise),
                            contentDescription = "重新体检",
                            modifier = Modifier.size(13.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = if (isScanning) "扫描中…" else "重新体检",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            // ====== 2. 全息扫描视觉区 (车模 + 激光雷达扫描波) ======
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.glassSurface,
                border = glassCardBorder()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp, horizontal = 14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // 车模 + 激光光束覆盖层
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(130.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (remoteBitmap != null) {
                            Image(
                                bitmap = remoteBitmap.asImageBitmap(),
                                contentDescription = "体检车模",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(130.dp)
                                    .padding(horizontal = 12.dp)
                            )
                        } else {
                            Image(
                                painter = painterResource(vehicleAppearance.imageResource),
                                contentDescription = "体检车模",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(130.dp)
                                    .padding(horizontal = 12.dp)
                            )
                        }

                        // 激光雷达扫描波动态渲染
                        if (isScanning) {
                            val infiniteTransition = rememberInfiniteTransition(label = "laserScan")
                            val sweepFraction by infiniteTransition.animateFloat(
                                initialValue = 0f,
                                targetValue = 1f,
                                animationSpec = infiniteRepeatable(
                                    animation = tween(1200, easing = LinearEasing),
                                    repeatMode = RepeatMode.Restart
                                ),
                                label = "sweep"
                            )

                            androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                                val w = size.width
                                val h = size.height
                                val laserX = w * sweepFraction

                                // 扫描光柱与尾晕
                                drawRect(
                                    brush = Brush.horizontalGradient(
                                        colors = listOf(
                                            Color.Transparent,
                                            LeapBlue.copy(alpha = 0.20f),
                                            LeapBlue.copy(alpha = 0.85f),
                                            Color.White
                                        ),
                                        startX = (laserX - 60f).coerceAtLeast(0f),
                                        endX = laserX
                                    ),
                                    topLeft = Offset((laserX - 60f).coerceAtLeast(0f), 0f),
                                    size = androidx.compose.ui.geometry.Size(60f, h)
                                )

                                // 激光竖线
                                drawLine(
                                    color = Color.White,
                                    start = Offset(laserX, 0f),
                                    end = Offset(laserX, h),
                                    strokeWidth = 2.dp.toPx()
                                )
                            }
                        }
                    }

                    // 健康得分与全车状态评级
                    Row(
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        val scoreColor = when (report.level) {
                            HealthCheckLevel.GOOD -> MaterialTheme.statusGood
                            HealthCheckLevel.WARNING -> MaterialTheme.statusWarn
                            HealthCheckLevel.CRITICAL -> MaterialTheme.colorScheme.error
                        }
                        Text(
                            text = "$animatedScore",
                            fontSize = 38.sp,
                            lineHeight = 36.sp,
                            fontWeight = FontWeight.Bold,
                            color = scoreColor
                        )
                        Text(
                            text = "分",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = scoreColor,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                    }

                    Text(
                        text = if (isScanning) "全车传感器阵列高速体检中…" else report.summary,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center
                    )
                }
            }

            // ====== 3. 待处理异常项目与一键修复动作 ======
            if (!isScanning && report.issues.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "需留意事项 (${report.issues.size})",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.statusWarn
                    )
                    report.issues.forEach { issue ->
                        HealthIssueCard(
                            issue = issue,
                            onFix = { cmd ->
                                onControl(cmd)
                                scanKey++
                            }
                        )
                    }
                }
            }

            // ====== 4. 四大核心分系统体检报告 ======
            Text(
                text = "系统检测详情",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            report.systems.forEach { system ->
                HealthSystemCard(system = system, isScanning = isScanning)
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun HealthIssueCard(
    issue: HealthCheckItem,
    onFix: (String) -> Unit
) {
    val containerColor = when (issue.level) {
        HealthCheckLevel.CRITICAL -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.40f)
        HealthCheckLevel.WARNING -> MaterialTheme.statusWarn.copy(alpha = 0.12f)
        HealthCheckLevel.GOOD -> MaterialTheme.glassInsetSurface
    }
    val strokeColor = when (issue.level) {
        HealthCheckLevel.CRITICAL -> MaterialTheme.colorScheme.error.copy(alpha = 0.65f)
        HealthCheckLevel.WARNING -> MaterialTheme.statusWarn.copy(alpha = 0.50f)
        HealthCheckLevel.GOOD -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = containerColor,
        border = BorderStroke(0.6.dp, strokeColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = issue.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (issue.level == HealthCheckLevel.CRITICAL) MaterialTheme.colorScheme.error else MaterialTheme.statusWarn
                )
                Text(
                    text = issue.detail,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (issue.fixCommand != null && issue.fixLabel != null) {
                Button(
                    onClick = { onFix(issue.fixCommand) },
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (issue.level == HealthCheckLevel.CRITICAL) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                    ),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = issue.fixLabel,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun HealthSystemCard(
    system: HealthSystemReport,
    isScanning: Boolean
) {
    var expanded by remember { mutableStateOf(true) }
    val levelColor = when (system.level) {
        HealthCheckLevel.GOOD -> MaterialTheme.statusGood
        HealthCheckLevel.WARNING -> MaterialTheme.statusWarn
        HealthCheckLevel.CRITICAL -> MaterialTheme.colorScheme.error
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.glassInsetSurface,
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // 系统顶栏行
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        painter = painterResource(system.iconRes),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = levelColor
                    )
                    Text(
                        text = system.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = levelColor.copy(alpha = 0.12f)
                    ) {
                        Text(
                            text = if (isScanning) "检测中…" else system.statusText,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            fontWeight = FontWeight.Bold,
                            color = levelColor
                        )
                    }
                    Icon(
                        painter = painterResource(R.drawable.ic_phosphor_caret_right),
                        contentDescription = if (expanded) "收起" else "展开",
                        modifier = Modifier
                            .size(14.dp)
                            .then(if (expanded) Modifier.clip(CircleShape) else Modifier),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // 展开的检测细项
            if (expanded && !isScanning) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 26.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    system.items.forEach { item ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = item.title,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = item.detail,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (item.level != HealthCheckLevel.GOOD) levelColor else MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.End,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}
