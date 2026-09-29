package com.leapauto.app

import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import kotlin.math.abs

data class EnergyMetric(
    val label: String,
    val value: String,
    val unit: String? = null
)

data class EnergySeriesPoint(
    val label: String,
    val value: Double,
    /** Optional period end returned by weekly energy endpoints. */
    val endLabel: String? = null
)

/** A single valid day returned by the recent mileage detail endpoint. */
data class DailyMileage(
    val day: String,
    val mileageKm: Double
)

/**
 * Parsed recent mileage detail.
 *
 * [networkRows] is the bounded response representation (the last eight valid
 * rows). [mileage] is the seven-row view used by the product chart. Keeping
 * both makes the server-row boundary explicit and prevents UI code from
 * accidentally summing an unbounded response.
 */
data class MileageEnergyDetail(
    val networkRows: List<DailyMileage>,
    val mileage: List<DailyMileage>,
    val totalMileageKm: Int,
    val totalEnergyKwh: Double?,
    val deliveryDays: Int?,
    val vehicleTotalMileage: String? = null
)

data class EnergyCategory(
    val label: String,
    val value: Double
)

/**
 * User-facing labels for the three verified fields returned by getLastweekEC.
 * Unknown categories deliberately retain their server-provided label.
 */
object EnergyCompositionPresentation {
    fun displayLabel(rawLabel: String): String = when (rawLabel.trim()) {
        "驾驶", "行车", "行车能耗" -> "行车能耗"
        "空调", "空调能耗" -> "空调能耗"
        "其他", "其他能耗" -> "其他能耗"
        else -> rawLabel
    }

    fun displayPercent(value: Double, total: Double): String {
        if (!value.isFinite() || !total.isFinite() || total <= 0.0) return "0%"
        val percent = (value.coerceAtLeast(0.0) / total * 100.0).coerceIn(0.0, 100.0)
        return String.format(Locale.US, "%.2f", percent)
            .trimEnd('0')
            .trimEnd('.') + "%"
    }
}

/** Formats verified weekly ISO dates for the compact chart labels. */
object EnergyWeekPeriodFormatter {
    private val isoDatePrefix = Regex("^\\s*(?:(\\d{4})[-/])?(\\d{1,2})[-/](\\d{1,2})")

    fun format(start: String, end: String?): String {
        val startLabel = monthDay(start)
        val endLabel = end?.takeIf { it.isNotBlank() }?.let(::monthDay)
        return if (endLabel == null) startLabel else "$startLabel-$endLabel"
    }

    fun weekEndDate(start: String, end: String?): String {
        val formattedEnd = end?.takeIf { it.isNotBlank() }?.let(::monthDay)
        if (formattedEnd != null) return formattedEnd

        return runCatching {
            val match = isoDatePrefix.find(start) ?: return monthDay(start)
            val year = match.groupValues[1].toIntOrNull() ?: java.time.LocalDate.now().year
            val month = match.groupValues[2].toInt()
            val day = match.groupValues[3].toInt()
            val sunday = java.time.LocalDate.of(year, month, day).plusDays(6)
            "${sunday.monthValue}/${sunday.dayOfMonth}"
        }.getOrDefault(monthDay(start))
    }

    fun isCurrentWeek(start: String, end: String?): Boolean {
        return runCatching {
            val today = java.time.LocalDate.now()
            val matchStart = isoDatePrefix.find(start) ?: return false
            val yearStart = matchStart.groupValues[1].toIntOrNull() ?: today.year
            val startDate = java.time.LocalDate.of(yearStart, matchStart.groupValues[2].toInt(), matchStart.groupValues[3].toInt())
            val endDate = if (!end.isNullOrBlank()) {
                val matchEnd = isoDatePrefix.find(end) ?: return false
                val yearEnd = matchEnd.groupValues[1].toIntOrNull() ?: today.year
                java.time.LocalDate.of(yearEnd, matchEnd.groupValues[2].toInt(), matchEnd.groupValues[3].toInt())
            } else {
                startDate.plusDays(6)
            }
            !today.isBefore(startDate) && !today.isAfter(endDate)
        }.getOrDefault(false)
    }

