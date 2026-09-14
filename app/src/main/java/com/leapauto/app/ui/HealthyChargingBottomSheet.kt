package com.leapauto.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
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
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.delay
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.leapauto.app.ChargePlanCyclesHelper
import com.leapauto.app.R
import com.leapauto.app.VehicleHomeStatus
import com.leapauto.app.VehicleStatus
import com.leapauto.app.VehicleStatusMapper
import com.leapauto.app.ui.theme.LocalAppDarkTheme
import com.leapauto.app.ui.theme.statusGood
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 充电中心主界面（参考官方 App 图 1、图 2、图 3 重构）。
 *
 * 整合：
 * 1. 顶部当前电量展示与电池数据卡片。
 * 2. 健康充电卡片：开关、描述、充电上限滑动条、90% 最佳限值小三角标记。
 * 3. 预约充电卡片：开关、描述、时段入口（点击弹出图 2 预约充电滚轮弹窗）。
 * 4. 预约电池预热卡片：开关、描述、时段入口（点击弹出图 3 预约电池预热滚轮弹窗）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HealthyChargingBottomSheet(
    onDismissRequest: () -> Unit,
    status: VehicleStatus?,
    currentLimitSoc: Int,
    isHealthyChargeEnabled: Boolean,
    initialScheduledChargeEnabled: Boolean = false,
    initialScheduledStartTime: String = "22:00",
    initialScheduledEndTime: String = "06:30",
    initialContinueUntilLimit: Boolean = true,
    initialScheduledCirculation: Int = 1,
    initialScheduledCycles: String = "1,1,1,1,1,1,1",
    initialScheduledPreheatEnabled: Boolean = false,
    initialScheduledPreheatStartTime: String = "13:35",
    initialScheduledPreheatDays: String = "1,1,1,1,1,1,1",
    onApplyChargingSettings: (
        healthyEnabled: Boolean,
        targetSoc: Int,
        scheduledEnabled: Boolean,
        startTime: String,
        endTime: String,
        continueUntilLimit: Boolean,
        circulation: Int,
        cycles: String
    ) -> Unit,
    onApplyScheduledPreheat: (
        enabled: Boolean,
        startTime: String,
        days: String
    ) -> Unit = { _, _, _ -> },
    onControl: (String) -> Unit = {},
    onRefreshStatus: () -> Unit = {}
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current
    var lastToastTime by remember { mutableLongStateOf(0L) }

    // 充电中每 1 秒自动静默刷新车况，实时同步电量、功率与倒计时
    val isChargingState = status?.chargeState == 1
    LaunchedEffect(isChargingState) {
        if (isChargingState) {
            while (true) {
                delay(1000L)
                onRefreshStatus()
            }
        }
    }

    // 1. 健康充电状态
    var healthySwitchEnabled by remember(isHealthyChargeEnabled) { mutableStateOf(isHealthyChargeEnabled) }
    var selectedSoc by remember(currentLimitSoc, isHealthyChargeEnabled) {
        val base = currentLimitSoc.toFloat().coerceIn(50f, 100f)
        mutableFloatStateOf(if (isHealthyChargeEnabled && base >= 100f) 90f else base)
    }

    // 2. 预约充电状态
    var scheduledChargeEnabled by remember(initialScheduledChargeEnabled) { mutableStateOf(initialScheduledChargeEnabled) }
    var scheduledStartTime by remember(initialScheduledStartTime) { mutableStateOf(initialScheduledStartTime.ifBlank { "22:00" }) }
    var scheduledEndTime by remember(initialScheduledEndTime) { mutableStateOf(initialScheduledEndTime.ifBlank { "06:30" }) }
    var continueUntilLimit by remember(initialContinueUntilLimit) { mutableStateOf(initialContinueUntilLimit) }
    var scheduledDays by remember(initialScheduledCycles) {
        mutableStateOf(ChargePlanCyclesHelper.parseToDaySet(initialScheduledCycles))
    }

    // 3. 预约电池预热状态
    var scheduledPreheatEnabled by remember(initialScheduledPreheatEnabled) { mutableStateOf(initialScheduledPreheatEnabled) }
    var scheduledPreheatStartTime by remember(initialScheduledPreheatStartTime) { mutableStateOf(initialScheduledPreheatStartTime.ifBlank { "13:35" }) }
    var preheatDays by remember(initialScheduledPreheatDays) {
        mutableStateOf(ChargePlanCyclesHelper.parseToDaySet(initialScheduledPreheatDays))
    }

    // 弹窗控制
    var showScheduledChargeDialog by remember { mutableStateOf(false) }
    var showScheduledPreheatDialog by remember { mutableStateOf(false) }

    // 图 2: 预约充电弹窗
    if (showScheduledChargeDialog) {
        ScheduledChargingDialog(
            onDismissRequest = { showScheduledChargeDialog = false },
            initialStartTime = scheduledStartTime,
            initialEndTime = scheduledEndTime,
            initialDays = scheduledDays,
            initialContinueUntilLimit = continueUntilLimit,
            onConfirm = { sTime, eTime, days, contLimit ->
                scheduledStartTime = sTime
                scheduledEndTime = eTime
                scheduledDays = days
                continueUntilLimit = contLimit
                showScheduledChargeDialog = false
            }
        )
    }

    // 图 3: 预约电池预热弹窗
    if (showScheduledPreheatDialog) {
        ScheduledPreheatDialog(
            onDismissRequest = { showScheduledPreheatDialog = false },
            initialStartTime = scheduledPreheatStartTime,
            initialDays = preheatDays,
            onConfirm = { sTime, days ->
                scheduledPreheatStartTime = sTime
                preheatDays = days
                showScheduledPreheatDialog = false
            }
        )
    }

    val isDark = LocalAppDarkTheme.current
    val pageBgColor = if (isDark) Color(0xFF141416) else Color(0xFFF7F8FA)
    val cardBgColor = if (isDark) Color(0xFF1E1E22) else Color.White
    val cardBorderColor = if (isDark) Color(0xFF2C2C30) else Color(0xFFEBECEF)

    // 图 1: 充电中心主界面
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
                .padding(horizontal = 16.dp, vertical = 4.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 顶栏：返回按钮与标题“充电中心”
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(
                    onClick = onDismissRequest,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_phosphor_arrow_left),
                        contentDescription = "返回",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Text(
                    text = "充电中心",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.size(36.dp))
            }

            // 当前电量与电池数据（最低温度 + 充电剩余时间）
            val rawSoc = VehicleHomeStatus.resolvedSoc(status?.preciseSoc, status?.soc)
            val displaySoc = rawSoc?.let { VehicleStatusMapper.displayPreciseSoc(it)?.removeSuffix("%") } ?: "--"
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = displaySoc,
                        fontSize = 48.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        lineHeight = 48.sp
                    )
                    if (displaySoc != "--") {
                        Text(
                            text = "%",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(bottom = 6.dp, start = 2.dp)
                        )
                    }
                }

                // 电池温度、充电功率 与 充电剩余时间 精致卡片
                val formattedRemainTime = formatChargeRemainTime(status?.chargeRemainTime, status?.chargeState)
                val minTemp = status?.minBatteryTemp?.trim()?.takeIf { it.isNotBlank() && it != "--" } ?: "-- °C"
                val chargingPowerText = when {
                    status?.chargeState == 1 -> status.chargingPower?.takeIf { it.isNotBlank() } ?: "充电中"
                    else -> "未充电"
                }

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color.Transparent,
                    border = glassCardBorder(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .frostedGlassCard(
                            shape = RoundedCornerShape(16.dp),
                            auraColor = MaterialTheme.statusGood.copy(alpha = 0.10f),
                            auraCenter = Offset(0.5f, 0.5f)
                        )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp, horizontal = 12.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 1. 电池温度
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(3.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = "电池温度",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = minTemp,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        // 分割线 1
                        Box(
                            modifier = Modifier
                                .height(22.dp)
                                .width(0.8.dp)
                                .background(cardBorderColor)
                        )

                        // 2. 充电功率
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(3.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = "充电功率",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = chargingPowerText,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (status?.chargeState == 1) MaterialTheme.statusGood else MaterialTheme.colorScheme.onSurface
                            )
                        }

                        // 分割线 2
                        Box(
                            modifier = Modifier
                                .height(22.dp)
                                .width(0.8.dp)
                                .background(cardBorderColor)
                        )

                        // 3. 剩余时间
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(3.dp),
                            modifier = Modifier.weight(1.1f)
                        ) {
                            Text(
                                text = "剩余时间",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = formattedRemainTime,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (status?.chargeState == 1) MaterialTheme.statusGood else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            // 实时插枪即时充电操作栏（未插枪时完全隐藏）
            val isGunConnected = status?.chargeGunConnected == true || status?.chargeState == 1 || status?.chargeState == 2
            val isCharging = status?.chargeState == 1

            if (isGunConnected) {
                val outlineBtnBg = if (isDark) Color(0xFF222226) else Color.White
                val outlineBtnBorder = if (isDark) Color(0xFF48484C) else Color(0xFF8E8E93)
                val outlineBtnText = if (isDark) Color(0xFFEDEDED) else Color(0xFF222222)
                val solidBtnBg = if (isDark) Color(0xFF38383A) else Color(0xFF2B2B2B)

                val mainActionText = if (isCharging) "结束充电" else "开始充电"
                val mainActionCmd = if (isCharging) "stopCharging" else "startCharging"

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 左侧线框按钮：解锁充电枪
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = outlineBtnBg,
                        border = BorderStroke(0.8.dp, outlineBtnBorder),
                        modifier = Modifier
                            .weight(1f)
                            .height(42.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .clickable {
                                onControl("unlockCharger")
                                onDismissRequest()
                            }
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Text(
                                text = "解锁充电枪",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Normal,
                                color = outlineBtnText
                            )
                        }
                    }

                    // 右侧实心深色按钮：结束充电 (充电中) / 开始充电 (未充电)
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = solidBtnBg,
                        modifier = Modifier
                            .weight(1f)
                            .height(42.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .clickable {
                                onControl(mainActionCmd)
                                onDismissRequest()
                            }
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Text(
                                text = mainActionText,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Normal,
                                color = Color.White
                            )
                        }
                    }
                }
            }

            // 卡片 1: 健康充电（参考图 1）
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color.Transparent,
                border = glassCardBorder(),
                modifier = Modifier
                    .fillMaxWidth()
                    .frostedGlassCard(
                        shape = RoundedCornerShape(16.dp),
                        auraColor = MaterialTheme.statusGood.copy(alpha = 0.10f),
                        auraCenter = Offset(0.9f, 0.1f)
                    )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // 开关行
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "健康充电",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(12.dp))
                        Switch(
                            checked = healthySwitchEnabled,
                            onCheckedChange = { isChecked ->
                                healthySwitchEnabled = isChecked
                                if (isChecked && selectedSoc >= 100f) {
                                    selectedSoc = 90f
                                    Toast.makeText(context, "已调整至健康限值 90%", Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = MaterialTheme.statusGood
                            )
                        )
                    }

                    // 充电上限滑块与最佳限值90%标记
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "充电上限${selectedSoc.roundToInt()}%",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        // 滑块本体（完全对照截图：浅薄荷绿微轨 + 内部微型白色刻度柱 + 20dp 白色立体旋钮）
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "50%",
                                fontSize = 13.sp,
                                color = if (isDark) Color(0xFF8E8E93) else Color(0xFF6E6E73),
                                modifier = Modifier.padding(end = 8.dp)
                            )
                            Slider(
                                value = selectedSoc,
                                onValueChange = { targetVal ->
                                    val snapped = (targetVal / 5).roundToInt() * 5f
                                    if (healthySwitchEnabled && snapped >= 100f) {
                                        selectedSoc = 95f
                                        val now = System.currentTimeMillis()
                                        if (now - lastToastTime > 1800L) {
                                            lastToastTime = now
                                            Toast.makeText(context, "健康充电最高限值为 90%", Toast.LENGTH_SHORT).show()
                                        }
                                    } else {
                                        selectedSoc = snapped
                                    }
                                },
                                valueRange = 50f..100f,
                                steps = 0,
                                thumb = {
                                    Surface(
                                        shape = CircleShape,
                                        color = Color.White,
                                        shadowElevation = 3.dp,
                                        border = BorderStroke(0.6.dp, Color(0xFFD8D8DC)),
                                        modifier = Modifier.size(20.dp)
                                    ) {}
                                },
                                track = { sliderState ->
                                    val activeRatio = ((sliderState.value - 50f) / 50f).coerceIn(0f, 1f)
                                    val activeTrackColor = if (isDark) Color(0xFF234B32) else Color(0xFFD0E8D6)
                                    val inactiveTrackColor = if (isDark) Color(0xFF2C2C30) else Color(0xFFECECF0)
                                    val tickColor = Color.White

                                    Canvas(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(7.dp)
                                    ) {
                                        val w = size.width
                                        val h = size.height
                                        val r = h / 2f
                                        val activeW = w * activeRatio

                                        // 底层全宽浅灰轨道
                                        drawRoundRect(
                                            color = inactiveTrackColor,
                                            size = Size(w, h),
                                            cornerRadius = CornerRadius(r, r)
                                        )

                                        // 活跃浅薄荷绿轨道
                                        if (activeW > 0f) {
                                            drawRoundRect(
                                                color = activeTrackColor,
                                                size = Size(activeW, h),
                                                cornerRadius = CornerRadius(r, r)
                                            )
                                        }

                                        // 内部微型白色垂直刻度柱（60%, 70%, 80%, 90%）
                                        val stepRatios = listOf(0.20f, 0.40f, 0.60f, 0.80f)
                                        val tickW = 2.dp.toPx()
                                        val tickH = 3.5.dp.toPx()
                                        val tickY = (h - tickH) / 2f
                                        stepRatios.forEach { ratio ->
                                            val tickX = w * ratio - tickW / 2f
                                            drawRoundRect(
                                                color = tickColor,
                                                topLeft = Offset(tickX, tickY),
                                                size = Size(tickW, tickH),
                                                cornerRadius = CornerRadius(1.dp.toPx(), 1.dp.toPx())
                                            )
                                        }
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = "100%",
                                fontSize = 13.sp,
                                color = if (isDark) Color(0xFF8E8E93) else Color(0xFF6E6E73),
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }

                        // 90% 最佳限值提示标记（向上偏移 16dp 抵消 Slider 触控盒空白，让箭头紧贴圆钮正下方 2~3dp）
                        BoxWithConstraints(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 38.dp)
                                .offset(y = (-16).dp)
                        ) {
                            val xPos = maxWidth * 0.80f
                            val triangleColor = if (isDark) Color(0xFF8E8E93) else Color(0xFFA0A0A5)
                            val labelColor = if (isDark) Color(0xFF8E8E93) else Color(0xFF7A7A80)

                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.offset(x = xPos - 30.dp)
                            ) {
                                // 纯几何三角形绘制，消除中文字符字体自带的虚高下边距
                                Canvas(modifier = Modifier.size(width = 8.dp, height = 5.dp)) {
                                    val path = Path().apply {
                                        moveTo(size.width / 2f, 0f)
                                        lineTo(size.width, size.height)
                                        lineTo(0f, size.height)
                                        close()
                                    }
                                    drawPath(path, color = triangleColor)
                                }

                                Spacer(Modifier.height(2.dp))

                                Text(
                                    text = "最佳限值90%",
                                    fontSize = 11.sp,
                                    lineHeight = 12.sp,
                                    fontWeight = FontWeight.Normal,
                                    color = labelColor
                                )
                            }
                        }
                    }
                }
            }

            // 卡片 2: 预约充电（参考图 1）
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color.Transparent,
                border = glassCardBorder(),
                modifier = Modifier
                    .fillMaxWidth()
                    .frostedGlassCard(shape = RoundedCornerShape(16.dp))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "预约充电",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "插枪后会根据设定时间充电，仅支持慢充",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                                lineHeight = 16.sp
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Switch(
                            checked = scheduledChargeEnabled,
                            onCheckedChange = { scheduledChargeEnabled = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = MaterialTheme.statusGood
                            )
                        )
                    }

                    // 开启时展现时段入口行（点击弹出图 2 预约充电滚轮弹窗）
                    if (scheduledChargeEnabled) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(0.6.dp)
                                .background(cardBorderColor)
                        )

                        val timeRangeText = formatScheduleTimeRange(scheduledStartTime, scheduledEndTime)
                        val daysText = ChargePlanCyclesHelper.formatSummary(scheduledDays).substringBefore(" (")
                        val summaryText = "$daysText $timeRangeText"

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { showScheduledChargeDialog = true }
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    text = "时段",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = summaryText,
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Icon(
                                painter = painterResource(R.drawable.ic_phosphor_caret_right),
                                contentDescription = "设置时段",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            // 卡片 3: 预约电池预热（参考图 1 底部）
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color.Transparent,
                border = glassCardBorder(),
                modifier = Modifier
                    .fillMaxWidth()
                    .frostedGlassCard(shape = RoundedCornerShape(16.dp))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            horizontal = 16.dp,
                            vertical = if (scheduledPreheatEnabled) 12.dp else 6.dp
                        ),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "电池预热",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(12.dp))
                        Switch(
                            checked = scheduledPreheatEnabled,
                            onCheckedChange = { scheduledPreheatEnabled = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = MaterialTheme.statusGood
                            )
                        )
                    }

                    // 开启时展现时段入口行（点击弹出图 3 预约电池预热滚轮弹窗）
                    if (scheduledPreheatEnabled) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(0.6.dp)
                                .background(cardBorderColor)
                        )

                        val preheatDaysText = ChargePlanCyclesHelper.formatSummary(preheatDays).substringBefore(" (")
                        val preheatSummaryText = "$preheatDaysText $scheduledPreheatStartTime"

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { showScheduledPreheatDialog = true }
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    text = "时段",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = preheatSummaryText,
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Icon(
                                painter = painterResource(R.drawable.ic_phosphor_caret_right),
                                contentDescription = "设置时段",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            val vehicleCyclesMask = ChargePlanCyclesHelper.toVehicleMask(scheduledDays)

            // 保存并下发按钮
            Button(
                onClick = {
                    val chargingChanged = healthySwitchEnabled != isHealthyChargeEnabled ||
                        selectedSoc.roundToInt() != currentLimitSoc ||
                        scheduledChargeEnabled != initialScheduledChargeEnabled ||
                        scheduledStartTime != initialScheduledStartTime ||
                        scheduledEndTime != initialScheduledEndTime ||
                        continueUntilLimit != initialContinueUntilLimit ||
                        ChargePlanCyclesHelper.toVehicleMask(scheduledDays) != ChargePlanCyclesHelper.toVehicleMask(initialScheduledCycles)

                    val preheatChanged = scheduledPreheatEnabled != initialScheduledPreheatEnabled ||
                        scheduledPreheatStartTime != initialScheduledPreheatStartTime ||
                        ChargePlanCyclesHelper.toVehicleMask(preheatDays) != ChargePlanCyclesHelper.toVehicleMask(initialScheduledPreheatDays)

                    if (chargingChanged || !preheatChanged) {
                        onApplyChargingSettings(
                            healthySwitchEnabled,
                            selectedSoc.roundToInt(),
                            scheduledChargeEnabled,
                            scheduledStartTime,
                            scheduledEndTime,
                            continueUntilLimit,
                            if (scheduledDays.size == 7 && scheduledChargeEnabled) 1 else 1,
                            vehicleCyclesMask
                        )
                    }

                    if (preheatChanged) {
                        onApplyScheduledPreheat(
                            scheduledPreheatEnabled,
                            scheduledPreheatStartTime,
                            ChargePlanCyclesHelper.toVehicleMask(preheatDays)
                        )
                    }
                    onDismissRequest()
                },
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isDark) Color(0xFF2C2C30) else Color(0xFF1E1E20)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Text(
                    text = "保存并下发设置",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

// ----------------------------------------------------------------
// 图 2: 预约充电滚轮弹窗
// ----------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScheduledChargingDialog(
    onDismissRequest: () -> Unit,
    initialStartTime: String,
    initialEndTime: String,
    initialDays: Set<Int>,
    initialContinueUntilLimit: Boolean,
    onConfirm: (startTime: String, endTime: String, days: Set<Int>, continueUntilLimit: Boolean) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var draftStartTime by remember(initialStartTime) { mutableStateOf(initialStartTime) }
    var draftEndTime by remember(initialEndTime) { mutableStateOf(initialEndTime) }
    var draftDays by remember(initialDays) { mutableStateOf(initialDays) }
    var draftContinueUntilLimit by remember(initialContinueUntilLimit) { mutableStateOf(initialContinueUntilLimit) }

    val isNextDay = remember(draftStartTime, draftEndTime) {
        val sHour = draftStartTime.substringBefore(":").toIntOrNull() ?: 0
        val sMin = draftStartTime.substringAfter(":").toIntOrNull() ?: 0
        val eHour = draftEndTime.substringBefore(":").toIntOrNull() ?: 0
        val eMin = draftEndTime.substringAfter(":").toIntOrNull() ?: 0
        (eHour * 60 + eMin) <= (sHour * 60 + sMin)
    }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        dragHandle = null,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 顶栏：标题“预约充电”与右上角关闭叉号
            Box(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "预约充电",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.align(Alignment.Center)
                )
                IconButton(
                    onClick = onDismissRequest,
                    modifier = Modifier
                        .size(32.dp)
                        .align(Alignment.CenterEnd)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_phosphor_x),
                        contentDescription = "关闭",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // 1. 开始时间滚轮
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "开始时间",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                TimeWheelPicker(
                    timeString = draftStartTime,
                    onTimeChanged = { draftStartTime = it }
                )
            }

            // 2. 结束时间滚轮（带次日标识）
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "结束时间",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                TimeWheelPicker(
                    timeString = draftEndTime,
                    onTimeChanged = { draftEndTime = it },
                    prefix = if (isNextDay) "次日" else null
                )
            }

            // 3. 重复：周日 周一 周二 周三 周四 周五 周六（7 颗胶囊按钮，参考图 2）
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "重复",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                // 顺序：周日(7), 周一(1), 周二(2), 周三(3), 周四(4), 周五(5), 周六(6)
                val pills = listOf(
                    7 to "周日",
                    1 to "周一",
                    2 to "周二",
                    3 to "周三",
                    4 to "周四",
                    5 to "周五",
                    6 to "周六"
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    pills.forEach { (dayIdx, label) ->
                        OfficialDayPill(
                            label = label,
                            selected = draftDays.contains(dayIdx),
                            onClick = {
                                draftDays = if (draftDays.contains(dayIdx)) {
                                    if (draftDays.size > 1) draftDays - dayIdx else draftDays
                                } else {
                                    draftDays + dayIdx
                                }
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // 4. 到停止时间未达到充电上限，将继续充电（单选圈/复选圈）
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { draftContinueUntilLimit = !draftContinueUntilLimit }
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(18.dp)
                        .clip(CircleShape)
                        .border(
                            width = if (draftContinueUntilLimit) 5.dp else 1.5.dp,
                            color = if (draftContinueUntilLimit) MaterialTheme.statusGood else MaterialTheme.colorScheme.outlineVariant,
                            shape = CircleShape
                        )
                )
                Text(
                    text = "到停止时间未达到充电上限，将继续充电",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp
                )
            }

            // 5. 确定按钮（黑色圆角按钮，参考图 2）
            Button(
                onClick = {
                    onConfirm(draftStartTime, draftEndTime, draftDays, draftContinueUntilLimit)
                },
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF222222)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Text(
                    text = "确定",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}

// ----------------------------------------------------------------
// 图 3: 预约电池预热滚轮弹窗
// ----------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScheduledPreheatDialog(
    onDismissRequest: () -> Unit,
    initialStartTime: String,
    initialDays: Set<Int>,
    onConfirm: (startTime: String, days: Set<Int>) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var draftStartTime by remember(initialStartTime) { mutableStateOf(initialStartTime) }
    var draftDays by remember(initialDays) { mutableStateOf(initialDays) }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        dragHandle = null,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 顶栏：标题“电池预热”与右上角关闭叉号
            Box(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "电池预热",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.align(Alignment.Center)
                )
                IconButton(
                    onClick = onDismissRequest,
                    modifier = Modifier
                        .size(32.dp)
                        .align(Alignment.CenterEnd)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_phosphor_x),
                        contentDescription = "关闭",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // 1. 开始时间滚轮（图 3）
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "开始时间",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                TimeWheelPicker(
                    timeString = draftStartTime,
                    onTimeChanged = { draftStartTime = it }
                )
            }

            // 2. 重复：周日 周一 周二 周三 周四 周五 周六（7 颗胶囊按钮，参考图 3）
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "重复",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                val pills = listOf(
                    7 to "周日",
                    1 to "周一",
                    2 to "周二",
                    3 to "周三",
                    4 to "周四",
                    5 to "周五",
                    6 to "周六"
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    pills.forEach { (dayIdx, label) ->
                        OfficialDayPill(
                            label = label,
                            selected = draftDays.contains(dayIdx),
                            onClick = {
                                draftDays = if (draftDays.contains(dayIdx)) {
                                    if (draftDays.size > 1) draftDays - dayIdx else draftDays
                                } else {
                                    draftDays + dayIdx
                                }
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // 3. 确定按钮（黑色圆角按钮，参考图 3）
            Button(
                onClick = {
                    onConfirm(draftStartTime, draftDays)
                },
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF222222)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Text(
                    text = "确定",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}

// ----------------------------------------------------------------
// 官方质感时间滚轮选择器（TimeWheelPicker）
// ----------------------------------------------------------------

@Composable
private fun TimeWheelPicker(
    timeString: String,
    onTimeChanged: (String) -> Unit,
    modifier: Modifier = Modifier,
    prefix: String? = null
) {
    val hours = remember { (0..23).map { "%02d".format(it) } }
    val minutes = remember { (0..55 step 5).map { "%02d".format(it) } }

    val currentHour = timeString.substringBefore(":").trim().toIntOrNull() ?: 0
    val currentMin = timeString.substringAfter(":").trim().toIntOrNull() ?: 0

    val hourIndex = currentHour.coerceIn(0, 23)
    val minuteIndex = (minutes.indexOfFirst { it.toInt() >= currentMin }.takeIf { it >= 0 } ?: 0)

    val dividerColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)

    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        // 两条水平高亮指示线（对应选中的中央行）
        Canvas(modifier = Modifier.fillMaxWidth(0.85f).height(160.dp)) {
            val y1 = size.height * 0.36f
            val y2 = size.height * 0.64f
            drawLine(dividerColor, Offset(0f, y1), Offset(size.width, y1), 0.8.dp.toPx())
            drawLine(dividerColor, Offset(0f, y2), Offset(size.width, y2), 0.8.dp.toPx())
        }

        Row(
            modifier = Modifier.fillMaxWidth(0.85f),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (prefix != null) {
                Text(
                    text = prefix,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(end = 16.dp)
                )
            }

            // 小时滚轮
            WheelPickerColumn(
                items = hours,
                selectedIndex = hourIndex,
                onIndexChanged = { newHourIdx ->
                    val newHour = hours[newHourIdx]
                    val currentMinuteStr = minutes[minuteIndex]
                    onTimeChanged("$newHour:$currentMinuteStr")
                },
                modifier = Modifier.weight(1f)
            )

            // 冒号
            Text(
                text = ":",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 8.dp)
            )

            // 分钟滚轮（5分钟步进）
            WheelPickerColumn(
                items = minutes,
                selectedIndex = minuteIndex,
                onIndexChanged = { newMinIdx ->
                    val currentHourStr = hours[hourIndex]
                    val newMinuteStr = minutes[newMinIdx]
                    onTimeChanged("$currentHourStr:$newMinuteStr")
                },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun WheelPickerColumn(
    items: List<String>,
    selectedIndex: Int,
    onIndexChanged: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var dragAccumulator by remember { mutableFloatStateOf(0f) }
    val itemHeight = 32.dp
    val density = LocalDensity.current
    val itemHeightPx = with(density) { itemHeight.toPx() }

    Box(
        modifier = modifier
            .height(itemHeight * 5)
            .pointerInput(items.size, selectedIndex) {
                detectVerticalDragGestures(
                    onVerticalDrag = { change, dragAmount ->
                        change.consume()
                        dragAccumulator += dragAmount
                        if (dragAccumulator <= -itemHeightPx) {
                            val steps = (-dragAccumulator / itemHeightPx).toInt()
                            dragAccumulator += steps * itemHeightPx
                            onIndexChanged((selectedIndex + steps) % items.size)
                        } else if (dragAccumulator >= itemHeightPx) {
                            val steps = (dragAccumulator / itemHeightPx).toInt()
                            dragAccumulator -= steps * itemHeightPx
                            onIndexChanged((selectedIndex - steps + items.size * 100) % items.size)
                        }
                    },
                    onDragEnd = { dragAccumulator = 0f },
                    onDragCancel = { dragAccumulator = 0f }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            for (offset in -2..2) {
                val index = (selectedIndex + offset + items.size * 100) % items.size
                val item = items[index]
                val isSelected = offset == 0
                val alpha = when (abs(offset)) {
                    0 -> 1f
                    1 -> 0.40f
                    else -> 0.16f
                }
                val fontSize = when (abs(offset)) {
                    0 -> 24.sp
                    1 -> 16.sp
                    else -> 12.sp
                }
                val fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal

                Box(
                    modifier = Modifier
                        .height(itemHeight)
                        .fillMaxWidth()
                        .clickable { onIndexChanged(index) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = item,
                        fontSize = fontSize,
                        fontWeight = fontWeight,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha)
                    )
                }
            }
        }
    }
}

// ----------------------------------------------------------------
// 官方星期胶囊（周日 周一 周二...）
// ----------------------------------------------------------------

@Composable
private fun OfficialDayPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = LocalAppDarkTheme.current
    val bgColor = if (selected) {
        if (isDark) Color(0xFF1E3A2B) else Color(0xFFEAF7EE)
    } else {
        if (isDark) Color(0xFF26262A) else Color(0xFFF5F6F8)
    }
    val contentColor = if (selected) {
        MaterialTheme.statusGood
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Box(
        modifier = modifier
            .height(34.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(bgColor)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = contentColor
        )
    }
}

// ----------------------------------------------------------------
// 格式化辅助
// ----------------------------------------------------------------

private fun formatScheduleTimeRange(start: String, end: String): String {
    val sHour = start.substringBefore(":").toIntOrNull() ?: 0
    val sMin = start.substringAfter(":").toIntOrNull() ?: 0
    val eHour = end.substringBefore(":").toIntOrNull() ?: 0
    val eMin = end.substringAfter(":").toIntOrNull() ?: 0
    val isNextDay = (eHour * 60 + eMin) <= (sHour * 60 + sMin)
    return if (isNextDay) "$start-次日$end" else "$start-$end"
}

private fun formatChargeRemainTime(remainTime: String?, chargeState: Int?): String {
    if (chargeState == 2) return "已充满"
    if (chargeState != 1 || remainTime.isNullOrBlank() || remainTime == "--") return "未充电"

    val raw = remainTime.trim()
    val hasHourAndMin = raw.contains("时")
    val totalMinutes = if (hasHourAndMin) {
        val h = raw.substringBefore("时").filter { it.isDigit() }.toIntOrNull() ?: 0
        val m = raw.substringAfter("时").substringBefore("分").filter { it.isDigit() }.toIntOrNull() ?: 0
        h * 60 + m
    } else {
        raw.filter { it.isDigit() || it == '.' }.toDoubleOrNull()?.toInt()
    }

    if (totalMinutes == null) return raw

    return when {
        totalMinutes <= 0 -> "已充满"
        totalMinutes < 60 -> "${totalMinutes}分"
        else -> {
            val hours = totalMinutes / 60
            val mins = totalMinutes % 60
            if (mins > 0) "${hours}时${mins}分" else "${hours}时"
        }
    }
}
