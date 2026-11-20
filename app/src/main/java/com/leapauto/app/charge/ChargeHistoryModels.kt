package com.leapauto.app.charge

import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

enum class ChargePeriodType(val label: String) {
    RECENT_7_DAYS("近7天"),
    MONTH("月"),
    YEAR("年")
}

data class ChargeStatisticPoint(
    val dateLabel: String,
    val fullDate: String,
    val energyKwh: Double
)

data class ChargeStatisticResult(
    val totalCounts: Int,
    val totalEnergyKwh: Double,
    val fastChargePercent: Double,
    val points: List<ChargeStatisticPoint>
) {
    /** 稠密化补齐日期：将官方返回的稀疏点补满为完整的 7 天、整月各天或 12 个月序列。 */
    fun densePoints(
        periodType: ChargePeriodType,
        year: Int,
        month: Int,
        referenceCal: Calendar = Calendar.getInstance(java.util.TimeZone.getTimeZone("GMT+8"))
    ): List<ChargeStatisticPoint> {
        val tz = java.util.TimeZone.getTimeZone("GMT+8")
        val dayKeyFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).apply { timeZone = tz }
        val labelMonthDay = SimpleDateFormat("MM/dd", Locale.getDefault()).apply { timeZone = tz }

        val energyMap = HashMap<String, Double>()
        points.forEach { pt ->
            if (pt.fullDate.length >= 10) {
                val key = when (periodType) {
                    ChargePeriodType.YEAR -> pt.fullDate.take(7)
                    else -> pt.fullDate.take(10)
                }
                energyMap[key] = pt.energyKwh
            }
        }

        val result = mutableListOf<ChargeStatisticPoint>()
        when (periodType) {
            ChargePeriodType.RECENT_7_DAYS -> {
                val cal = (referenceCal.clone() as Calendar).apply { timeZone = tz }
                cal.add(Calendar.DAY_OF_MONTH, -6)
                for (i in 0..6) {
                    val key = dayKeyFormat.format(cal.time)
                    val label = labelMonthDay.format(cal.time)
                    val energy = energyMap[key] ?: 0.0
                    result.add(ChargeStatisticPoint(dateLabel = label, fullDate = "$key 00:00:00", energyKwh = energy))
                    cal.add(Calendar.DAY_OF_MONTH, 1)
                }
            }
            ChargePeriodType.MONTH -> {
                val cal = Calendar.getInstance(tz).apply {
                    set(Calendar.YEAR, year)
                    set(Calendar.MONTH, month - 1)
                    set(Calendar.DAY_OF_MONTH, 1)
                }
                val maxDay = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
                for (d in 1..maxDay) {
                    cal.set(Calendar.DAY_OF_MONTH, d)
                    val key = dayKeyFormat.format(cal.time)
                    val energy = energyMap[key] ?: 0.0
                    result.add(ChargeStatisticPoint(dateLabel = "$d", fullDate = "$key 00:00:00", energyKwh = energy))
                }
            }
            ChargePeriodType.YEAR -> {
                for (m in 1..12) {
                    val key = String.format(Locale.US, "%04d-%02d", year, m)
                    val label = "${m}月"
                    val energy = energyMap[key] ?: 0.0
                    result.add(ChargeStatisticPoint(dateLabel = label, fullDate = "$key-01 00:00:00", energyKwh = energy))
                }
            }
        }
        return result
    }

    companion object {
        fun fromJson(json: JSONObject, periodType: ChargePeriodType): ChargeStatisticResult {
            val totalCounts = json.optInt("totalChargeCounts", 0)
            val totalEnergyKwh = json.optDouble("totalChargeInEnergy", 0.0)
            val fastChargePercent = json.optDouble("fastChargePercent", 0.0)
            val listArray = json.optJSONArray("chargeStatisList")
            val points = mutableListOf<ChargeStatisticPoint>()

            val tz = java.util.TimeZone.getTimeZone("GMT+8")
            val dayFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).apply { timeZone = tz }
            val labelFormatMonthDay = SimpleDateFormat("MM/dd", Locale.getDefault()).apply { timeZone = tz }
            val labelFormatMonth = SimpleDateFormat("M月", Locale.getDefault()).apply { timeZone = tz }
            val labelFormatDay = SimpleDateFormat("d", Locale.getDefault()).apply { timeZone = tz }

            if (listArray != null) {
                for (i in 0 until listArray.length()) {
                    val item = listArray.optJSONObject(i) ?: continue
                    val statisDay = item.optString("statisDay", "")
                    val energy = item.optDouble("chargeInEnergy", 0.0)

                    val label = try {
                        val date = dayFormat.parse(statisDay) ?: Date()
                        when (periodType) {
                            ChargePeriodType.RECENT_7_DAYS -> labelFormatMonthDay.format(date)
                            ChargePeriodType.MONTH -> labelFormatDay.format(date)
                            ChargePeriodType.YEAR -> labelFormatMonth.format(date)
                        }
                    } catch (_: Exception) {
                        statisDay.take(10)
                    }

                    points.add(
                        ChargeStatisticPoint(
                            dateLabel = label,
                            fullDate = statisDay,
                            energyKwh = energy
                        )
                    )
                }
            }
            return ChargeStatisticResult(
                totalCounts = totalCounts,
                totalEnergyKwh = totalEnergyKwh,
                fastChargePercent = fastChargePercent,
                points = points
            )
        }
    }
}

