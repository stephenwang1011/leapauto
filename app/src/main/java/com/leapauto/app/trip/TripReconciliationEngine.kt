// [FEATURE: TRIP_LOG] - 纯遥测差分对账核心引擎
package com.leapauto.app.trip

import android.content.Context
import java.util.UUID
import kotlin.math.roundToInt

object TripReconciliationEngine {
    const val MIN_TRIP_DISTANCE_KM = 0.5

    /** 解析零跑全系车型的动力电池标称度数 (kWh) */
    fun resolveBatteryCapacityKwh(carType: String, carTypeCode: String = ""): Double {
        return VehicleBatteryCapacityTable.resolve(carType, carTypeCode)
    }

    /**
     * 纯函数核心差分对账算法：
     * 给定前序检查点与最新车况检查点，计算是否满足单次行程闭环。
     */
    fun reconcile(
        previous: TripCheckpoint?,
        current: TripCheckpoint,
        batteryCapacityKwh: Double,
        pricePerKwh: Double,
        unlockDepartureEpochMs: Long? = null,
        fuelTankCapacityLiters: Double = 0.0,
        pricePerFuelLiter: Double = TripRecordStore.DEFAULT_FUEL_PRICE
    ): Pair<TripRecord?, TripCheckpoint> {
        if (previous == null) {
            // 初次接入，建立首个驻车基线锚点
            return Pair(null, current)
        }

        val distance = (current.mileage - previous.mileage).coerceAtLeast(0.0)
        val roundedDistance = (distance * 10.0).roundToInt() / 10.0

        val isDrivingGear = when (current.gear?.trim()?.uppercase()) {
            "D", "D挡", "DRIVE", "前进", "3", "R", "R挡", "REVERSE", "倒车", "1" -> true
            else -> false
        }
        val isExplicitParkGear = when (current.gear?.trim()?.uppercase()) {
            "P", "P挡", "PARK", "驻车", "停车", "0" -> true
            else -> false
        }
        // 强门禁：D/R 行车挡严禁判定为驻车（彻底杜绝等红绿灯时因车速为 0 误切断行程）；
        // 只有明确挂入 P 挡（或无行车挡且整车未行驶）才判定为已驻车并触发结算
        val isParked = !isDrivingGear && (isExplicitParkGear || (!current.isDriving && current.gear == null))
        if (roundedDistance >= MIN_TRIP_DISTANCE_KM && isParked) {
            // [提升点 2：长下坡微幅动能回收与插枪充电精准分离]
            val socRise = current.soc - previous.soc
            val charged = socRise >= 1.5
            val isRegenBraking = socRise in 0.001..1.5
            val socDelta = if (charged || isRegenBraking) 0.0 else (previous.soc - current.soc).coerceAtLeast(0.0)
            val consumedKwh = (socDelta / 100.0) * batteryCapacityKwh
            val roundedConsumedKwh = if (consumedKwh > 0.001) {
                maxOf(0.1, (consumedKwh * 10.0).roundToInt() / 10.0)
            } else 0.0

            // [提升点 4：增程车型燃油消耗与综合花费核算]
            val fuelConsumed = if (fuelTankCapacityLiters > 0.0 && previous.fuelSoc != null && current.fuelSoc != null) {
                val fuelDelta = (previous.fuelSoc - current.fuelSoc).coerceAtLeast(0.0)
                val liters = (fuelDelta / 100.0) * fuelTankCapacityLiters
                if (liters > 0.05) {
                    (liters * 10.0).roundToInt() / 10.0
                } else 0.0
            } else null

            // [提升点 3：起步短途 (0.5~1.5km) 百公里能耗毛刺滤波]
            val rawAvgKwh = if (roundedDistance > 0 && roundedConsumedKwh > 0.0) {
                (roundedConsumedKwh / roundedDistance) * 100.0
            } else 0.0
            val effectiveAvgKwh = if (roundedDistance < 1.5 && rawAvgKwh > 32.0) {
                32.0
            } else {
                rawAvgKwh
            }
            val roundedAvgKwh = (effectiveAvgKwh * 10.0).roundToInt() / 10.0

            val electricityCost = if (roundedConsumedKwh > 0.0) {
                (roundedConsumedKwh * pricePerKwh * 100.0).roundToInt() / 100.0
            } else 0.0
            val fuelCost = if (fuelConsumed != null && fuelConsumed > 0.0) {
                (fuelConsumed * pricePerFuelLiter * 100.0).roundToInt() / 100.0
            } else 0.0
            val totalCost = electricityCost + fuelCost
            val roundedCost = if (totalCost > 0.0) {
                maxOf(0.01, (totalCost * 100.0).roundToInt() / 100.0)
            } else 0.0

            // [提升点 1：消除 50km 悬崖断层，基于物理时速异常率自适应判定隔夜停放]
            val rawDuration = (current.timestampEpochMs - previous.timestampEpochMs).coerceAtLeast(0L)
            val rawHours = rawDuration / 3600000.0
            val startTime = when {
                unlockDepartureEpochMs != null && unlockDepartureEpochMs > previous.timestampEpochMs && unlockDepartureEpochMs < current.timestampEpochMs ->
                    unlockDepartureEpochMs
                rawDuration > 3 * 3600 * 1000L && rawHours > 0 && (roundedDistance / rawHours) < 15.0 -> {
                    val estimatedSpeedKmh = when {
                        roundedDistance < 20.0 -> 30.0
                        roundedDistance < 60.0 -> 45.0
                        else -> 65.0
                    }
                    val estimatedDriveMs = ((roundedDistance / estimatedSpeedKmh) * 3600 * 1000L).toLong()
                        .coerceIn(3 * 60 * 1000L, 5 * 3600 * 1000L)
                    maxOf(previous.timestampEpochMs, current.timestampEpochMs - estimatedDriveMs)
                }
                else -> previous.timestampEpochMs
            }

            val record = TripRecord(
                id = UUID.randomUUID().toString(),
                vin = current.vin,
                startTimeEpochMs = startTime,
                endTimeEpochMs = current.timestampEpochMs,
                startMileage = previous.mileage,
                endMileage = current.mileage,
                distanceKm = roundedDistance,
                startSoc = previous.soc,
                endSoc = current.soc,
                consumedKwh = roundedConsumedKwh,
                avgKwhPer100Km = roundedAvgKwh,
                estimatedCostYuan = roundedCost,
                fuelConsumedLiters = fuelConsumed,
                startLocation = previous.locationName,
                endLocation = current.locationName,
                type = TripType.DRIVING,
                chargedDuringTrip = charged
            )

            // 结算完成，将当前驻车状态转为新的锚点
            return Pair(record, current)
        }

        // 行驶中或未达到结算阈值，保留原锚点里程与起点信息
        val updatedCheckpoint = if (current.isDriving || isDrivingGear) {
            previous.copy(isDriving = true, gear = current.gear)
        } else {
            // 静止但未位移（如夜间静置、开哨兵、插枪充电、加油站加油）：
            // 动态校准基准电量与油量，确保出发前的驻车损耗或补能不污染行车电耗
            previous.copy(
                timestampEpochMs = if (distance < 0.1) previous.timestampEpochMs else current.timestampEpochMs,
                soc = if (distance < 0.1) current.soc else previous.soc,
                fuelSoc = if (distance < 0.1) current.fuelSoc else previous.fuelSoc,
                isDriving = false,
                gear = current.gear,
                locationName = current.locationName ?: previous.locationName
            )
        }
        return Pair(null, updatedCheckpoint)
    }