    fun monthDay(value: String): String {
        val match = isoDatePrefix.find(value) ?: return value.trim()
        val month = match.groupValues[2].toIntOrNull() ?: return value.trim()
        val day = match.groupValues[3].toIntOrNull() ?: return value.trim()
        return "$month/$day"
    }
}

data class EnergyAnalyticsData(
    val overallConsumption: EnergyMetric?,
    val totalMileage: EnergyMetric?,
    val cumulativeEnergy: EnergyMetric?,
    val ownershipDays: EnergyMetric?,
    val recentMileage: EnergyMetric?,
    val trend: List<EnergySeriesPoint>,
    val mileageTrend: List<EnergySeriesPoint>,
    val composition: List<EnergyCategory> = emptyList(),
    val otherFields: List<Pair<String, String>> = emptyList(),
    val capturedAt: Long,
    val rankLabel: String? = null,
    val rankError: String? = null,
    val lastWeekComposition: List<EnergyCategory> = emptyList()
) {
    companion object {
        val EMPTY = EnergyAnalyticsData(
            overallConsumption = null,
            totalMileage = null,
            cumulativeEnergy = null,
            ownershipDays = null,
            recentMileage = null,
            trend = emptyList(),
            mileageTrend = emptyList(),
            composition = emptyList(),
            otherFields = emptyList(),
            capturedAt = 0L
        )
    }
}

/** Data limits used by the compact home energy pager. */
object EnergyHomeCardPolicy {
    const val PAGE_COUNT = 4
    const val MIN_CARD_HEIGHT_DP = 176
    const val BOTTOM_PADDING_DP = 12

    fun calculateDynamicCardHeightDp(
        viewportHeightPx: Int,
        topContentHeightPx: Int,
        topPaddingPx: Int,
        spacingPx: Int,
        bottomPaddingPx: Int,
        density: Float,
        minHeightDp: Int = MIN_CARD_HEIGHT_DP
    ): Float {
        if (topContentHeightPx <= 0 || density <= 0f) return minHeightDp.toFloat()
        val remainingPx = viewportHeightPx - topContentHeightPx - topPaddingPx - spacingPx - bottomPaddingPx
        val remainingDp = remainingPx / density
        return maxOf(minHeightDp.toFloat(), remainingDp)
    }

    fun todayMileage(data: EnergyAnalyticsData?): String {
        if (data == null || data.mileageTrend.isEmpty()) return "--"
        val shanghaiToday = try {
            java.time.LocalDate.now(java.time.ZoneId.of("Asia/Shanghai")).toString()
        } catch (_: Throwable) {
            null
        }
        val todayPoint = (if (shanghaiToday != null) data.mileageTrend.find { it.label == shanghaiToday } else null)
            ?: data.mileageTrend.lastOrNull()
            ?: return "--"
        val km = todayPoint.value
        val numStr = if (km % 1.0 == 0.0) km.toInt().toString() else String.format(java.util.Locale.US, "%.1f", km)
        return "${numStr}km"
    }

    fun recentTrend(data: EnergyAnalyticsData, limit: Int = 6): List<EnergySeriesPoint> =
        data.trend.takeLast(limit.coerceAtLeast(0))

    fun recentMileage(data: EnergyAnalyticsData, limit: Int = 7): List<EnergySeriesPoint> =
        data.mileageTrend.takeLast(limit.coerceAtLeast(0))
}

data class EnergyRankAnalytics(
    val overallConsumption: EnergyMetric?,
    val rankLabel: String?,
    val weeklyTrend: List<EnergySeriesPoint>
)

sealed interface EnergyAnalyticsState {
    data object Idle : EnergyAnalyticsState
    data object Loading : EnergyAnalyticsState
    data class Success(val data: EnergyAnalyticsData) : EnergyAnalyticsState
    data class Failed(val message: String) : EnergyAnalyticsState
}

object EnergyRefreshPolicy {
    const val CACHE_TTL_MS = 15 * 60 * 1000L
    const val MAX_STALE_MS = 24 * 60 * 60 * 1000L

    fun shouldRefresh(lastSuccessAt: Long, now: Long, force: Boolean): Boolean {
        if (force || lastSuccessAt <= 0L || now < lastSuccessAt) return true
        return now - lastSuccessAt >= CACHE_TTL_MS
    }

    fun canUseCachedData(capturedAt: Long, now: Long): Boolean =
        capturedAt > 0L && now >= capturedAt && now - capturedAt <= MAX_STALE_MS

