// [FEATURE: CHARGE_HISTORY] - 官方充电记录界面 (基于抓包接口与设计规范重构)
package com.leapauto.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.leapauto.app.LeapmotorApi
import com.leapauto.app.R
import com.leapauto.app.SessionStore
import com.leapauto.app.charge.ChargeDetailItem
import com.leapauto.app.charge.ChargePeriodType
import com.leapauto.app.charge.ChargeStatisticPoint
import com.leapauto.app.charge.ChargeStatisticResult
import com.leapauto.app.ui.theme.LocalAppDarkTheme
import com.leapauto.app.ui.theme.glassInsetSurface
import com.leapauto.app.ui.theme.statusGood
import com.leapauto.app.ui.theme.statusWarn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar

@Composable
fun ChargeHistoryScreen(
    vin: String,
    onBack: () -> Unit = {},
    apiProvider: (() -> LeapmotorApi?)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val effectiveApiProvider = apiProvider ?: remember(context) {
        { runCatching { LeapmotorApi(SessionStore(context).load()) }.getOrNull() }
    }
    val isDark = LocalAppDarkTheme.current
    val currentCal = remember { Calendar.getInstance() }
    val thisYear = remember { currentCal.get(Calendar.YEAR) }
    val thisMonth = remember { currentCal.get(Calendar.MONTH) + 1 }

    var selectedPeriod by remember { mutableStateOf(ChargePeriodType.RECENT_7_DAYS) }
    var selectedYear by remember { mutableIntStateOf(thisYear) }
    var selectedMonth by remember { mutableIntStateOf(thisMonth) }

    var statsLoading by remember { mutableStateOf(true) }
    var statsResult by remember { mutableStateOf<ChargeStatisticResult?>(null) }
    var statsError by remember { mutableStateOf<String?>(null) }

    var selectedPointIndex by remember { mutableIntStateOf(-1) }

    var detailsLoading by remember { mutableStateOf(false) }
    var detailsList by remember { mutableStateOf<List<ChargeDetailItem>>(emptyList()) }
    var detailsError by remember { mutableStateOf<String?>(null) }
    var focusedLabel by remember { mutableStateOf("") }

    // 默认展开首条详情
    var expandedItems by remember { mutableStateOf(setOf(0)) }

    var showYearDialog by remember { mutableStateOf(false) }
    var showMonthDialog by remember { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()

    // 聚焦拉取选定单天（近7天/月视图）或单月（年视图）的明细记录，绝不堆砌多余记录
    suspend fun fetchDetailsForTarget(
        api: LeapmotorApi,
        point: ChargeStatisticPoint?,
        period: ChargePeriodType,
        year: Int,
        month: Int
    ) {
        detailsLoading = true
        detailsError = null
        expandedItems = setOf(0)

        val (startStr, endStr) = when (period) {
            ChargePeriodType.RECENT_7_DAYS,
            ChargePeriodType.MONTH -> {
                val dayStr = point?.fullDate?.take(10) ?: String.format(java.util.Locale.US, "%04d-%02d-01", year, month)
                val cal = Calendar.getInstance().apply {
                    val parts = dayStr.split("-")
                    if (parts.size == 3) {
                        set(Calendar.YEAR, parts[0].toInt())
                        set(Calendar.MONTH, parts[1].toInt() - 1)
                        set(Calendar.DAY_OF_MONTH, parts[2].toInt())
                        add(Calendar.DAY_OF_MONTH, 1)
                    }
                }
                val nextDay = String.format(java.util.Locale.US, "%04d-%02d-%02d", cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH))
                focusedLabel = if (period == ChargePeriodType.MONTH) "${point?.dateLabel ?: dayStr.takeLast(2)}日" else (point?.dateLabel ?: dayStr)
                Pair(dayStr, nextDay)
            }
            ChargePeriodType.YEAR -> {
                val targetMonth = if (point != null && point.fullDate.length >= 7) {
                    point.fullDate.substring(5, 7).toIntOrNull() ?: month
                } else {
                    month
                }
                val cal = Calendar.getInstance().apply {
                    set(Calendar.YEAR, year)
                    set(Calendar.MONTH, targetMonth - 1)
                    set(Calendar.DAY_OF_MONTH, 1)
                }
                val startDay = String.format(java.util.Locale.US, "%04d-%02d-01", year, targetMonth)
                val maxDay = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
                val endDay = String.format(java.util.Locale.US, "%04d-%02d-%02d", year, targetMonth, maxDay)
                focusedLabel = "${targetMonth}月"
                Pair(startDay, endDay)
            }
        }

        try {
            val details = withContext(Dispatchers.IO) {
                api.queryChargeDailyDetails(
                    vin = vin,
                    startTime = startStr,
                    endTime = endStr
                )
            }
            detailsList = details
            detailsLoading = false
        } catch (e: Exception) {
            detailsLoading = false
            detailsError = e.message ?: "拉取充电详情失败"
        }
    }

    // 异步加载统计数据，并自动聚焦至距离今天最近的有充电记录的单天或单月
    LaunchedEffect(vin, selectedPeriod, selectedYear, selectedMonth) {
        val api = effectiveApiProvider.invoke()
        if (api == null || vin.isBlank()) {
            statsLoading = false
            statsError = "车辆或账号未就绪"
            return@LaunchedEffect
        }
        statsLoading = true
        statsError = null
        selectedPointIndex = -1

        try {
            val stat = withContext(Dispatchers.IO) {
                api.queryChargeStatistics(
                    vin = vin,
                    periodType = selectedPeriod,
                    year = selectedYear,
                    month = selectedMonth
                )
            }
            statsResult = stat
            statsLoading = false

            // 计算稠密补齐点集
            val dense = stat.densePoints(selectedPeriod, selectedYear, selectedMonth)

            // 优先查找距离今天最近的有充电记录的柱子
            val targetIdx = dense.indexOfLast { it.energyKwh > 0 }.takeIf { it >= 0 }
                ?: when (selectedPeriod) {
                    ChargePeriodType.RECENT_7_DAYS -> dense.size - 1
                    ChargePeriodType.MONTH -> if (selectedYear == thisYear && selectedMonth == thisMonth) {
                        (currentCal.get(Calendar.DAY_OF_MONTH) - 1).coerceIn(0, dense.size - 1)
                    } else 0
                    ChargePeriodType.YEAR -> if (selectedYear == thisYear) {
                        (thisMonth - 1).coerceIn(0, dense.size - 1)
                    } else 0
                }

            selectedPointIndex = targetIdx
            val defaultPoint = dense.getOrNull(targetIdx)

            // 仅拉取该单天或单月的明细
            fetchDetailsForTarget(api, defaultPoint, selectedPeriod, selectedYear, selectedMonth)
        } catch (e: Exception) {
            statsLoading = false
            statsError = e.message ?: "拉取充电记录失败"
            detailsLoading = false
        }
    }

    // 点击具体柱子时，精准切换至选定单天或单月的明细
    fun onSelectPoint(point: ChargeStatisticPoint, index: Int) {
        selectedPointIndex = index
        val api = effectiveApiProvider.invoke() ?: return
        coroutineScope.launch {
            fetchDetailsForTarget(api, point, selectedPeriod, selectedYear, selectedMonth)
        }
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp, vertical = 6.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1. 周期分段切换器【近7天】【月】【年】
            PeriodSegmentedControl(
                selectedPeriod = selectedPeriod,
                onPeriodSelected = { selectedPeriod = it }
            )

            // 2. 充电统计卡片（含大指标与能量柱状图）
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .frostedGlassCard(
                        shape = RoundedCornerShape(16.dp),
                        topSpecularLine = true
                    )
                    .border(glassCardBorder(), shape = RoundedCornerShape(16.dp))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 标题行与日期切换下拉
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "充电统计",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    // 周期与时间选择器
                    when (selectedPeriod) {
                        ChargePeriodType.RECENT_7_DAYS -> {
                            Text(
                                text = "近7天",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        ChargePeriodType.MONTH -> {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(2.dp),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable { showMonthDialog = true }
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "${selectedYear}年${selectedMonth}月",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Icon(
                                    painter = painterResource(R.drawable.ic_phosphor_caret_right),
                                    contentDescription = "选择月份",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(12.dp).rotate(90f)
                                )
                            }
                        }
                        ChargePeriodType.YEAR -> {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(2.dp),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable { showYearDialog = true }
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "${selectedYear}年",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Icon(
                                    painter = painterResource(R.drawable.ic_phosphor_caret_right),
                                    contentDescription = "选择年份",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(12.dp).rotate(90f)
                                )
                            }
                        }
                    }
                }

                Text(
                    text = "充电统计数据更新存在延时，请以页面实际展示为准",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                )

                // 核心三大指标（累计充电、充电次数、快充占比）
                val totalEnergy = statsResult?.totalEnergyKwh ?: 0.0
                val totalCount = statsResult?.totalCounts ?: 0
                val fastPercent = statsResult?.fastChargePercent ?: 0.0

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.glassInsetSurface)
                        .padding(vertical = 12.dp, horizontal = 8.dp),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 指标 1: 累计充电
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = buildAnnotatedString {
                                withStyle(SpanStyle(fontSize = 24.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)) {
                                    append(if (totalEnergy > 0) String.format(java.util.Locale.US, "%.1f", totalEnergy) else "0")
                                }
                                withStyle(SpanStyle(fontSize = 12.sp, fontWeight = FontWeight.Normal, color = MaterialTheme.colorScheme.onSurfaceVariant)) {
                                    append(" 度")
                                }
                            }
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = "累计充电",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // 指标 2: 充电次数
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = buildAnnotatedString {
                                withStyle(SpanStyle(fontSize = 24.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)) {
                                    append("$totalCount")
                                }
                                withStyle(SpanStyle(fontSize = 12.sp, fontWeight = FontWeight.Normal, color = MaterialTheme.colorScheme.onSurfaceVariant)) {
                                    append(" 次")
                                }
                            }
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = "充电次数",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // 指标 3: 快充占比
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = buildAnnotatedString {
                                withStyle(SpanStyle(fontSize = 24.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)) {
                                    append(if (fastPercent > 0) "${fastPercent.toInt()}" else "0")
                                }
                                withStyle(SpanStyle(fontSize = 12.sp, fontWeight = FontWeight.Normal, color = MaterialTheme.colorScheme.onSurfaceVariant)) {
                                    append(" %")
                                }
                            }
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = "快充占比",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // 交互式微晶能量柱状图
                if (statsLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = MaterialTheme.statusGood,
                            strokeWidth = 2.dp
                        )
                    }
                } else if (statsResult != null) {
                    val dense = statsResult!!.densePoints(selectedPeriod, selectedYear, selectedMonth)
                    ChargeHistogram(
                        points = dense,
                        selectedIndex = selectedPointIndex,
                        onSelect = { pt, idx -> onSelectPoint(pt, idx) }
                    )
                    if (totalCount == 0) {
                        Text(
                            text = "当前时段暂无充电数据\n(注：受零跑官方权限限制，仅车主主账号有权查看该车辆的充电记录)",
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp)
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            statsError ?: "暂无统计数据",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // 3. 充电详情卡片
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .frostedGlassCard(
                        shape = RoundedCornerShape(16.dp),
                        topSpecularLine = false
                    )
                    .border(glassCardBorder(), shape = RoundedCornerShape(16.dp))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "充电详情",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    if (focusedLabel.isNotBlank()) {
                        Text(
                            text = focusedLabel,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (detailsLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = MaterialTheme.statusGood,
                            strokeWidth = 2.dp
                        )
                    }
                } else if (detailsList.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        detailsList.forEachIndexed { index, item ->
                            val isExpanded = expandedItems.contains(index)
                            val arrowRotation by animateFloatAsState(
                                targetValue = if (isExpanded) 180f else 0f,
                                animationSpec = tween(200),
                                label = "chargeDetailArrow"
                            )

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.glassInsetSurface)
                                    .padding(12.dp)
                            ) {
                                // 折叠头部行
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(6.dp))
                                        .clickable {
                                            expandedItems = if (isExpanded) {
                                                expandedItems - index
                                            } else {
                                                expandedItems + index
                                            }
                                        },
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = item.timeRangeFormatted,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Icon(
                                        painter = painterResource(R.drawable.ic_phosphor_caret_right),
                                        contentDescription = if (isExpanded) "折叠" else "展开",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier
                                            .size(16.dp)
                                            .rotate(if (isExpanded) 270f else 90f)
                                    )
                                }

                                // 展开明细卡片
                                AnimatedVisibility(visible = isExpanded) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 10.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        // 充入电量
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                "充入电量",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Text(
                                                "${String.format(java.util.Locale.US, "%.1f", item.energyKwh)} kWh",
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        }

                                        // 充电时长
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                "充电时长",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Text(
                                                item.durationFormatted,
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        }

                                        // 充电类型
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                "充电类型",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            // 快充 / 慢充微晶胶囊
                                            val tagBg = if (item.isFastCharge) MaterialTheme.statusGood.copy(alpha = 0.16f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                                            val tagColor = if (item.isFastCharge) MaterialTheme.statusGood else MaterialTheme.colorScheme.onSurfaceVariant
                                            val tagText = if (item.isFastCharge) "⚡ 快充" else "⚡ 慢充"
                                            Surface(
                                                shape = RoundedCornerShape(percent = 50),
                                                color = tagBg,
                                                border = BorderStroke(0.6.dp, tagColor.copy(alpha = 0.35f))
                                            ) {
                                                Text(
                                                    text = tagText,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = tagColor,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 18.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "当前时段暂无充电详情记录\n(注：受零跑官方权限限制，仅车主主账号有权查看该车辆的充电记录)",
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                        )
                    }
                }
            }
        }
    }

    // 年份选择弹窗
    if (showYearDialog) {
        val years = (thisYear - 3..thisYear).toList().reversed()
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showYearDialog = false },
            title = { Text("选择统计年份", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    years.forEach { y ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (y == selectedYear) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else Color.Transparent)
                                .clickable {
                                    selectedYear = y
                                    showYearDialog = false
                                }
                                .padding(vertical = 12.dp, horizontal = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("${y}年", fontWeight = if (y == selectedYear) FontWeight.Bold else FontWeight.Normal)
                            if (y == selectedYear) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_phosphor_check),
                                    contentDescription = null,
                                    tint = MaterialTheme.statusGood,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showYearDialog = false }) { Text("取消") }
            }
        )
    }

    // 月份选择弹窗
    if (showMonthDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showMonthDialog = false },
            title = { Text("选择统计月份", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // 年份切换行
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { selectedYear-- }) {
                            Icon(painterResource(R.drawable.ic_phosphor_arrow_left), contentDescription = "上一年")
                        }
                        Text("${selectedYear}年", fontWeight = FontWeight.Bold)
                        IconButton(
                            onClick = { if (selectedYear < thisYear) selectedYear++ },
                            enabled = selectedYear < thisYear
                        ) {
                            Icon(painterResource(R.drawable.ic_phosphor_caret_right), contentDescription = "下一年")
                        }
                    }

                    // 12 个月网格选择
                    val maxMonth = if (selectedYear == thisYear) thisMonth else 12
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        for (row in 0..3) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                for (col in 1..3) {
                                    val m = row * 3 + col
                                    val enabled = m <= maxMonth
                                    val isCur = m == selectedMonth
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (isCur) MaterialTheme.statusGood else if (enabled) MaterialTheme.glassInsetSurface else MaterialTheme.glassInsetSurface.copy(alpha = 0.4f),
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable(enabled = enabled) {
                                                selectedMonth = m
                                                showMonthDialog = false
                                            }
                                    ) {
                                        Text(
                                            text = "${m}月",
                                            textAlign = TextAlign.Center,
                                            fontWeight = if (isCur) FontWeight.Bold else FontWeight.Medium,
                                            color = if (isCur) Color.White else if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                            modifier = Modifier.padding(vertical = 10.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showMonthDialog = false }) { Text("取消") }
            }
        )
    }
}

