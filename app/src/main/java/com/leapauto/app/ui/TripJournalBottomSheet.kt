package com.leapauto.app.ui

import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.leapauto.app.R
import com.leapauto.app.trip.DayTripGroup
import com.leapauto.app.trip.TripGroupHelper
import com.leapauto.app.trip.TripRecord
import com.leapauto.app.ui.theme.glassInsetSurface
import com.leapauto.app.ui.theme.statusGood
import kotlinx.coroutines.launch

/**
 * 智能座舱级·全景自驾日记与行程大抽屉：
 * 1. 日历滑动胶囊栏：按天横滑切日（今天/昨天/前天），轻触秒级直达；
 * 2. 座舱仪表板：大字里程总览 + 用时/趟数/电耗多维微晶徽章；
 * 3. 路线光轨时间轴：起点绿光 ➔ 垂直发光轨迹线 ➔ 终点旗标，自驾代入感十足；
 * 4. 视觉降噪：收敛的微晶垃圾桶图标，两段式大视口平滑拉伸至屏幕顶端网孔下方。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripJournalBottomSheet(
    trips: List<TripRecord>,
    onClearTrips: () -> Unit,
    onDismissRequest: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    val coroutineScope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    var showClearConfirmDialog by remember { mutableStateOf(false) }

    val dayGroups = remember(trips) { TripGroupHelper.groupByDate(trips) }
    val pagerState = rememberPagerState(initialPage = 0) { dayGroups.size.coerceAtLeast(1) }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
        ) {
            val fullSheetHeight = maxHeight
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(fullSheetHeight)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // 1. 顶部标题栏：精致路线图标 + 标题 + 右侧收敛的圆形垃圾桶操作钮
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                            modifier = Modifier.size(32.dp)
                        ) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_trip_route),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(17.dp)
                                )
                            }
                        }
                        Text(
                            text = "行驶记录",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    if (trips.isNotEmpty()) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f),
                            border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                            modifier = Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .clickable { showClearConfirmDialog = true }
                        ) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_phosphor_trash),
                                    contentDescription = "清空行程记录",
                                    modifier = Modifier.size(15.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                if (dayGroups.isEmpty()) {
                    // 空数据居中提示：在半屏和全屏视口内均处于视觉中心，绝不沉底截断
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.TopCenter
                    ) {
                        Column(
                            modifier = Modifier.padding(top = (fullSheetHeight * 0.11f).coerceIn(36.dp, 80.dp)),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_trip_route),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.40f),
                                modifier = Modifier.size(46.dp)
                            )
                            Text(
                                text = "暂无行驶行程记录",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "开车出行挂 D/R 挡行驶后，停稳熄火将自动归档生成报告",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.70f)
                            )
                        }
                    }
                } else {
                    // 2. 日历滑动微晶胶囊栏 (按天横向排列，点击或横滑切日)
                    val currentDayIndex = pagerState.currentPage
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        itemsIndexed(dayGroups) { index, day ->
                            val isSelected = currentDayIndex == index
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f) else MaterialTheme.glassInsetSurface,
                                border = BorderStroke(
                                    0.7.dp,
                                    if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.50f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
                                ),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable {
                                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                                        coroutineScope.launch {
                                            pagerState.animateScrollToPage(index, animationSpec = tween(250))
                                        }
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    Text(
                                        text = "${day.dateLabel} · ${day.totalDistanceKm}km",
                                        fontSize = 12.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                    )
                                    if (isSelected) {
                                        Box(
                                            modifier = Modifier
                                                .size(4.dp)
                                                .clip(CircleShape)
                                                .background(MaterialTheme.colorScheme.primary)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // 3. 核心双维交互：HorizontalPager (按天横滑) + 内部 LazyColumn (当天明细上下滑动)
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        beyondViewportPageCount = 1
                    ) { pageIndex ->
                        val dayGroup = dayGroups.getOrNull(pageIndex)
                        if (dayGroup != null) {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                // 3.1 该日的专属全日驾驶仪表盘展卡
                                item(key = "summary_${dayGroup.dateKey}") {
                                    Surface(
                                        shape = RoundedCornerShape(16.dp),
                                        color = MaterialTheme.glassInsetSurface,
                                        border = BorderStroke(0.6.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 16.dp, vertical = 14.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column {
                                                Text(
                                                    text = "${dayGroup.dateLabel}累计行驶",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                Row(verticalAlignment = Alignment.Bottom) {
                                                    Text(
                                                        text = "${dayGroup.totalDistanceKm}",
                                                        fontSize = 30.sp,
                                                        lineHeight = 32.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = MaterialTheme.colorScheme.onSurface
                                                    )
                                                    Text(
                                                        text = " km",
                                                        fontSize = 13.sp,
                                                        fontWeight = FontWeight.SemiBold,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        modifier = Modifier.padding(bottom = 2.dp, start = 2.dp)
                                                    )
                                                }
                                            }

                                            Column(
                                                horizontalAlignment = Alignment.End,
                                                verticalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                    DashboardPillBadge(text = "⏱ ${dayGroup.totalDurationFormatted}")
                                                    DashboardPillBadge(text = "🚗 ${dayGroup.trips.size}趟")
                                                }
                                                if (dayGroup.totalSocDeltaPercent > 0.0) {
                                                    Surface(
                                                        shape = RoundedCornerShape(4.dp),
                                                        color = MaterialTheme.statusGood.copy(alpha = 0.08f),
                                                        border = BorderStroke(0.5.dp, MaterialTheme.statusGood.copy(alpha = 0.30f))
                                                    ) {
                                                        Text(
                                                            text = "累计耗电 -${dayGroup.totalSocDeltaPercent}%" +
                                                                (dayGroup.avgEnergyConsumption?.let { " · 均耗 $it" } ?: ""),
                                                            fontSize = 10.sp,
                                                            lineHeight = 11.sp,
                                                            fontWeight = FontWeight.SemiBold,
                                                            color = MaterialTheme.statusGood,
                                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 0.5.dp)
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }

                                // 3.2 该日的多趟轨迹光轨单次行程流水
                                items(dayGroup.trips, key = { it.id }) { trip ->
                                    TripCardItem(trip)
                                }

                                item {
                                    Spacer(Modifier.height(16.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showClearConfirmDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showClearConfirmDialog = false },
            title = { Text("清空行驶记录", fontWeight = FontWeight.Bold) },
            text = { Text("确定清空当前车辆的所有历史行程记录吗？清空后无法找回。") },
            confirmButton = {
                Text(
                    text = "确认清空",
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clickable {
                            showClearConfirmDialog = false
                            onClearTrips()
                        }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                )
            },
            dismissButton = {
                Text(
                    text = "取消",
                    modifier = Modifier
                        .clickable { showClearConfirmDialog = false }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }
        )
    }
}

/** 顶部小标签徽章 */
@Composable
private fun DashboardPillBadge(text: String) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.50f),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.30f))
    ) {
        Text(
            text = text,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

/** 单条行程卡片：具备【出发🟢 ➔ 垂直光轨 ➔ 到达🏁】与能耗高光徽章 */
@Composable
private fun TripCardItem(trip: TripRecord) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.glassInsetSurface,
        border = BorderStroke(0.6.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.30f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            // 1. 卡片头部：时段 + 用时标签 + 右侧能耗高光徽章
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = trip.formattedTimeRange,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.60f)
                    ) {
                        Text(
                            text = trip.formattedDuration,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.5.dp)
                        )
                    }
                }

                // 右侧能耗高光胶囊徽章
                val energyText = if (trip.energyConsumptionKwhPer100Km != null) {
                    "⚡ ${trip.energyConsumptionKwhPer100Km} kWh/100km"
                } else if (trip.socDeltaPercent > 0.0) {
                    "⚡ -${trip.socDeltaPercent}%"
                } else null

                if (energyText != null) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.statusGood.copy(alpha = 0.08f),
                        border = BorderStroke(0.5.dp, MaterialTheme.statusGood.copy(alpha = 0.28f))
                    ) {
                        Text(
                            text = energyText,
                            fontSize = 10.sp,
                            lineHeight = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.statusGood,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 0.5.dp)
                        )
                    }
                }
            }

            // 2. 轨迹光轨：起点绿光 ➔ 垂直渐变轨迹线 ➔ 终点蓝光
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 1.dp, bottom = 1.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 左侧发光轨迹线
                Column(
                    modifier = Modifier.padding(top = 3.dp, bottom = 3.dp, end = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF00C853))
                    )
                    Box(
                        modifier = Modifier
                            .width(1.5.dp)
                            .height(18.dp)
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color(0xFF00C853), MaterialTheme.colorScheme.primary)
                                )
                            )
                    )
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                    )
                }

                // 右侧起止地点
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = trip.startAddress.ifBlank { "出发点" },
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(5.dp))
                    Text(
                        text = trip.endAddress.ifBlank { "到达点" },
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // 3. 底部核心指标栏（目的地文本下方间距减半，紧凑对齐）
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "里程 ${trip.distanceKm} km",
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "均速 ${trip.avgSpeedKmh} km/h",
                    fontSize = 11.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (trip.maxSpeedKmh > 0.0) {
                    Text(
                        text = "最高 ${trip.maxSpeedKmh} km/h",
                        fontSize = 11.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
