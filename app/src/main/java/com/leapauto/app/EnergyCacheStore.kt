package com.leapauto.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

/** App-private cache for parsed energy data. Raw HTTP responses are never stored. */
class EnergyCacheStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(
        "leap_energy_cache",
        Context.MODE_PRIVATE
    )

    fun load(vin: String, now: Long = System.currentTimeMillis()): EnergyAnalyticsData? {
        if (vin.isBlank()) return null
        return runCatching {
            prefs.getString(key(vin), null)?.let(::decode)?.takeIf {
                EnergyRefreshPolicy.canUseCachedData(it.capturedAt, now)
            }
        }.getOrNull()
    }

    fun save(vin: String, data: EnergyAnalyticsData) {
        if (vin.isBlank()) return
        runCatching { prefs.edit().putString(key(vin), encode(data).toString()).apply() }
    }

    fun clearAll() {
        prefs.edit().clear().apply()
    }

    private fun key(vin: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(vin.toByteArray())
        return "energy_" + digest.joinToString("") { "%02x".format(it) }
    }

    private fun encode(data: EnergyAnalyticsData): JSONObject = JSONObject().apply {
        putMetric("overall", data.overallConsumption)
        putMetric("totalMileage", data.totalMileage)
        putMetric("cumulativeEnergy", data.cumulativeEnergy)
        putMetric("ownershipDays", data.ownershipDays)
        putMetric("recentMileage", data.recentMileage)
        put("capturedAt", data.capturedAt)
        putNullable("rankLabel", data.rankLabel)
        putNullable("rankError", data.rankError)
        putSeries("trend", data.trend)
        putSeries("mileageTrend", data.mileageTrend)
        putCategories("composition", data.composition)
        putCategories("lastWeekComposition", data.lastWeekComposition)
        put("otherFields", JSONArray().apply {
            data.otherFields.forEach { put(JSONObject().put("key", it.first).put("value", it.second)) }
        })
    }

    private fun decode(json: String): EnergyAnalyticsData {
        val root = JSONObject(json)
        return EnergyAnalyticsData(
            overallConsumption = readMetric(root, "overall"),
            totalMileage = readMetric(root, "totalMileage"),
            cumulativeEnergy = readMetric(root, "cumulativeEnergy"),
            ownershipDays = readMetric(root, "ownershipDays"),
            recentMileage = readMetric(root, "recentMileage"),
            trend = readSeries(root.optJSONArray("trend")),
            mileageTrend = readSeries(root.optJSONArray("mileageTrend")),
            composition = readCategories(root.optJSONArray("composition")),
            otherFields = readOther(root.optJSONArray("otherFields")),
            capturedAt = root.optLong("capturedAt", 0L),
            rankLabel = root.optString("rankLabel").takeIf { it.isNotBlank() },
            rankError = root.optString("rankError").takeIf { it.isNotBlank() },
            lastWeekComposition = readCategories(root.optJSONArray("lastWeekComposition"))
        )
    }

    private fun JSONObject.putMetric(name: String, metric: EnergyMetric?) {
        if (metric == null) return
        put(name, JSONObject().put("label", metric.label).put("value", metric.value).putNullable("unit", metric.unit))
    }

    private fun JSONObject.putNullable(name: String, value: String?) {
        if (value == null) put(name, JSONObject.NULL) else put(name, value)
    }

    private fun JSONObject.putSeries(name: String, values: List<EnergySeriesPoint>) {
        put(name, JSONArray().apply {
            values.forEach { put(JSONObject().put("label", it.label).put("value", it.value).putNullable("endLabel", it.endLabel)) }
        })
    }

    private fun JSONObject.putCategories(name: String, values: List<EnergyCategory>) {
        put(name, JSONArray().apply { values.forEach { put(JSONObject().put("label", it.label).put("value", it.value)) } })
    }

    private fun readMetric(root: JSONObject, name: String): EnergyMetric? = root.optJSONObject(name)?.let {
        EnergyMetric(it.optString("label"), it.optString("value"), it.optString("unit").takeIf(String::isNotBlank))
    }

    private fun readSeries(array: JSONArray?): List<EnergySeriesPoint> = buildList {
        if (array == null) return@buildList
        for (i in 0 until array.length()) array.optJSONObject(i)?.let {
            add(EnergySeriesPoint(it.optString("label"), it.optDouble("value"), it.optString("endLabel").takeIf(String::isNotBlank)))
        }
    }

    private fun readCategories(array: JSONArray?): List<EnergyCategory> = buildList {
        if (array == null) return@buildList
        for (i in 0 until array.length()) array.optJSONObject(i)?.let { add(EnergyCategory(it.optString("label"), it.optDouble("value"))) }
    }

    private fun readOther(array: JSONArray?): List<Pair<String, String>> = buildList {
        if (array == null) return@buildList
        for (i in 0 until array.length()) array.optJSONObject(i)?.let { add(it.optString("key") to it.optString("value")) }
    }
}