    /**
     * The home pager has no fallback for raw, unknown response fields. Do not
     * let such a cached response suppress a fresh request after app restart.
     */
    fun hasHomeDisplayableData(data: EnergyAnalyticsData): Boolean =
        data.overallConsumption != null ||
            data.totalMileage != null ||
            data.cumulativeEnergy != null ||
            data.ownershipDays != null ||
            data.recentMileage != null ||
            data.trend.isNotEmpty() ||
            data.mileageTrend.isNotEmpty() ||
            data.composition.isNotEmpty() ||
            data.lastWeekComposition.isNotEmpty()
}

open class EnergyAnalyticsParseException(message: String) : Exception(message)

class EnergyAnalyticsBusinessException : EnergyAnalyticsParseException("能耗服务暂时不可用，请稍后重试")

class EnergyAnalyticsEmptyDataException : EnergyAnalyticsParseException("能耗接口暂未返回可展示数据")

/**
 * Conservative parser for the old mileage/energy endpoint.
 *
 * The MCP documentation does not publish a stable field schema. We therefore
 * only promote a small set of observed/obvious aliases and keep every other
 * non-sensitive leaf in the "other data" table instead of inventing units.
 */
object EnergyAnalyticsParser {
    private val sensitiveKey = Regex(
        "(token|cookie|authorization|password|secret|sign|device|imei|vin|phone|mobile|account|longitude|latitude|address|location)",
        RegexOption.IGNORE_CASE
    )
    private val protocolKey = Regex(
        "^(result|code|msg|message|success|status|error)$",
        RegexOption.IGNORE_CASE
    )

    private val overallKeys = setOf(
        "comprehensiveenergyconsumption",
        "overallenergyconsumption",
        "averageenergyconsumption",
        "energyconsumptionper100km",
        "energyconsumption"
    )
    private val totalMileageKeys = setOf(
        "totalmileage", "totalmile", "mileagetotal", "totaldrivingmileage"
    )
    private val cumulativeEnergyKeys = setOf(
        "cumulativeenergy", "totalenergy", "totalenergyconsumption", "energytotal"
    )
    private val recentMileageKeys = setOf(
        "recentmileage", "recentdrivingmileage", "mileagerecent", "recentdistance"
    )
    private val ownershipDaysKeys = setOf(
        "ownershipdays", "dayssincepurchase", "pickupdays", "deliverydays", "carage"
    )
    private val trendKeys = setOf(
        "trend",
        "energytrend",
        "mileageenergytrend",
        "weeklyenergy",
        "weeklyenergyconsumption",
        "weekenergyconsumption",
        "recentenergy",
        "consumptiontrend"
    )
    private val mileageTrendKeys = setOf(
        "mileagetrend",
        "weeklymileage",
        "weekmileage",
        "recentmileagetrend",
        "drivingtrend",
        "recentmileagechart",
        // The documented recent-mileage response uses data.detail[].day and
        // data.detail[].accumulatedMileage rather than a named trend array.
        "detail"
    )
    private val compositionKeys = setOf(
        "composition", "energycomposition", "energybreakdown", "energycomponents"
    )

