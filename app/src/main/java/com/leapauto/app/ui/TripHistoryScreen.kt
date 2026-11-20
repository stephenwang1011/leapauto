// [FEATURE: TRIP_LOG] - 行程与能耗记账全屏独立界面
package com.leapauto.app.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.leapauto.app.R
import com.leapauto.app.trip.TripRecord
import com.leapauto.app.trip.TripRecordStore
import com.leapauto.app.trip.TripType
import com.leapauto.app.ui.theme.LocalAppDarkTheme
import com.leapauto.app.ui.theme.glassInsetSurface
import com.leapauto.app.ui.theme.statusGood
import com.leapauto.app.ui.theme.statusWarn
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun TripHistoryScreen(
    vin: String,
    carType: String = "",
    onBack: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val store = remember(context) { TripRecordStore(context) }
    var records by remember(vin) { mutableStateOf(store.loadRecords(vin)) }
    var pricePerKwh by remember { mutableDoubleStateOf(store.loadElectricityPrice()) }
    var batteryCapacityKwh by remember(vin) { mutableDoubleStateOf(store.loadBatteryCapacityKwh(vin, carType)) }
    var showClearAllDialog by remember { mutableStateOf(false) }
    var recordToDelete by remember { mutableStateOf<TripRecord?>(null) }

    val defaultCapacity = remember(vin, carType) {
        store.loadBatteryCapacityKwh(vin, carType)
    }
    val defaultPrice = remember {
        store.loadElectricityPrice()
    }
    var showInitDialog by remember(vin) {
        mutableStateOf(!store.hasConfiguredTripLogParams(vin))
    }

    // 动态监听本地数据库变化，后台补全地名或新增行程时无缝实时刷新
    DisposableEffect(vin) {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == "records_$vin") {
                records = store.loadRecords(vin)
            } else if (key == "electricity_price") {
                pricePerKwh = store.loadElectricityPrice()
            } else if (key == "battery_capacity_$vin") {
                batteryCapacityKwh = store.loadBatteryCapacityKwh(vin, carType)
            }
        }
        store.registerListener(listener)
        onDispose {
            store.unregisterListener(listener)
        }
    }

    val isDark = LocalAppDarkTheme.current
    val summary = remember(records) { TripRecordStore.computeSummary(records) }

    val monthFormat = remember { SimpleDateFormat("yyyy年MM月", Locale.getDefault()) }
    val currentYear = remember { SimpleDateFormat("yyyy年", Locale.getDefault()).format(Date()) }
    val currentMonthKey = remember { SimpleDateFormat("yyyy年MM月", Locale.getDefault()).format(Date()) }

    val groupedRecords = remember(records) {
        val map = linkedMapOf<String, MutableList<TripRecord>>()
        records.forEach { record ->
            val key = monthFormat.format(Date(record.endTimeEpochMs))
            map.getOrPut(key) { mutableListOf() }.add(record)
        }
        map
    }

    var expandedMonths by remember(records) {
        val firstKey = groupedRecords.keys.firstOrNull()
        val defaultOpen = when {
            groupedRecords.containsKey(currentMonthKey) -> setOf(currentMonthKey)
            firstKey != null -> setOf(firstKey)
            else -> emptySet()
        }
        mutableStateOf(defaultOpen)
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 4.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 1. 统计概览微晶看板（座舱级微晶毛玻璃 + 极光能量透镜）
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .frostedGlassCard(
                        shape = RoundedCornerShape(16.dp),
                        topSpecularLine = true
                    )
                    .border(glassCardBorder(), shape = RoundedCornerShape(16.dp))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // 主指标一行大数字突出显示：出行次数（冰蓝）、累计总里程（能量绿·无小数点）、预估总花费（暖金橙）
                val tripCountColor = if (isDark) Color(0xFF40C4FF) else Color(0xFF0288D1)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    // 1. 出行次数
                    Column {
                        Text(
                            text = "出行次数",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = buildAnnotatedString {
                                withStyle(SpanStyle(fontSize = 26.sp, fontWeight = FontWeight.Bold, color = tripCountColor)) {
                                    append("${summary.tripCount}")
                                }
                                withStyle(SpanStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)) {
                                    append("次")
                                }
                            }
                        )
                    }

                    // 2. 累计总里程（无小数点，能量绿）
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "累计总里程",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = buildAnnotatedString {
                                withStyle(SpanStyle(fontSize = 26.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.statusGood)) {
                                    append("${summary.totalDistanceKm.toInt()}")
                                }
                                withStyle(SpanStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)) {
                                    append("km")
                                }
                            }
                        )
                    }

                    // 3. 预估总花费（暖金橙）
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "预估总花费",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = buildAnnotatedString {
                                withStyle(SpanStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.statusWarn)) {
                                    append("¥")
                                }
                                withStyle(SpanStyle(fontSize = 26.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.statusWarn)) {
                                    append("%.2f".format(summary.totalCostYuan))
                                }
                            }
                        )
                    }
                }

                // 次级指标槽（消耗能源 & 平均电耗）
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            if (isDark) Color(0xFF1E1E1E).copy(alpha = 0.5f)
                            else Color(0xFFF2F2F2).copy(alpha = 0.65f)
                        )
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = if (summary.totalFuelLiters > 0.0) "消耗能源" else "消耗电量",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (summary.totalFuelLiters > 0.0) {
                            Text(
                                text = buildAnnotatedString {
                                    withStyle(SpanStyle(color = Color(0xFFFF9800), fontWeight = FontWeight.SemiBold)) {
                                        append("${"%.1f".format(summary.totalFuelLiters)}L")
                                    }
                                    append(" + ")
                                    withStyle(SpanStyle(color = MaterialTheme.statusGood, fontWeight = FontWeight.SemiBold)) {
                                        append("${"%.1f".format(summary.totalKwh)}kWh")
                                    }
                                },
                                style = MaterialTheme.typography.labelMedium
                            )
                        } else {
                            Text(
                                text = "${"%.1f".format(summary.totalKwh)}kWh",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.statusGood
                            )
                        }
                    }
                    Box(
                        modifier = Modifier
                            .width(0.6.dp)
                            .height(14.dp)
                            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "百公里平均电耗",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = if (summary.totalDistanceKm > 0) "${"%.1f".format(summary.avgKwhPer100Km)} kWh/100km" else "--",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            // 2. 行程历史列表 / 空状态
            if (records.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .frostedGlassCard(shape = RoundedCornerShape(16.dp))
                            .border(glassCardBorder(), shape = RoundedCornerShape(16.dp))
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "暂无行程记录",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "当车辆完成行驶并挂入 P 挡驻车后，系统将自动比对里程表，在此生成行程记账单。",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    groupedRecords.forEach { (monthKey, monthTrips) ->
                        val isExpanded = expandedMonths.contains(monthKey)
                        val monthTitle = if (monthKey.startsWith(currentYear)) {
                            monthKey.substringAfter("年")
                        } else {
                            monthKey
                        }
                        val monthDistance = monthTrips.sumOf { it.distanceKm }
                        val monthCost = monthTrips.sumOf { it.estimatedCostYuan }

                        // 月度可折叠小计标头
                        item(key = "header_$monthKey") {
                            val caretRotation by animateFloatAsState(
                                targetValue = if (isExpanded) 90f else 0f,
                                label = "caret_$monthKey"
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 6.dp, bottom = 2.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        expandedMonths = if (isExpanded) {
                                            expandedMonths - monthKey
                                        } else {
                                            expandedMonths + monthKey
                                        }
                                    }
                                    .padding(horizontal = 4.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_phosphor_caret_right),
                                        contentDescription = if (isExpanded) "收起" else "展开",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier
                                            .size(13.dp)
                                            .graphicsLayer { rotationZ = caretRotation }
                                    )
                                    Text(
                                        text = monthTitle,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Surface(
                                        shape = RoundedCornerShape(percent = 50),
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)
                                    ) {
                                        Text(
                                            text = "共${monthTrips.size}笔",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = "${monthDistance.toInt()}km",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.statusGood
                                    )
                                    Text(
                                        text = "·",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "¥${"%.2f".format(monthCost)}",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.statusWarn
                                    )
                                }
                            }
                        }

                        if (isExpanded) {
                            items(monthTrips, key = { it.id }) { record ->
                                TripRecordCard(
                                    record = record,
                                    pricePerKwh = pricePerKwh,
                                    onDelete = { recordToDelete = record }
                                )
                            }
                        }
                    }
                    if (records.isNotEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "清空所有行程记录",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .clickable { showClearAllDialog = true }
                                        .padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // 首次进入行程账本：初始化参数确认弹窗（预填官方数据 + 引导修改）
    if (showInitDialog) {
        var tempCapacityStr by remember {
            mutableStateOf(String.format(Locale.US, "%.1f", defaultCapacity))
        }
        var tempPriceStr by remember {
            mutableStateOf(String.format(Locale.US, "%.2f", defaultPrice))
        }
        AlertDialog(
            onDismissRequest = {
                // 点击弹窗外，默认保存预置参数并关闭，不再重复弹窗
                store.setTripLogParamsConfigured(vin, true)
                showInitDialog = false
            },
            title = {
                Text(
                    text = "确认行程记账参数",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "系统已根据您的车型自动预填官方电池容量与推荐电价，用于计算单次出行的电耗与花费：",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = tempCapacityStr,
                        onValueChange = { input ->
                            val cleaned = input.filter { it.isDigit() || it == '.' }
                            val parts = cleaned.split('.')
                            if (parts.size <= 2) {
                                val decimals = parts.getOrNull(1)
                                if (decimals == null || decimals.length <= 1) {
                                    tempCapacityStr = cleaned
                                }
                            }
                        },
                        label = { Text("动力电池容量 (kWh)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        trailingIcon = { Text("kWh", style = MaterialTheme.typography.labelSmall) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = tempPriceStr,
                        onValueChange = { input ->
                            val cleaned = input.filter { it.isDigit() || it == '.' }
                            val parts = cleaned.split('.')
                            if (parts.size <= 2) {
                                val decimals = parts.getOrNull(1)
                                if (decimals == null || decimals.length <= 2) {
                                    tempPriceStr = cleaned
                                }
                            }
                        },
                        label = { Text("充电电价 (元/kWh)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        trailingIcon = { Text("元/kWh", style = MaterialTheme.typography.labelSmall) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)
                    ) {
                        Text(
                            text = "💡 提示：后续可随时在「设置 ➔ 行程记账设置」中重新修改或校准。",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val cap = tempCapacityStr.toDoubleOrNull() ?: defaultCapacity
                        val p = tempPriceStr.toDoubleOrNull() ?: defaultPrice
                        store.saveBatteryCapacityKwh(vin, cap)
                        store.saveElectricityPrice(p)
                        store.setTripLogParamsConfigured(vin, true)
                        batteryCapacityKwh = cap
                        pricePerKwh = p
                        // 重新刷新现有记录的花费预估与电耗
                        val updated = store.loadRecords(vin).map { r ->
                            if (r.type == TripType.DRIVING && !r.chargedDuringTrip) {
                                val socDelta = (r.startSoc - r.endSoc).coerceAtLeast(0.0)
                                val consumed = (socDelta / 100.0) * cap
                                val roundedConsumed = if (consumed > 0.001) maxOf(0.1, (consumed * 10.0).roundToInt() / 10.0) else 0.0
                                val roundedAvg = if (r.distanceKm > 0 && roundedConsumed > 0.0) {
                                    ((roundedConsumed / r.distanceKm) * 100.0 * 10.0).roundToInt() / 10.0
                                } else 0.0
                                val roundedCost = if (roundedConsumed > 0.0) {
                                    maxOf(0.01, (roundedConsumed * p * 100.0).roundToInt() / 100.0)
                                } else 0.0
                                r.copy(
                                    consumedKwh = roundedConsumed,
                                    avgKwhPer100Km = roundedAvg,
                                    estimatedCostYuan = roundedCost
                                )
                            } else r
                        }
                        store.saveRecords(vin, updated)
                        records = updated
                        showInitDialog = false
                    }
                ) {
                    Text("确认并启用")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        store.setTripLogParamsConfigured(vin, true)
                        showInitDialog = false
                    }
                ) {
                    Text("稍后设置")
                }
            }
        )
    }

    // 清空全部行程确认弹窗
    if (showClearAllDialog) {
        AlertDialog(
            onDismissRequest = { showClearAllDialog = false },
            title = { Text("清空所有行程？", fontWeight = FontWeight.Bold) },
            text = { Text("将永久清空当前车辆的所有历史记账单，此操作不可撤销。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        store.clearRecords(vin)
                        records = emptyList()
                        showClearAllDialog = false
                    }
                ) {
                    Text("全部清空", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearAllDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    // 删除单次行程确认弹窗
    recordToDelete?.let { record ->
        AlertDialog(
            onDismissRequest = { recordToDelete = null },
            title = { Text("删除该行程？") },
            text = { Text("将永久移除本次 ${"%.1f".format(record.distanceKm)}km 的行驶账单记录。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        store.deleteRecord(vin, record.id)
                        records = store.loadRecords(vin)
                        recordToDelete = null
                    }
                ) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { recordToDelete = null }) {
                    Text("取消")
                }
            }
        )
    }
}

@Composable
private fun TripRecordCard(
    record: TripRecord,
    pricePerKwh: Double,
    onDelete: () -> Unit
) {
    val isDark = LocalAppDarkTheme.current
    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val dateFormat = remember { SimpleDateFormat("MM月dd日", Locale.getDefault()) }
    val dateStr = remember(record.endTimeEpochMs) {
        val calNow = java.util.Calendar.getInstance()
        val calRecord = java.util.Calendar.getInstance().apply { timeInMillis = record.endTimeEpochMs }
        val isSameYear = calNow.get(java.util.Calendar.YEAR) == calRecord.get(java.util.Calendar.YEAR)
        val dayDiff = calNow.get(java.util.Calendar.DAY_OF_YEAR) - calRecord.get(java.util.Calendar.DAY_OF_YEAR)
        when {
            isSameYear && dayDiff == 0 -> "今天"
            isSameYear && dayDiff == 1 -> "昨天"
            else -> dateFormat.format(Date(record.endTimeEpochMs))
        }
    }
    val startTimeStr = remember(record.startTimeEpochMs) { timeFormat.format(Date(record.startTimeEpochMs)) }
    val endTimeStr = remember(record.endTimeEpochMs) { timeFormat.format(Date(record.endTimeEpochMs)) }

    // 计算本次出行历时（若时间跨度大于4小时且行驶小于50km，多为隔夜驻车未常驻对账，自动降级为到达时间）
    val durationMinutes = remember(record.startTimeEpochMs, record.endTimeEpochMs) {
        val diff = (record.endTimeEpochMs - record.startTimeEpochMs).coerceAtLeast(0L)
        (diff / 60000L).toInt()
    }
    val isOvernightSpan = durationMinutes > 240 && (durationMinutes > 0 && (record.distanceKm / (durationMinutes / 60.0)) < 15.0)
    val showDuration = !isOvernightSpan && durationMinutes > 0
    val durationText = when {
        durationMinutes <= 0 -> "<1分钟"
        durationMinutes < 60 -> "${durationMinutes}分钟"
        else -> "${durationMinutes / 60}小时${durationMinutes % 60}分"
    }
    // 计算本次行程平均时速
    val avgSpeedKmh = remember(record.distanceKm, durationMinutes) {
        if (durationMinutes > 0) {
            val hours = durationMinutes / 60.0
            (record.distanceKm / hours).roundToInt()
        } else 0
    }
    val timeDisplayStr = if (isOvernightSpan) {
        "$dateStr · $endTimeStr 到达"
    } else {
        "$dateStr · $startTimeStr ➔ $endTimeStr"
    }

    val fuelConsumed = record.fuelConsumedLiters
    val hasFuelConsumed = fuelConsumed != null && fuelConsumed > 0.0
    val avgFuelLiters = if (hasFuelConsumed && record.distanceKm > 0) {
        ((fuelConsumed!! / record.distanceKm) * 100.0 * 10.0).roundToInt() / 10.0
    } else 0.0

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .frostedGlassCard(
                shape = RoundedCornerShape(16.dp),
                topSpecularLine = true
            )
            .border(glassCardBorder(), shape = RoundedCornerShape(16.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // 1. 顶部路线大字标题 + 删除按钮
        val hasLocation = !record.startLocation.isNullOrBlank() || !record.endLocation.isNullOrBlank()
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (hasLocation) {
                    val isRoundTrip = !record.startLocation.isNullOrBlank() &&
                        !record.endLocation.isNullOrBlank() &&
                        record.startLocation.trim() == record.endLocation.trim()
                    if (isRoundTrip) {
                        Text(
                            text = record.startLocation ?: "",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                        Surface(
                            shape = RoundedCornerShape(3.dp),
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                        ) {
                            Text(
                                text = "短途往返",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    } else {
                        Text(
                            text = record.startLocation ?: "起始地",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Text(
                            text = "➔",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                        Text(
                            text = record.endLocation ?: "目的地",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                    }
                } else {
                    Text(
                        text = "单次出行行程",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(22.dp)
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_phosphor_trash),
                    contentDescription = "删除记录",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier.size(14.dp)
                )
            }
        }

        // 2. 次级标签行：出行时间 + 历时 / 均速胶囊 + 补能标记
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = timeDisplayStr,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (showDuration) {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)
                ) {
                    val speedSnippet = if (avgSpeedKmh in 1..200) " · 均速${avgSpeedKmh}km/h" else ""
                    Text(
                        text = "历时$durationText$speedSnippet",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                    )
                }
            }
            if (record.chargedDuringTrip) {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = MaterialTheme.statusGood.copy(alpha = 0.12f)
                ) {
                    Text(
                        text = "途中有补能",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.statusGood,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                    )
                }
            }
            if (hasFuelConsumed) {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = Color(0xFFFF9800).copy(alpha = 0.14f)
                ) {
                    Text(
                        text = "⛽ 增程介入 · ${"%.1f".format(fuelConsumed)}L",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFF9800),
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                    )
                }
            }
        }

        // 细晶线微晶分割线
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(0.6.dp)
                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
        )

        // 3. 核心 3 列指标仪表盘（里程 | 百公里电耗/能耗 | 预估花费）
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 列 1: 里程
            Column {
                Text(
                    text = "行驶里程",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(1.dp))
                Text(
                    text = buildAnnotatedString {
                        withStyle(SpanStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)) {
                            append("%.1f".format(record.distanceKm))
                        }
                        withStyle(SpanStyle(fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)) {
                            append("km")
                        }
                    }
                )
            }

            // 列 2: 百公里电耗 / 能耗
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = if (hasFuelConsumed) "百公里能耗" else "百公里电耗",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(1.dp))
                if (hasFuelConsumed) {
                    Text(
                        text = buildAnnotatedString {
                            withStyle(SpanStyle(fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFF9800))) {
                                append("${"%.1f".format(avgFuelLiters)}L")
                            }
                            append("+")
                            withStyle(SpanStyle(fontSize = 15.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.statusGood)) {
                                append(if (record.avgKwhPer100Km > 0) "%.1fkWh".format(record.avgKwhPer100Km) else "0kWh")
                            }
                        }
                    )
                } else {
                    Text(
                        text = buildAnnotatedString {
                            withStyle(SpanStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.statusGood)) {
                                append(if (record.avgKwhPer100Km > 0) "%.1f".format(record.avgKwhPer100Km) else "--")
                            }
                            withStyle(SpanStyle(fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)) {
                                append("kWh")
                            }
                        }
                    )
                }
            }

            // 列 3: 预估花费 (及消耗电量/油量)
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = if (hasFuelConsumed) "预估总花费" else "预估花费",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(1.dp))
                if (hasFuelConsumed) {
                    Text(
                        text = buildAnnotatedString {
                            withStyle(SpanStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.statusWarn)) {
                                append("¥${"%.2f".format(record.estimatedCostYuan)}")
                            }
                            withStyle(SpanStyle(fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))) {
                                append("/")
                            }
                            withStyle(SpanStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFF9800))) {
                                append("${"%.1f".format(fuelConsumed)}L")
                            }
                            append("+")
                            withStyle(SpanStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.statusGood)) {
                                append("%.1f".format(record.consumedKwh))
                            }
                            withStyle(SpanStyle(fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)) {
                                append("kWh")
                            }
                        }
                    )
                } else {
                    Text(
                        text = buildAnnotatedString {
                            withStyle(SpanStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.statusWarn)) {
                                append("¥${"%.2f".format(record.estimatedCostYuan)}")
                            }
                            withStyle(SpanStyle(fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))) {
                                append("/")
                            }
                            withStyle(SpanStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.statusGood)) {
                                append("%.1f".format(record.consumedKwh))
                            }
                            withStyle(SpanStyle(fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)) {
                                append("kWh")
                            }
                        }
                    )
                }
            }
        }
    }
}
