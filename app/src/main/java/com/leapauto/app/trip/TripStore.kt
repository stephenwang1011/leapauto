package com.leapauto.app.trip

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.UUID

/** 行程数据本地持久化与状态机调度服务 */
class TripStore(
    private val context: Context? = null,
    private val baseDir: File? = null
) {

    private fun safeLog(block: () -> Unit) {
        runCatching { block() }
    }

    private val prefs = context?.getSharedPreferences("leapauto_trip_prefs", Context.MODE_PRIVATE)
    private var testEnabled = true
    private val testUserClearedVins = mutableSetOf<String>()

    companion object {
        private const val TAG = "TripStore"
        private const val KEY_TRIP_RECORD_ENABLED = "trip_record_enabled"
        private const val MAX_SAVED_DAYS = 30 // 最多保留最近 30 天数据，超过后滚动覆盖淘汰最久远的那一天
        private const val INACTIVITY_END_TIMEOUT_MS = 3 * 60 * 1000L // 停稳挂 P 挡超过 3 分钟自动结算行程

        @Volatile
        private var activeTrip: ActiveTripSnapshot? = null

        fun getActiveTrip(): ActiveTripSnapshot? = activeTrip

        fun resetActiveTripForTesting() {
            activeTrip = null
        }
    }

    fun isTripRecordEnabled(): Boolean {
        return prefs?.getBoolean(KEY_TRIP_RECORD_ENABLED, false) ?: testEnabled
    }

    fun setTripRecordEnabled(enabled: Boolean) {
        if (prefs != null) {
            prefs.edit().putBoolean(KEY_TRIP_RECORD_ENABLED, enabled).apply()
        } else {
            testEnabled = enabled
        }
    }

    /**
     * 核心遥测事件喂入状态机：
     * 每次车辆数据刷新时调用此方法，自动判断行程开始、中途推进或到站结算。
     *
     * @return 若本次刚好结算完成了一次有效行程，返回该 [TripRecord]，否则返回 null
     */
    @Synchronized
    fun processTelemetry(
        vin: String,
        totalMileageStr: String?,
        socStr: String?,
        speedStr: String?,
        gearStatus: String?,
        isDriving: Boolean?,
        isShutDown: Boolean,
        currentAddress: String = "",
        nowEpochMs: Long = System.currentTimeMillis(),
        isBluetoothConnectTrigger: Boolean = false
    ): TripRecord? {
        if (!isTripRecordEnabled() || vin.isBlank()) return null

        val currentMileage = parseMileageKm(totalMileageStr) ?: return null
        val currentSoc = parseSocPercent(socStr) ?: return null
        val speedValue = parseSpeedKmh(speedStr)
        val isDrivingGear = gearStatus?.trim()?.uppercase() in setOf("D", "D挡", "DRIVE", "前进", "3", "R", "R挡", "REVERSE", "倒车", "1")
        val isCurrentlyMoving = isDrivingGear || isDriving == true || speedValue > 1.0f

        var current = activeTrip ?: loadActiveTripFromDisk(vin, nowEpochMs)
        if (current == null) {
            // 1. 尚未有活动行程：
            // 条件A: 检测到挂挡行驶或有速度
            // 条件B: 蓝牙连接事件触发（即使处于 P 挡/0车速，但车辆未熄火，提前锁定潜在行程起点）
            val shouldStartTrip = (isCurrentlyMoving || isBluetoothConnectTrigger) && !isShutDown
            if (shouldStartTrip) {
                val newActive = ActiveTripSnapshot(
                    vin = vin,
                    startTimeEpochMs = nowEpochMs,
                    startMileageKm = currentMileage,
                    startSocPercent = currentSoc,
                    startAddress = currentAddress,
                    lastActiveTimeEpochMs = nowEpochMs,
                    currentMileageKm = currentMileage,
                    currentSocPercent = currentSoc,
                    currentAddress = currentAddress,
                    maxSpeedKmh = speedValue.toDouble()
                )
                persistActiveTrip(newActive)
                safeLog { Log.i(TAG, "检测到车辆出发或蓝牙连接，开启新行程: vin=$vin, startKm=$currentMileage, startSoc=$currentSoc") }
            }
            return null
        } else {
            // 2. 当前已有活动行程中
            if (current.vin != vin) {
                // 切换了车辆，强制结算旧车并清理旧车活动文件
                val closed = finalizeTrip(current, currentMileage, currentSoc, currentAddress, nowEpochMs)
                clearActiveTripFile(current.vin)
                return closed
            }

            // 若本次是蓝牙连接触发，且已有活动行程在 10 分钟内活跃过，保持原起点，仅刷新活跃时间
            if (isBluetoothConnectTrigger) {
                current.lastActiveTimeEpochMs = nowEpochMs
                persistActiveTrip(current)
                return null
            }

            if (isCurrentlyMoving) {
                // 持续行驶中：更新最新位姿并持久化
                current.lastActiveTimeEpochMs = nowEpochMs
                current.currentMileageKm = maxOf(current.currentMileageKm, currentMileage)
                current.currentSocPercent = currentSoc
                // 若起点地标此前为空（如地库出发无 GPS），在行驶途中首次获得有效地址时，自动补录回填起点
                if (current.startAddress.isBlank() && currentAddress.isNotBlank()) {
                    current = current.copy(startAddress = currentAddress)
                }
                current.currentAddress = currentAddress.ifBlank { current.currentAddress }
                current.maxSpeedKmh = maxOf(current.maxSpeedKmh, speedValue.toDouble())
                persistActiveTrip(current)
                return null
            } else {
                // 车辆已静止：判断是否满足行程闭环条件（车辆已熄火、或者已停稳超时 3 分钟）
                val isParkTimeExceeded = (nowEpochMs - current.lastActiveTimeEpochMs) >= INACTIVITY_END_TIMEOUT_MS
                if (isShutDown || isParkTimeExceeded) {
                    val resolvedEndAddress = currentAddress.ifBlank { current.currentAddress }
                    val completedTrip = finalizeTrip(current, currentMileage, currentSoc, resolvedEndAddress, nowEpochMs)
                    clearActiveTripFile(current.vin)
                    return completedTrip
                }
                return null
            }
        }
    }

    /** 结算并归档活动行程 */
    private fun finalizeTrip(
        snapshot: ActiveTripSnapshot,
        endMileageKm: Double,
        endSocPercent: Double,
        endAddress: String,
        endEpochMs: Long
    ): TripRecord? {
        val distance = roundOneDecimal(maxOf(0.0, endMileageKm - snapshot.startMileageKm))
        val durationSeconds = ((endEpochMs - snapshot.startTimeEpochMs) / 1000L).coerceAtLeast(1L)
        val socDelta = roundOneDecimal(maxOf(0.0, snapshot.startSocPercent - endSocPercent))

        // 复合有效性门槛判定：
        // 1. 里程位移 >= 0.2 km（正式出发，最清晰明确的标准）
        // 2. 或者：位移 > 0.0 km 且持续时长 >= 60 秒（短途挪移/去门口便利店等）
        // 3. 或者：持续时长 >= 180 秒（3分钟以上）且最高车速 >= 10 km/h（哪怕车端里程截断四舍五入差为 0，车也明确在道路上行驶了）
        val isValidTrip = distance >= 0.20 ||
            (distance > 0.0 && durationSeconds >= 60L) ||
            (durationSeconds >= 180L && snapshot.maxSpeedKmh >= 10.0)

        if (!isValidTrip) {
            safeLog { Log.d(TAG, "行程位移或时长过小，判定为挪车或原地怠速误触，跳过保存: dist=$distance km, duration=$durationSeconds s, maxSpeed=${snapshot.maxSpeedKmh} km/h") }
            return null
        }

        val hours = durationSeconds.toDouble() / 3600.0
        val avgSpeed = if (hours > 0.0 && distance > 0.0) {
            roundOneDecimal(distance / hours)
        } else 0.0

        val energyPer100Km = if (distance >= 1.0 && socDelta > 0.0) {
            // 按照典型纯电/增程电池组可用电量(约 65 kWh 基准)估算等效电耗：消耗 kWh / 里程 * 100
            val estimatedKwh = socDelta * 0.65
            roundOneDecimal((estimatedKwh / distance) * 100.0)
        } else null

        val record = TripRecord(
            id = UUID.randomUUID().toString(),
            vin = snapshot.vin,
            startTimeEpochMs = snapshot.startTimeEpochMs,
            endTimeEpochMs = endEpochMs,
            startMileageKm = snapshot.startMileageKm,
            endMileageKm = endMileageKm,
            distanceKm = distance,
            durationSeconds = durationSeconds,
            startSocPercent = snapshot.startSocPercent,
            endSocPercent = endSocPercent,
            socDeltaPercent = socDelta,
            avgSpeedKmh = avgSpeed,
            maxSpeedKmh = roundOneDecimal(snapshot.maxSpeedKmh),
            startAddress = snapshot.startAddress.ifBlank { snapshot.currentAddress }.ifBlank { endAddress },
            endAddress = endAddress.ifBlank { snapshot.currentAddress }.ifBlank { snapshot.startAddress },
            energyConsumptionKwhPer100Km = energyPer100Km
        )

        saveTrip(record)
        safeLog { Log.i(TAG, "行程结束并归档保存: distance=${record.distanceKm}km, duration=${record.formattedDuration}, avgSpeed=${record.avgSpeedKmh}km/h") }
        return record
    }

    /** 读取指定车辆的所有历史行程记录（按时间倒序） */
    @Synchronized
    fun getTrips(vin: String): List<TripRecord> {
        if (vin.isBlank()) return emptyList()
        val file = getTripFile(vin)
        if (!file.exists()) return emptyList()
        return try {
            val jsonStr = file.readText()
            val array = JSONArray(jsonStr)
            val list = mutableListOf<TripRecord>()
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                list.add(TripRecord.fromJson(obj))
            }
            list.sortedByDescending { it.startTimeEpochMs }
        } catch (e: Exception) {
            safeLog { Log.w(TAG, "读取行程记录异常: ${e.message}") }
            emptyList()
        }
    }

    /** 持久化保存一条行程 */
    @Synchronized
    fun saveTrip(record: TripRecord) {
        val safeVin = record.vin.replace(Regex("[^A-Za-z0-9]"), "_")
        if (prefs != null) {
            prefs.edit().remove("user_cleared_$safeVin").apply()
        } else {
            testUserClearedVins.remove(safeVin)
        }
        val existing = getTrips(record.vin).toMutableList()
        existing.removeAll { it.id == record.id }
        existing.add(0, record)

        // 严格按最多保留 30 个自然日进行滚动淘汰：提取最新的 30 个日期，淘汰日期最久远的所有行程
        val allowedDates = existing.map { it.dateGroupKey }.distinct().sortedDescending().take(MAX_SAVED_DAYS).toSet()
        val capped = existing.filter { it.dateGroupKey in allowedDates }

        val array = JSONArray()
        for (item in capped) {
            array.put(item.toJson())
        }
        val file = getTripFile(record.vin)
        try {
            file.writeText(array.toString())
        } catch (e: Exception) {
            safeLog { Log.e(TAG, "写入行程文件失败: ${e.message}") }
        }
    }

    /** 清空指定车辆的历史行程记录 */
    @Synchronized
    fun clearTrips(vin: String) {
        if (vin.isBlank()) return
        val file = getTripFile(vin)
        if (file.exists()) {
            file.delete()
        }
        clearActiveTripFile(vin)
        val safeVin = vin.replace(Regex("[^A-Za-z0-9]"), "_")
        if (prefs != null) {
            prefs.edit().putBoolean("user_cleared_$safeVin", true).apply()
        } else {
            testUserClearedVins.add(safeVin)
        }
    }

    /** 清除之前演练时写入的历史测试假数据（若存在） */
    @Synchronized
    fun removeLegacyMockTripsIfPresent(vin: String) {
        if (vin.isBlank()) return
        val currentTrips = getTrips(vin)
        val hasMock = currentTrips.any {
            it.startAddress == "软件园二期" || it.startAddress == "吾悦华府 A区" || it.startAddress == "万象汇商圈"
        }
        if (hasMock) {
            clearTrips(vin)
        }
    }

    fun loadActiveTrip(vin: String, nowEpochMs: Long = System.currentTimeMillis()): ActiveTripSnapshot? {
        return activeTrip ?: loadActiveTripFromDisk(vin, nowEpochMs)
    }

    private fun loadActiveTripFromDisk(vin: String, nowEpochMs: Long = System.currentTimeMillis()): ActiveTripSnapshot? {
        if (vin.isBlank()) return null
        val file = getActiveTripFile(vin)
        if (!file.exists()) return null
        return try {
            val json = JSONObject(file.readText())
            val snapshot = ActiveTripSnapshot.fromJson(json)
            // 过期保护：若最后活跃时间距离当前超过 24 小时，视为废弃行程并清理
            if (nowEpochMs - snapshot.lastActiveTimeEpochMs > 24 * 3600 * 1000L) {
                file.delete()
                null
            } else {
                activeTrip = snapshot
                snapshot
            }
        } catch (e: Exception) {
            safeLog { Log.w(TAG, "读取活动行程文件异常: ${e.message}") }
            file.delete()
            null
        }
    }

    private fun persistActiveTrip(snapshot: ActiveTripSnapshot) {
        activeTrip = snapshot
        try {
            val file = getActiveTripFile(snapshot.vin)
            file.writeText(snapshot.toJson().toString())
        } catch (e: Exception) {
            safeLog { Log.w(TAG, "持久化活动行程快照失败: ${e.message}") }
        }
    }

    private fun clearActiveTripFile(vin: String) {
        if (activeTrip?.vin == vin) {
            activeTrip = null
        }
        if (vin.isBlank()) return
        try {
            val file = getActiveTripFile(vin)
            if (file.exists()) file.delete()
        } catch (e: Exception) {
            safeLog { Log.w(TAG, "清理活动行程快照文件失败: ${e.message}") }
        }
    }

    private fun getActiveTripFile(vin: String): File {
        val safeVin = vin.replace(Regex("[^A-Za-z0-9]"), "_")
        val dir = baseDir ?: context?.filesDir ?: File(".")
        if (!dir.exists()) dir.mkdirs()
        return File(dir, "active_trip_${safeVin}.json")
    }

    private fun getTripFile(vin: String): File {
        val safeVin = vin.replace(Regex("[^A-Za-z0-9]"), "_")
        val dir = baseDir ?: context?.filesDir ?: File(".")
        if (!dir.exists()) dir.mkdirs()
        return File(dir, "trips_${safeVin}.json")
    }

    private fun parseMileageKm(str: String?): Double? {
        if (str == null) return null
        val clean = str.replace("km", "", ignoreCase = true).trim()
        return clean.toDoubleOrNull()
    }

    private fun parseSocPercent(str: String?): Double? {
        if (str == null) return null
        val clean = str.replace("%", "").trim()
        return clean.toDoubleOrNull()
    }

    private fun parseSpeedKmh(str: String?): Float {
        if (str == null) return 0f
        val clean = str.replace("km/h", "", ignoreCase = true).trim()
        return clean.toFloatOrNull() ?: 0f
    }

    private fun roundOneDecimal(value: Double): Double {
        return BigDecimal(value).setScale(1, RoundingMode.HALF_UP).toDouble()
    }
}