    fun parse(response: JSONObject, capturedAt: Long = System.currentTimeMillis()): EnergyAnalyticsData {
        validateBusinessResult(response)
        val entries = mutableListOf<Pair<String, Any?>>()
        collectLeaves(response, "", entries)

        fun metric(keys: Set<String>, fallbackLabel: String): EnergyMetric? {
            val match = entries.firstOrNull { normalize(lastKey(it.first)) in keys && isScalar(it.second) }
                ?: return null
            return EnergyMetric(
                label = fallbackLabel,
                value = scalarText(match.second),
                unit = null
            )
        }

        val trend = findArrays(response, trendKeys)
            .flatMap(::parseSeries)
            .take(31)
        val mileageTrend = findArrays(response, mileageTrendKeys)
            .flatMap(::parseSeries)
            .take(31)
        val composition = findArrays(response, compositionKeys)
            .flatMap(::parseComposition)
            .take(24)

        val known = overallKeys + totalMileageKeys + cumulativeEnergyKeys + ownershipDaysKeys +
            recentMileageKeys + trendKeys + mileageTrendKeys + compositionKeys
        val other = entries
            .filter { normalize(lastKey(it.first)) !in known }
            .filterNot { isKnownChartLeaf(it.first) }
            .filter { !protocolKey.matches(lastKey(it.first)) }
            .filter { !sensitiveKey.containsMatchIn(it.first) }
            .map { it.first to scalarText(it.second) }
            .distinct()
            .take(200)

        if (metric(overallKeys, "综合能耗") == null &&
            metric(totalMileageKeys, "总里程") == null &&
            metric(cumulativeEnergyKeys, "累计能耗") == null &&
            metric(ownershipDaysKeys, "提车天数") == null &&
            metric(recentMileageKeys, "最近行驶里程") == null &&
            trend.isEmpty() && mileageTrend.isEmpty() && composition.isEmpty() && other.isEmpty()
        ) {
            throw EnergyAnalyticsEmptyDataException()
        }

        return EnergyAnalyticsData(
            overallConsumption = metric(overallKeys, "综合能耗"),
            totalMileage = metric(totalMileageKeys, "总里程"),
            cumulativeEnergy = metric(cumulativeEnergyKeys, "累计能耗"),
            ownershipDays = metric(ownershipDaysKeys, "提车天数"),
            recentMileage = metric(recentMileageKeys, "最近行驶里程"),
            trend = trend,
            mileageTrend = mileageTrend,
            composition = composition,
            otherFields = other,
            capturedAt = capturedAt
        )
    }

    private fun validateBusinessResult(response: JSONObject) {
        val result = response.opt("result")
        val code = response.opt("code")
        val success = response.opt("success")
        val failure = when {
            result != null && result != JSONObject.NULL && result.toString() !in setOf("0", "200") -> true
            code != null && code != JSONObject.NULL && code.toString() !in setOf("0", "200") -> true
            success is Boolean && !success -> true
            else -> false
        }
        if (failure) {
            // Do not surface the server message verbatim. The old-service
            // response is not a user-facing diagnostic payload and can carry
            // business data that does not belong in this screen or logs.
            throw EnergyAnalyticsBusinessException()
        }
    }

    private fun collectLeaves(value: Any?, path: String, out: MutableList<Pair<String, Any?>>) {
        when (value) {
            is JSONObject -> {
                val keys = value.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    if (sensitiveKey.containsMatchIn(key)) continue
                    val child = value.opt(key)
                    val childPath = if (path.isBlank()) key else "$path.$key"
                    if (isScalar(child)) out += childPath to child
                    else collectLeaves(child, childPath, out)
                }
            }
            is JSONArray -> for (i in 0 until value.length()) {
                collectLeaves(value.opt(i), "$path[$i]", out)
            }
        }
    }

    private fun findArrays(root: Any?, keys: Set<String>): List<JSONArray> {
        val out = mutableListOf<JSONArray>()
        fun walk(value: Any?) {
            when (value) {
                is JSONObject -> {
                    val iterator = value.keys()
                    while (iterator.hasNext()) {
                        val key = iterator.next()
                        val child = value.opt(key)
                        if (normalize(key) in keys && child is JSONArray) out += child
                        walk(child)
                    }
                }
                is JSONArray -> for (i in 0 until value.length()) walk(value.opt(i))
            }
        }
        walk(root)
        return out
    }

    private fun parseSeries(array: JSONArray): List<EnergySeriesPoint> =
        (0 until array.length()).mapNotNull { index ->
            val item = array.opt(index)
            if (item is JSONObject) {
                val label = firstString(item, "date", "day", "week", "label", "time")
                    ?: return@mapNotNull null
                val value = firstNumber(
                    item,
                    "value",
                    "energy",
                    "consumption",
                    "mileage",
                    "distance",
                    "accumulatedMileage"
                )
                    ?.takeIf { it >= 0.0 }
                    ?: return@mapNotNull null
                EnergySeriesPoint(
                    label = label,
                    value = value,
                    endLabel = item.optString("weekEnd").takeIf { it.isNotBlank() }
                )
            } else {
                // Compact deployments may return a numeric series without
                // labels. Use an ordinal label rather than inventing a date.
                scalarNumber(item)
                    ?.takeIf { it >= 0.0 }
                    ?.let { EnergySeriesPoint("#${index + 1}", it) }
            }
        }

    private fun parseComposition(array: JSONArray): List<EnergyCategory> =
        (0 until array.length()).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            val label = firstString(item, "label", "name", "type", "category") ?: return@mapNotNull null
            val value = firstNumber(item, "value", "ratio", "percent", "energy")
                ?.takeIf { it >= 0.0 }
                ?: return@mapNotNull null
            EnergyCategory(label, value)
        }

    private fun firstString(item: JSONObject, vararg keys: String): String? =
        keys.firstNotNullOfOrNull { key ->
            item.opt(key).takeIf { isScalar(it) }?.toString()?.takeIf(String::isNotBlank)
        }

    private fun firstNumber(item: JSONObject, vararg keys: String): Double? =
        keys.firstNotNullOfOrNull { key ->
            item.opt(key)?.toString()?.toDoubleOrNull()?.takeIf { it.isFinite() && abs(it) < 1e12 }
        }

    private fun normalize(value: String): String =
        value.lowercase(Locale.ROOT).replace(Regex("[_\\-\\s]"), "")

    private fun lastKey(path: String): String =
        path.substringAfterLast('.').substringBefore('[')

    /**
     * A chart array is already displayed as a graph. Keep its internal date/value
     * leaves out of "其他数据" so that the same payload does not appear twice.
     */
    private fun isKnownChartLeaf(path: String): Boolean {
        val container = path.substringBefore('[').substringAfterLast('.')
        val knownChartArrays = trendKeys + mileageTrendKeys + compositionKeys
        return normalize(container) in knownChartArrays
    }

    private fun isScalar(value: Any?): Boolean =
        value != null && value != JSONObject.NULL && value !is JSONObject && value !is JSONArray

    private fun scalarText(value: Any?): String = value?.toString().orEmpty()

    private fun scalarNumber(value: Any?): Double? =
        value?.takeIf { isScalar(it) }?.toString()?.toDoubleOrNull()
            ?.takeIf { it.isFinite() && abs(it) < 1e12 }
}

