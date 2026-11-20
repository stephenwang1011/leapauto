// [FEATURE: TRIP_LOG] - 行程与能耗记账核心数据模型
package com.leapauto.app.trip

import org.json.JSONObject

enum class TripType {
    DRIVING,            // 行车行驶
    STATIONARY_AC       // 原地驻车空调
}

data class TripRecord(
    val id: String,
    val vin: String,
    val startTimeEpochMs: Long,
    val endTimeEpochMs: Long,
    val startMileage: Double,
    val endMileage: Double,
    val distanceKm: Double,
    val startSoc: Double,
    val endSoc: Double,
    val consumedKwh: Double,
    val avgKwhPer100Km: Double,
    val estimatedCostYuan: Double,
    val fuelConsumedLiters: Double? = null,
    val startLocation: String? = null,
    val endLocation: String? = null,
    val type: TripType = TripType.DRIVING,
    val chargedDuringTrip: Boolean = false
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("vin", vin)
        put("startTimeEpochMs", startTimeEpochMs)
        put("endTimeEpochMs", endTimeEpochMs)
        put("startMileage", startMileage)
        put("endMileage", endMileage)
        put("distanceKm", distanceKm)
        put("startSoc", startSoc)
        put("endSoc", endSoc)
        put("consumedKwh", consumedKwh)
        put("avgKwhPer100Km", avgKwhPer100Km)
        put("estimatedCostYuan", estimatedCostYuan)
        fuelConsumedLiters?.let { put("fuelConsumedLiters", it) }
        startLocation?.let { put("startLocation", it) }
        endLocation?.let { put("endLocation", it) }
        put("type", type.name)
        put("chargedDuringTrip", chargedDuringTrip)
    }

    companion object {
        fun fromJson(json: JSONObject): TripRecord? {
            val id = json.optString("id").takeIf { it.isNotBlank() } ?: return null
            val vin = json.optString("vin").takeIf { it.isNotBlank() } ?: return null
            val startMs = json.optLong("startTimeEpochMs", 0L)
            val endMs = json.optLong("endTimeEpochMs", 0L)
            val distance = json.optDouble("distanceKm", 0.0)
            val startSoc = json.optDouble("startSoc", 0.0)
            val endSoc = json.optDouble("endSoc", 0.0)
            val consumedKwh = json.optDouble("consumedKwh", 0.0)
            val avgKwh = json.optDouble("avgKwhPer100Km", 0.0)
            val cost = json.optDouble("estimatedCostYuan", 0.0)
            val fuel = if (json.has("fuelConsumedLiters")) json.optDouble("fuelConsumedLiters") else null
            val startLoc = json.optString("startLocation").takeIf { it.isNotBlank() }
            val endLoc = json.optString("endLocation").takeIf { it.isNotBlank() }
            val typeStr = json.optString("type", TripType.DRIVING.name)
            val type = runCatching { TripType.valueOf(typeStr) }.getOrDefault(TripType.DRIVING)
            val charged = json.optBoolean("chargedDuringTrip", false)
            val startMil = json.optDouble("startMileage", 0.0)
            val endMil = json.optDouble("endMileage", startMil + distance)

            return TripRecord(
                id = id,
                vin = vin,
                startTimeEpochMs = startMs,
                endTimeEpochMs = endMs,
                startMileage = startMil,
                endMileage = endMil,
                distanceKm = distance,
                startSoc = startSoc,
                endSoc = endSoc,
                consumedKwh = consumedKwh,
                avgKwhPer100Km = avgKwh,
                estimatedCostYuan = cost,
                fuelConsumedLiters = fuel,
                startLocation = startLoc,
                endLocation = endLoc,
                type = type,
                chargedDuringTrip = charged
            )
        }
    }
}

/** 驻车检查点：用于纯遥测差分对账 */
data class TripCheckpoint(
    val vin: String,
    val timestampEpochMs: Long,
    val mileage: Double,
    val soc: Double,
    val isDriving: Boolean,
    val gear: String? = null,
    val locationName: String? = null,
    val fuelSoc: Double? = null
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("vin", vin)
        put("timestampEpochMs", timestampEpochMs)
        put("mileage", mileage)
        put("soc", soc)
        put("isDriving", isDriving)
        gear?.let { put("gear", it) }
        locationName?.let { put("locationName", it) }
        fuelSoc?.let { put("fuelSoc", it) }
    }

    companion object {
        fun fromJson(json: JSONObject): TripCheckpoint? {
            val vin = json.optString("vin").takeIf { it.isNotBlank() } ?: return null
            val ts = json.optLong("timestampEpochMs", 0L)
            val mileage = json.optDouble("mileage", -1.0)
            if (mileage < 0) return null
            val soc = json.optDouble("soc", -1.0)
            if (soc < 0) return null
            val driving = json.optBoolean("isDriving", false)
            val gear = json.optString("gear").takeIf { it.isNotBlank() }
            val loc = json.optString("locationName").takeIf { it.isNotBlank() }
            val fuelSoc = if (json.has("fuelSoc")) json.optDouble("fuelSoc") else null

            return TripCheckpoint(
                vin = vin,
                timestampEpochMs = ts,
                mileage = mileage,
                soc = soc,
                isDriving = driving,
                gear = gear,
                locationName = loc,
                fuelSoc = fuelSoc
            )
        }
    }
}

data class TripSummary(
    val totalDistanceKm: Double,
    val totalKwh: Double,
    val avgKwhPer100Km: Double,
    val totalCostYuan: Double,
    val tripCount: Int,
    val totalFuelLiters: Double = 0.0
)
