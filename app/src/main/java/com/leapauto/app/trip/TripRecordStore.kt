// [FEATURE: TRIP_LOG] - 行程数据本地独立持久化存储
package com.leapauto.app.trip

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class TripRecordStore(context: Context) {
    private val prefs = context.getSharedPreferences("leapauto_trip_records", Context.MODE_PRIVATE)

    @Synchronized
    fun loadRecords(vin: String): List<TripRecord> {
        if (vin.isBlank()) return emptyList()
        val raw = prefs.getString("records_$vin", null) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { i ->
                array.optJSONObject(i)?.let { TripRecord.fromJson(it) }
            }.sortedByDescending { it.endTimeEpochMs }
        } catch (_: Exception) {
            emptyList()
        }
    }

    @Synchronized
    fun saveRecord(record: TripRecord) {
        val current = loadRecords(record.vin).toMutableList()
        current.removeAll { it.id == record.id }
        current.add(0, record)
        // 最多保留最近 200 条行程，防止本地存储膨胀
        val trimmed = current.take(200)
        val array = JSONArray()
        trimmed.forEach { array.put(it.toJson()) }
        prefs.edit().putString("records_${record.vin}", array.toString()).apply()
    }

    @Synchronized
    fun saveRecords(vin: String, records: List<TripRecord>) {
        if (vin.isBlank()) return
        val trimmed = records.take(200)
        val array = JSONArray()
        trimmed.forEach { array.put(it.toJson()) }
        prefs.edit().putString("records_$vin", array.toString()).apply()
    }

    @Synchronized
    fun updateLatestLocation(vin: String, location: String) {
        if (vin.isBlank() || location.isBlank()) return
        val cp = loadCheckpoint(vin)
        if (cp != null && (cp.locationName == null || !cp.isDriving)) {
            saveCheckpoint(cp.copy(locationName = location))
        }
        val records = loadRecords(vin).toMutableList()
        if (records.isNotEmpty()) {
            val first = records[0]
            val isRecent = System.currentTimeMillis() - first.endTimeEpochMs < 30 * 60 * 1000L
            if (isRecent) {
                records[0] = first.copy(endLocation = location)
                saveRecords(vin, records)
            }
        }
    }

    @Synchronized
    fun deleteRecord(vin: String, recordId: String) {
        val current = loadRecords(vin).filterNot { it.id == recordId }
        val array = JSONArray()
        current.forEach { array.put(it.toJson()) }
        prefs.edit().putString("records_$vin", array.toString()).apply()
    }

    @Synchronized
    fun clearRecords(vin: String) {
        prefs.edit().remove("records_$vin").apply()
    }

    @Synchronized
    fun clearAll(vin: String) {
        prefs.edit().remove("records_$vin").remove("checkpoint_$vin").apply()
    }

    fun registerListener(listener: android.content.SharedPreferences.OnSharedPreferenceChangeListener) {
        prefs.registerOnSharedPreferenceChangeListener(listener)
    }

    fun unregisterListener(listener: android.content.SharedPreferences.OnSharedPreferenceChangeListener) {
        prefs.unregisterOnSharedPreferenceChangeListener(listener)
    }

    @Synchronized
    fun loadCheckpoint(vin: String): TripCheckpoint? {
        if (vin.isBlank()) return null
        val raw = prefs.getString("checkpoint_$vin", null) ?: return null
        return try {
            TripCheckpoint.fromJson(JSONObject(raw))
        } catch (_: Exception) {
            null
        }
    }

    @Synchronized
    fun saveCheckpoint(checkpoint: TripCheckpoint) {
        if (checkpoint.vin.isBlank()) return
        prefs.edit().putString("checkpoint_${checkpoint.vin}", checkpoint.toJson().toString()).apply()
    }

    fun markUnlockDeparture(vin: String, timestampMs: Long = System.currentTimeMillis()) {
        if (vin.isBlank()) return
        prefs.edit().putLong("unlock_departure_$vin", timestampMs).apply()
    }

    fun getUnlockDeparture(vin: String, maxAgeMs: Long = 90 * 60 * 1000L): Long? {
        if (vin.isBlank()) return null
        val ts = prefs.getLong("unlock_departure_$vin", 0L)
        if (ts <= 0L) return null
        val age = System.currentTimeMillis() - ts
        return if (age in 0..maxAgeMs) ts else null
    }

    fun clearUnlockDeparture(vin: String) {
        if (vin.isBlank()) return
        prefs.edit().remove("unlock_departure_$vin").apply()
    }

    fun hasConfiguredTripLogParams(vin: String): Boolean {
        if (vin.isBlank()) return true
        val hasCustomCapacity = prefs.contains("battery_capacity_$vin")
        val hasCustomPrice = prefs.contains("electricity_price")
        return prefs.getBoolean("trip_log_configured_$vin", hasCustomCapacity && hasCustomPrice)
    }

    fun setTripLogParamsConfigured(vin: String, configured: Boolean = true) {
        if (vin.isBlank()) return
        prefs.edit().putBoolean("trip_log_configured_$vin", configured).apply()
    }

    fun loadBatteryCapacityKwh(vin: String, carType: String = "", carTypeCode: String = ""): Double {
        val f = prefs.getFloat("battery_capacity_$vin", 0f)
        if (f > 0f) {
            return String.format(java.util.Locale.US, "%.1f", f).toDoubleOrNull() ?: f.toDouble()
        }
        return VehicleBatteryCapacityTable.resolve(carType, carTypeCode)
    }

    fun saveBatteryCapacityKwh(vin: String, capacity: Double) {
        if (vin.isBlank()) return
        val rounded = String.format(java.util.Locale.US, "%.1f", capacity.coerceIn(10.0, 150.0)).toFloat()
        prefs.edit().putFloat("battery_capacity_$vin", rounded).apply()
    }

    fun loadElectricityPrice(): Double {
        val f = prefs.getFloat("electricity_price", 0.58f)
        return String.format(java.util.Locale.US, "%.2f", f).toDoubleOrNull() ?: 0.58
    }

    fun saveElectricityPrice(price: Double) {
        val rounded = String.format(java.util.Locale.US, "%.2f", price.coerceIn(0.1, 5.0)).toFloat()
        prefs.edit().putFloat("electricity_price", rounded).apply()
    }

    fun loadFuelPrice(): Double {
        val f = prefs.getFloat("fuel_price", DEFAULT_FUEL_PRICE.toFloat())
        return String.format(java.util.Locale.US, "%.2f", f).toDoubleOrNull() ?: DEFAULT_FUEL_PRICE
    }

    fun saveFuelPrice(price: Double) {
        val rounded = String.format(java.util.Locale.US, "%.2f", price.coerceIn(3.0, 15.0)).toFloat()
        prefs.edit().putFloat("fuel_price", rounded).apply()
    }

    fun loadFuelTankCapacityLiters(vin: String, carType: String = "", carTypeCode: String = ""): Double {
        val f = prefs.getFloat("fuel_tank_capacity_$vin", 0f)
        if (f > 0f) {
            return String.format(java.util.Locale.US, "%.1f", f).toDoubleOrNull() ?: f.toDouble()
        }
        return VehicleBatteryCapacityTable.resolveFuelTankCapacityLiters(carType, carTypeCode)
    }

    fun saveFuelTankCapacityLiters(vin: String, capacity: Double) {
        if (vin.isBlank()) return
        val rounded = String.format(java.util.Locale.US, "%.1f", capacity.coerceIn(0.0, 100.0)).toFloat()
        prefs.edit().putFloat("fuel_tank_capacity_$vin", rounded).apply()
    }

    companion object {
        const val DEFAULT_FUEL_PRICE = 8.35 // 2026年11月全国 92号汽油最新均价 8.35 元/L

        fun computeSummary(records: List<TripRecord>): TripSummary {
            val drivingTrips = records.filter { it.type == TripType.DRIVING }
            val totalDistance = drivingTrips.sumOf { it.distanceKm }
            val totalKwh = drivingTrips.sumOf { it.consumedKwh }
            val totalCost = drivingTrips.sumOf { it.estimatedCostYuan }
            val totalFuel = drivingTrips.sumOf { it.fuelConsumedLiters ?: 0.0 }
            val avgKwh = if (totalDistance > 0) (totalKwh / totalDistance) * 100.0 else 0.0

            return TripSummary(
                totalDistanceKm = totalDistance,
                totalKwh = totalKwh,
                avgKwhPer100Km = avgKwh,
                totalCostYuan = totalCost,
                tripCount = drivingTrips.size,
                totalFuelLiters = totalFuel
            )
        }
    }
}