/**
 * Strict parser for the recent seven-day mileage contract documented in
 * `近7天行驶里程_API实现说明.md`.
 *
 * The generic energy parser above intentionally accepts several historical
 * aliases. This parser is kept separate so the fixed `data.detail` contract
 * can enforce its missing/invalid-data behavior without weakening that
 * compatibility parser.
 */
object RecentMileageEnergyParser {
    private const val MAX_NUMERIC_VALUE = 1e12

    fun parse(response: JSONObject): MileageEnergyDetail {
        validateBusinessResult(response)
        val data = response.optJSONObject("data")
            ?: throw EnergyAnalyticsParseException("近7日里程响应缺少 data")
        val detail = data.optJSONArray("detail")
            ?: throw EnergyAnalyticsParseException("近7日里程响应缺少 data.detail")

        val validRows = (0 until detail.length()).mapNotNull { index ->
            val item = detail.optJSONObject(index) ?: return@mapNotNull null
            val day = item.opt("day")
                ?.takeIf { it != JSONObject.NULL }
                ?.toString()
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?: return@mapNotNull null
            val mileage = item.opt("accumulatedMileage")
                ?.toString()
                ?.toDoubleOrNull()
                ?.takeIf { it.isFinite() && it >= 0.0 && it < MAX_NUMERIC_VALUE }
                ?: return@mapNotNull null
            DailyMileage(day = day, mileageKm = mileage)
        }

        val networkRows = validRows.takeLast(8)
        if (networkRows.isEmpty()) throw EnergyAnalyticsEmptyDataException()
        val displayedRows = networkRows.takeLast(7)
        // Keep an absurd server value from wrapping the integer shown in the
        // UI. Normal responses retain the documented truncation semantics.
        val totalMileageKm = displayedRows.sumOf { it.mileageKm }
            .coerceIn(0.0, Int.MAX_VALUE.toDouble())
            .toInt()

        val vehicleTotalMileage = data.opt("totalmileage")
            ?.takeIf { it != JSONObject.NULL }
            ?.toString()
            ?.trim()
            ?.takeIf { it.isNotEmpty() }

        return MileageEnergyDetail(
            networkRows = networkRows,
            mileage = displayedRows,
            totalMileageKm = totalMileageKm,
            totalEnergyKwh = parseNonNegativeNumber(data.opt("totalEnergy")),
            deliveryDays = parseNonNegativeInt(data.opt("deliveryDays")),
            vehicleTotalMileage = vehicleTotalMileage
        )
    }

