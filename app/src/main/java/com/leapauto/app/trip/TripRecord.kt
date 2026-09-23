package com.leapauto.app.trip

import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 单次行程记录实体模型 */
data class TripRecord(
    val id: String,
    val vin: String,
    val startTimeEpochMs: Long,
    val endTimeEpochMs: Long,
    val startMileageKm: Double,
    val endMileageKm: Double,
    val distanceKm: Double,
    val durationSeconds: Long,
    val startSocPercent: Double,
    val endSocPercent: Double,
    val socDeltaPercent: Double,
    val avgSpeedKmh: Double,
    val maxSpeedKmh: Double = 0.0,
    val startAddress: String = "",
    val endAddress: String = "",
    val energyConsumptionKwhPer100Km: Double? = null
) {
    /** 格式化后的用时：如 "28分钟"、"1小时15分" */
    val formattedDuration: String
        get() {
            val minutes = (durationSeconds / 60).coerceAtLeast(1)
            val hours = minutes / 60
            val remMinutes = minutes % 60
            return if (hours > 0) {
                "${hours}小时${remMinutes}分"
            } else {
                "${minutes}分钟"
            }
        }

    /** 起止时间段显示：如 "08:15 ~ 08:43" */
    val formattedTimeRange: String
        get() {
            val timeFmt = SimpleDateFormat("HH:mm", Locale.getDefault())
            val startStr = timeFmt.format(Date(startTimeEpochMs))
            val endStr = timeFmt.format(Date(endTimeEpochMs))
            return "$startStr ~ $endStr"
        }

    /** 行程日期归档分组键：如 "2026-09-19" */
    val dateGroupKey: String
        get() = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(startTimeEpochMs))

    /** 人性化日期标签：如 "今天"、"昨天" 或 "9月18日" */
    val dateDisplayLabel: String
        get() {
            val now = System.currentTimeMillis()
            val dayFmt = SimpleDateFormat("yyyyMMdd", Locale.getDefault())
            val tripDay = dayFmt.format(Date(startTimeEpochMs))
            val today = dayFmt.format(Date(now))
            val yesterday = dayFmt.format(Date(now - 86400000L))
            return when (tripDay) {
                today -> "今天"
                yesterday -> "昨天"
                else -> SimpleDateFormat("M月d日", Locale.getDefault()).format(Date(startTimeEpochMs))
            }
        }

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("vin", vin)
        put("startTimeEpochMs", startTimeEpochMs)
        put("endTimeEpochMs", endTimeEpochMs)
        put("startMileageKm", startMileageKm)
        put("endMileageKm", endMileageKm)
        put("distanceKm", distanceKm)
        put("durationSeconds", durationSeconds)
        put("startSocPercent", startSocPercent)
        put("endSocPercent", endSocPercent)
        put("socDeltaPercent", socDeltaPercent)
        put("avgSpeedKmh", avgSpeedKmh)
        put("maxSpeedKmh", maxSpeedKmh)
        put("startAddress", startAddress)
        put("endAddress", endAddress)
        if (energyConsumptionKwhPer100Km != null) {
            put("energyConsumptionKwhPer100Km", energyConsumptionKwhPer100Km)
        }
    }

    companion object {
        fun fromJson(json: JSONObject): TripRecord {
            return TripRecord(
                id = json.optString("id"),
                vin = json.optString("vin"),
                startTimeEpochMs = json.optLong("startTimeEpochMs"),
                endTimeEpochMs = json.optLong("endTimeEpochMs"),
                startMileageKm = json.optDouble("startMileageKm", 0.0),
                endMileageKm = json.optDouble("endMileageKm", 0.0),
                distanceKm = json.optDouble("distanceKm", 0.0),
                durationSeconds = json.optLong("durationSeconds", 0L),
                startSocPercent = json.optDouble("startSocPercent", 0.0),
                endSocPercent = json.optDouble("endSocPercent", 0.0),
                socDeltaPercent = json.optDouble("socDeltaPercent", 0.0),
                avgSpeedKmh = json.optDouble("avgSpeedKmh", 0.0),
                maxSpeedKmh = json.optDouble("maxSpeedKmh", 0.0),
                startAddress = json.optString("startAddress", ""),
                endAddress = json.optString("endAddress", ""),
                energyConsumptionKwhPer100Km = if (json.has("energyConsumptionKwhPer100Km")) {
                    json.optDouble("energyConsumptionKwhPer100Km")
                } else null
            )
        }
    }
}