    /** 接入车况更新的调度入口 */
    fun processTelemetry(
        context: Context,
        vin: String,
        mileageKm: Double?,
        socPercent: Double?,
        isDriving: Boolean,
        gear: String?,
        locationName: String?,
        carType: String,
        fuelSocPercent: Double? = null
    ): TripRecord? {
        if (vin.isBlank() || mileageKm == null || mileageKm <= 0 || socPercent == null || socPercent < 0) {
            return null
        }
        val store = TripRecordStore(context)
        val prev = store.loadCheckpoint(vin)
        val current = TripCheckpoint(
            vin = vin,
            timestampEpochMs = System.currentTimeMillis(),
            mileage = mileageKm,
            soc = socPercent,
            isDriving = isDriving,
            gear = gear,
            locationName = locationName,
            fuelSoc = fuelSocPercent
        )
        val capacity = store.loadBatteryCapacityKwh(vin, carType)
        val fuelTankCapacity = store.loadFuelTankCapacityLiters(vin, carType)
        val price = store.loadElectricityPrice()
        val fuelPrice = store.loadFuelPrice()
        val unlockTs = store.getUnlockDeparture(vin)

        val (newRecord, nextCheckpoint) = reconcile(
            previous = prev,
            current = current,
            batteryCapacityKwh = capacity,
            pricePerKwh = price,
            unlockDepartureEpochMs = unlockTs,
            fuelTankCapacityLiters = fuelTankCapacity,
            pricePerFuelLiter = fuelPrice
        )
        if (newRecord != null) {
            store.saveRecord(newRecord)
            store.clearUnlockDeparture(vin)
        }
        store.saveCheckpoint(nextCheckpoint)
        return newRecord
    }
}