    private fun validateBusinessResult(response: JSONObject) {
        val result = response.opt("result")
        val code = response.opt("code")
        val success = response.opt("success")
        val failed = when {
            result != null && result != JSONObject.NULL && result.toString() !in setOf("0", "200") -> true
            code != null && code != JSONObject.NULL && code.toString() !in setOf("0", "200") -> true
            success is Boolean && !success -> true
            else -> false
        }
        if (failed) throw EnergyAnalyticsBusinessException()
    }

    private fun parseNonNegativeNumber(value: Any?): Double? = value
        ?.takeIf { it != JSONObject.NULL }
        ?.toString()
        ?.toDoubleOrNull()
        ?.takeIf { it.isFinite() && it >= 0.0 && it < MAX_NUMERIC_VALUE }

    private fun parseNonNegativeInt(value: Any?): Int? {
        val number = parseNonNegativeNumber(value) ?: return null
        if (number > Int.MAX_VALUE || number % 1.0 != 0.0) return null
        return number.toInt()
    }
}

/** Parser for the verified weekly 100-km energy/rank response. */
object EnergyRankAnalyticsParser {
    fun parse(response: JSONObject): EnergyRankAnalytics {
        val result = response.opt("result")
        val code = response.opt("code")
        if ((result != null && result != JSONObject.NULL && result.toString() !in setOf("0", "200")) ||
            (code != null && code != JSONObject.NULL && code.toString() !in setOf("0", "200"))
        ) {
            throw EnergyAnalyticsBusinessException()
        }
        val data = response.optJSONObject("data") ?: throw EnergyAnalyticsEmptyDataException()
        val rankResult = data.optJSONObject("rankResult")
        val consumption = rankResult?.opt("hundredKmEC")?.toString()?.toDoubleOrNull()
            ?.takeIf { it.isFinite() && it >= 0.0 && it < 1e12 }
            ?.let { EnergyMetric("综合能耗", it.toString(), "kWh/100km") }
        val rank = rankResult?.optString("rank")?.takeIf { it.isNotBlank() }
        val weekly = data.optJSONArray("weeklyEC")?.let { array ->
            (0 until array.length()).mapNotNull { index ->
                val item = array.optJSONObject(index) ?: return@mapNotNull null
                val label = item.optString("weekStart").takeIf { it.isNotBlank() }
                    ?: return@mapNotNull null
                val value = item.opt("hundredKmEC")?.toString()?.toDoubleOrNull()
                    ?.takeIf { it.isFinite() && it >= 0.0 && it < 1e12 }
                    ?: return@mapNotNull null
                EnergySeriesPoint(
                    label = label,
                    value = value,
                    endLabel = item.optString("weekEnd").takeIf { it.isNotBlank() }
                )
            }.take(52)
        }.orEmpty()
        if (consumption == null && rank == null && weekly.isEmpty()) {
            throw EnergyAnalyticsEmptyDataException()
        }
        return EnergyRankAnalytics(consumption, rank, weekly)
    }
}

/** Parser for the verified getLastweekEC composition response. */
object LastWeekEnergyCompositionParser {
    fun parse(response: JSONObject): List<EnergyCategory> {
        val result = response.opt("result")
        val code = response.opt("code")
        if ((result != null && result != JSONObject.NULL && result.toString() !in setOf("0", "200")) ||
            (code != null && code != JSONObject.NULL && code.toString() !in setOf("0", "200"))
        ) {
            throw EnergyAnalyticsBusinessException()
        }
        val data = response.optJSONObject("data") ?: throw EnergyAnalyticsEmptyDataException()
        val fields = listOf(
            "driverEC" to "驾驶",
            "acEC" to "空调",
            "otherEC" to "其他"
        )
        val categories = fields.mapNotNull { (key, label) ->
            val value = data.opt(key)?.toString()?.toDoubleOrNull()
                ?.takeIf { it.isFinite() && it >= 0.0 && it < 1e12 }
                ?: return@mapNotNull null
            EnergyCategory(label, value)
        }
        if (categories.isEmpty()) throw EnergyAnalyticsEmptyDataException()
        return categories
    }
}