/** 进行中的活动行程实时快照 */
data class ActiveTripSnapshot(
    val vin: String,
    val startTimeEpochMs: Long,
    val startMileageKm: Double,
    val startSocPercent: Double,
    val startAddress: String = "",
    var lastActiveTimeEpochMs: Long = startTimeEpochMs,
    var currentMileageKm: Double = startMileageKm,
    var currentSocPercent: Double = startSocPercent,
    var currentAddress: String = startAddress,
    var maxSpeedKmh: Double = 0.0
) {
    fun toJson(): JSONObject {
        val obj = JSONObject()
        obj.put("vin", vin)
        obj.put("startTimeEpochMs", startTimeEpochMs)
        obj.put("startMileageKm", startMileageKm)
        obj.put("startSocPercent", startSocPercent)
        obj.put("startAddress", startAddress)
        obj.put("lastActiveTimeEpochMs", lastActiveTimeEpochMs)
        obj.put("currentMileageKm", currentMileageKm)
        obj.put("currentSocPercent", currentSocPercent)
        obj.put("currentAddress", currentAddress)
        obj.put("maxSpeedKmh", maxSpeedKmh)
        return obj
    }

    companion object {
        fun fromJson(obj: JSONObject): ActiveTripSnapshot {
            return ActiveTripSnapshot(
                vin = obj.optString("vin"),
                startTimeEpochMs = obj.optLong("startTimeEpochMs"),
                startMileageKm = obj.optDouble("startMileageKm"),
                startSocPercent = obj.optDouble("startSocPercent"),
                startAddress = obj.optString("startAddress"),
                lastActiveTimeEpochMs = obj.optLong("lastActiveTimeEpochMs"),
                currentMileageKm = obj.optDouble("currentMileageKm"),
                currentSocPercent = obj.optDouble("currentSocPercent"),
                currentAddress = obj.optString("currentAddress"),
                maxSpeedKmh = obj.optDouble("maxSpeedKmh")
            )
        }
    }
}

/** 某一天行程的统计汇总 */
data class TripDaySummary(
    val dateLabel: String,
    val tripCount: Int,
    val totalDistanceKm: Double,
    val totalDurationSeconds: Long,
    val avgSpeedKmh: Double,
    val totalSocDeltaPercent: Double
)

/** 某一天行程的完整数据组（供横滑按天分页使用） */
data class DayTripGroup(
    val dateKey: String,
    val dateLabel: String,
    val dateSubLabel: String,
    val trips: List<TripRecord>,
    val totalDistanceKm: Double,
    val totalDurationFormatted: String,
    val totalSocDeltaPercent: Double,
    val avgSpeedKmh: Double,
    val avgEnergyConsumption: Double?
)

object TripGroupHelper {
    const val MAX_DISPLAY_DAYS = 30

    fun groupByDate(trips: List<TripRecord>): List<DayTripGroup> {
        if (trips.isEmpty()) return emptyList()
        val groups = trips.groupBy { it.dateGroupKey }.toSortedMap(reverseOrder())
        val result = mutableListOf<DayTripGroup>()
        val dateSubFmt = SimpleDateFormat("M月d日", Locale.getDefault())

        for ((dateKey, dayTrips) in groups.entries.take(MAX_DISPLAY_DAYS)) {
            val sortedDayTrips = dayTrips.sortedByDescending { it.startTimeEpochMs }
            val first = sortedDayTrips.first()
            val totalKm = java.math.BigDecimal(sortedDayTrips.sumOf { it.distanceKm }).setScale(1, java.math.RoundingMode.HALF_UP).toDouble()
            val totalSeconds = sortedDayTrips.sumOf { it.durationSeconds }
            val totalMinutes = (totalSeconds / 60).coerceAtLeast(1)
            val totalHours = totalMinutes / 60
            val remMinutes = totalMinutes % 60
            val durationStr = if (totalHours > 0) "${totalHours}小时${remMinutes}分" else "${totalMinutes}分钟"
            val totalSocDelta = java.math.BigDecimal(sortedDayTrips.sumOf { it.socDeltaPercent }).setScale(1, java.math.RoundingMode.HALF_UP).toDouble()

            val totalHoursDbl = totalSeconds.toDouble() / 3600.0
            val avgSpeed = if (totalHoursDbl > 0.0 && totalKm > 0.0) {
                java.math.BigDecimal(totalKm / totalHoursDbl).setScale(1, java.math.RoundingMode.HALF_UP).toDouble()
            } else 0.0

            val validEnergies = sortedDayTrips.mapNotNull { it.energyConsumptionKwhPer100Km }
            val avgEnergy = if (validEnergies.isNotEmpty()) {
                java.math.BigDecimal(validEnergies.average()).setScale(1, java.math.RoundingMode.HALF_UP).toDouble()
            } else null

            result.add(
                DayTripGroup(
                    dateKey = dateKey,
                    dateLabel = first.dateDisplayLabel,
                    dateSubLabel = dateSubFmt.format(Date(first.startTimeEpochMs)),
                    trips = sortedDayTrips,
                    totalDistanceKm = totalKm,
                    totalDurationFormatted = durationStr,
                    totalSocDeltaPercent = totalSocDelta,
                    avgSpeedKmh = avgSpeed,
                    avgEnergyConsumption = avgEnergy
                )
            )
        }
        return result
    }
}