data class ChargeDetailItem(
    val startTs: Long,
    val endTs: Long,
    val chargeType: String,
    val energyKwh: Double,
    val longitude: String? = null,
    val latitude: String? = null
) {
    val isFastCharge: Boolean
        get() {
            val normalized = chargeType.trim().uppercase(Locale.ROOT)
            val isExplicitFast = normalized.contains("FAST") ||
                normalized.contains("QUICK") ||
                normalized.contains("DC") ||
                normalized.contains("RAPID") ||
                (normalized.isNotEmpty() && normalized != "SLOW" && normalized != "AC")
            if (isExplicitFast) return true

            // 物理平均功率双保险兜底：家用交流慢充物理上限不超过 11kW，平均功率 >= 15kW 物理上必然是直流快充
            val totalSec = maxOf(0L, (endTs - startTs) / 1000)
            val avgPowerKw = if (totalSec > 60L) (energyKwh / (totalSec / 3600.0)) else 0.0
            return avgPowerKw >= 15.0
        }

    val durationFormatted: String
        get() {
            val totalSec = maxOf(0L, (endTs - startTs) / 1000)
            val totalMinutes = Math.round(totalSec / 60.0)
            val hours = totalMinutes / 60
            val minutes = totalMinutes % 60
            return when {
                hours > 0 && minutes > 0 -> "${hours} h ${minutes} min"
                hours > 0 -> "${hours} h"
                else -> "${maxOf(1L, minutes)} min"
            }
        }

    val timeRangeFormatted: String
        get() {
            val tz = java.util.TimeZone.getTimeZone("GMT+8")
            val startCal = Calendar.getInstance(tz).apply { timeInMillis = startTs }
            val endCal = Calendar.getInstance(tz).apply { timeInMillis = endTs }

            val startDateFormat = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault()).apply {
                timeZone = tz
            }
            val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault()).apply {
                timeZone = tz
            }

            val startStr = startDateFormat.format(Date(startTs))
            val endStr = timeFormat.format(Date(endTs))

            val startDayOfYear = startCal.get(Calendar.DAY_OF_YEAR)
            val endDayOfYear = endCal.get(Calendar.DAY_OF_YEAR)
            val startYear = startCal.get(Calendar.YEAR)
            val endYear = endCal.get(Calendar.YEAR)

            val dayDiff: Long = if (startYear == endYear) {
                (endDayOfYear - startDayOfYear).toLong()
            } else {
                // 跨年情况
                (endCal.timeInMillis - startCal.timeInMillis) / (1000L * 60L * 60L * 24L)
            }

            return when {
                dayDiff == 1L -> "$startStr - $endStr ⁺¹"
                dayDiff > 1L -> "$startStr - $endStr ⁺$dayDiff"
                else -> "$startStr - $endStr"
            }
        }

    companion object {
        fun fromJson(json: JSONObject): ChargeDetailItem {
            val startTs = json.optLong("chargeGunStartTs", json.optLong("chargeEarliestTs", 0L))
            val endTs = json.optLong("chargeGunEndTs", json.optLong("chargeLatestTs", 0L))
            val chargeType = json.optString("chargeType", "SLOW")
            val energy = json.optDouble("chargeInEnergy", 0.0)
            val lon = json.optString("chargeStartLongitude").takeIf { it.isNotBlank() }
            val lat = json.optString("chargeStartLatitude").takeIf { it.isNotBlank() }

            return ChargeDetailItem(
                startTs = startTs,
                endTs = endTs,
                chargeType = chargeType,
                energyKwh = energy,
                longitude = lon,
                latitude = lat
            )
        }
    }
}