/** 周期切换胶囊分段控制器 */
@Composable
private fun PeriodSegmentedControl(
    selectedPeriod: ChargePeriodType,
    onPeriodSelected: (ChargePeriodType) -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = LocalAppDarkTheme.current
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(40.dp),
        shape = RoundedCornerShape(percent = 50),
        color = if (isDark) Color(0xFF222222) else Color(0xFFEAEAEA),
        border = BorderStroke(0.6.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ChargePeriodType.entries.forEach { period ->
                val selected = period == selectedPeriod
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(percent = 50))
                        .background(
                            if (selected) {
                                if (isDark) Color(0xFF333333) else Color.White
                            } else Color.Transparent
                        )
                        .clickable { onPeriodSelected(period) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = period.label,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/** 交互式微晶能量柱状图 */
@Composable
private fun ChargeHistogram(
    points: List<ChargeStatisticPoint>,
    selectedIndex: Int,
    onSelect: (ChargeStatisticPoint, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = LocalAppDarkTheme.current
    if (points.isEmpty()) return

    val maxVal = points.maxOfOrNull { it.energyKwh }?.takeIf { it > 0.0 } ?: 1.0
    val isScrollable = points.size > 12 // 月视图 (28~31天) 启用横向滑动，近7天与年视图整宽平铺

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        // 虚线上方直接显示一体化刻度 "54.3 kWh"，虚线不再分割单位与数值
        val displayMaxStr = if (maxVal > 0 && maxVal != 1.0) {
            String.format(java.util.Locale.US, "%.1f kWh", maxVal)
        } else {
            "0.0 kWh"
        }
        Text(
            text = displayMaxStr,
            style = MaterialTheme.typography.labelSmall,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 2.dp, bottom = 2.dp)
        )

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(130.dp)
        ) {
            val totalH = maxHeight
            val barAreaH = (totalH - 24.dp).coerceAtLeast(60.dp)

            // 1. 虚线基准线 Canvas (绘制在刻度文字下方)
            Canvas(modifier = Modifier.fillMaxSize()) {
                val lineY = 2.dp.toPx()
                drawLine(
                    color = if (isDark) Color.White.copy(alpha = 0.20f) else Color.Black.copy(alpha = 0.15f),
                    start = Offset(0f, lineY),
                    end = Offset(size.width, lineY),
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))
                )
            }

            // 2. 柱条分布展示
            if (isScrollable) {
                // 月视图：横向平滑滚动，每根柱槽 28dp 充足宽度，彻底杜绝两位数换行
                val scrollState = rememberScrollState()

                // 进入时自动滚动至最新有数据的位置或当前选中的柱子
                LaunchedEffect(points, selectedIndex) {
                    val targetIdx = if (selectedIndex >= 0) selectedIndex else points.indexOfLast { it.energyKwh > 0 }
                    if (targetIdx > 4) {
                        scrollState.scrollTo((targetIdx * 30 * 2).coerceAtLeast(0))
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .horizontalScroll(scrollState),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    points.forEachIndexed { index, pt ->
                        val hasEnergy = pt.energyKwh > 0
                        val isSelected = index == selectedIndex || (selectedIndex == -1 && hasEnergy && index == points.indexOfLast { it.energyKwh > 0 })
                        val ratio = (pt.energyKwh / maxVal).toFloat().coerceIn(0f, 1f)
                        val barH = if (hasEnergy) (barAreaH * ratio).coerceAtLeast(6.dp) else 0.dp

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .width(28.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .clickable { onSelect(pt, index) }
                                .padding(bottom = 2.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .height(barAreaH)
                                    .width(8.dp),
                                contentAlignment = Alignment.BottomCenter
                            ) {
                                // 没充电的天 (0度) 完全不绘制能量条，保持纯净空白
                                if (hasEnergy) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(barH)
                                            .clip(RoundedCornerShape(percent = 50))
                                            .background(
                                                if (isSelected) {
                                                    Brush.verticalGradient(
                                                        listOf(MaterialTheme.statusGood, MaterialTheme.statusGood.copy(alpha = 0.75f))
                                                    )
                                                } else {
                                                    Brush.verticalGradient(
                                                        listOf(MaterialTheme.statusGood.copy(alpha = 0.40f), MaterialTheme.statusGood.copy(alpha = 0.20f))
                                                    )
                                                }
                                            )
                                    )
                                }
                            }

                            Spacer(Modifier.height(4.dp))

                            Text(
                                text = pt.dateLabel,
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 10.sp,
                                maxLines = 1,
                                softWrap = false,
                                textAlign = TextAlign.Center,
                                fontWeight = if (isSelected && hasEnergy) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected && hasEnergy) MaterialTheme.statusGood else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.width(28.dp)
                            )
                        }
                    }
                }
            } else {
                // 近7天与年视图：一屏自适应分布
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.Bottom
                ) {
                    points.forEachIndexed { index, pt ->
                        val hasEnergy = pt.energyKwh > 0
                        val isSelected = index == selectedIndex || (selectedIndex == -1 && hasEnergy && index == points.indexOfLast { it.energyKwh > 0 })
                        val ratio = (pt.energyKwh / maxVal).toFloat().coerceIn(0f, 1f)
                        val barH = if (hasEnergy) (barAreaH * ratio).coerceAtLeast(6.dp) else 0.dp

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(6.dp))
                                .clickable { onSelect(pt, index) }
                                .padding(bottom = 2.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .height(barAreaH)
                                    .width(if (points.size <= 7) 12.dp else 8.dp),
                                contentAlignment = Alignment.BottomCenter
                            ) {
                                // 没充电的天/月完全不绘制能量条
                                if (hasEnergy) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(barH)
                                            .clip(RoundedCornerShape(percent = 50))
                                            .background(
                                                if (isSelected) {
                                                    Brush.verticalGradient(
                                                        listOf(MaterialTheme.statusGood, MaterialTheme.statusGood.copy(alpha = 0.75f))
                                                    )
                                                } else {
                                                    Brush.verticalGradient(
                                                        listOf(MaterialTheme.statusGood.copy(alpha = 0.40f), MaterialTheme.statusGood.copy(alpha = 0.20f))
                                                    )
                                                }
                                            )
                                    )
                                }
                            }

                            Spacer(Modifier.height(4.dp))

                            Text(
                                text = pt.dateLabel,
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 10.sp,
                                maxLines = 1,
                                softWrap = false,
                                textAlign = TextAlign.Center,
                                fontWeight = if (isSelected && hasEnergy) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected && hasEnergy) MaterialTheme.statusGood else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}
