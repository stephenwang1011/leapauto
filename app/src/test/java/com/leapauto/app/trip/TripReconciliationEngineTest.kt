// [FEATURE: TRIP_LOG] - 差分对账引擎单元测试
package com.leapauto.app.trip

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TripReconciliationEngineTest {

    private val vin = "LFZ63AZ55SH023503"
    private val capacity = 67.7 // C16 纯电 67.7 kWh
    private val price = 0.58    // 0.58 元/度

    @Test
    fun `initial update establishes first baseline checkpoint without generating trip`() {
        val current = TripCheckpoint(
            vin = vin,
            timestampEpochMs = 1000L,
            mileage = 23300.0,
            soc = 90.0,
            isDriving = false,
            gear = "P"
        )
        val (record, checkpoint) = TripReconciliationEngine.reconcile(null, current, capacity, price)

        assertNull("初次接入不生成行程单", record)
        assertEquals(current, checkpoint)
    }

    @Test
    fun `driving distance above half km and parked generates accurate trip record`() {
        val start = TripCheckpoint(
            vin = vin,
            timestampEpochMs = 1000L,
            mileage = 23300.0,
            soc = 90.0,
            isDriving = false,
            gear = "P",
            locationName = "汉阳区"
        )
        val end = TripCheckpoint(
            vin = vin,
            timestampEpochMs = 3000L,
            mileage = 23320.0, // 行驶 20.0 km
            soc = 85.0,        // 消耗 5% 电量
            isDriving = false,
            gear = "P",
            locationName = "经开区"
        )

        val (record, nextCheckpoint) = TripReconciliationEngine.reconcile(start, end, capacity, price)

        assertNotNull(record)
        assertEquals(20.0, record!!.distanceKm, 0.01)
        assertEquals(90.0, record.startSoc, 0.01)
        assertEquals(85.0, record.endSoc, 0.01)

        // 5% * 67.7 = 3.385 kWh -> 3.4 kWh
        assertEquals(3.4, record.consumedKwh, 0.1)
        // (3.385 / 20.0) * 100 = 16.9 kWh/100km
        assertEquals(16.9, record.avgKwhPer100Km, 0.2)
        // 3.385 * 0.58 = 1.96 元
        assertEquals(1.96, record.estimatedCostYuan, 0.1)
        assertEquals(TripType.DRIVING, record.type)
        assertFalse(record.chargedDuringTrip)
        assertEquals("汉阳区", record.startLocation)
        assertEquals("经开区", record.endLocation)

        // 终点状态被转为新的基线锚点
        assertEquals(end, nextCheckpoint)
    }

    @Test
    fun `driving with precise fractional soc calculates accurate power consumption under one percent`() {
        val start = TripCheckpoint(
            vin = vin,
            timestampEpochMs = 1000L,
            mileage = 23300.0,
            soc = 84.4, // 高精带小数电量 84.4%
            isDriving = false,
            gear = "P",
            locationName = "起点"
        )
        val end = TripCheckpoint(
            vin = vin,
            timestampEpochMs = 2000L,
            mileage = 23304.0, // 行驶 4.0 km
            soc = 83.8,        // 掉电 0.6%（不足 1%）
            isDriving = false,
            gear = "P",
            locationName = "终点"
        )

        val (record, _) = TripReconciliationEngine.reconcile(start, end, capacity, price)

        assertNotNull(record)
        assertEquals(4.0, record!!.distanceKm, 0.01)
        assertEquals(84.4, record.startSoc, 0.01)
        assertEquals(83.8, record.endSoc, 0.01)
        // 0.6% * 67.7 = 0.4062 kWh -> 0.4 kWh
        assertEquals(0.4, record.consumedKwh, 0.05)
        // (0.4 / 4.0) * 100 = 10.0 kWh/100km
        assertEquals(10.0, record.avgKwhPer100Km, 0.5)
        assertTrue("单程预估花费应大于0", record.estimatedCostYuan > 0.0)
    }

    @Test
    fun `stopping at red light in D gear with zero speed does not settle trip and preserves start anchor`() {
        val start = TripCheckpoint(
            vin = vin,
            timestampEpochMs = 1000L,
            mileage = 23300.0,
            soc = 90.0,
            isDriving = false,
            gear = "P",
            locationName = "吾悦华府"
        )
        // 中途等红绿灯：已行驶 5 公里，速度为 0，挂在 D 挡
        val waitingAtRedLight = TripCheckpoint(
            vin = vin,
            timestampEpochMs = 2000L,
            mileage = 23305.0,
            soc = 88.0,
            isDriving = false, // 车速为0时底层isDriving可能被判定为false
            gear = "D",        // 但挡位依然在D挡
            locationName = "路口等红灯"
        )

        val (record, checkpoint) = TripReconciliationEngine.reconcile(start, waitingAtRedLight, capacity, price)

        assertNull("D挡等红绿灯严禁结算生成行程", record)
        assertEquals("起点里程锚点必须保持不变", start.mileage, checkpoint.mileage, 0.01)
        assertEquals("起点电量锚点必须保持不变", start.soc, checkpoint.soc, 0.01)
        assertEquals("起点时间必须保持不变", start.timestampEpochMs, checkpoint.timestampEpochMs)
        assertEquals("起点地名必须保持不变", "吾悦华府", checkpoint.locationName)
        assertTrue("必须维持行车态", checkpoint.isDriving)

        // 最终到达目的地挂入 P 挡
        val finalPark = TripCheckpoint(
            vin = vin,
            timestampEpochMs = 3000L,
            mileage = 23310.0,
            soc = 87.0,
            isDriving = false,
            gear = "P",
            locationName = "公司地下车库"
        )
        val (finalRecord, finalCheckpoint) = TripReconciliationEngine.reconcile(checkpoint, finalPark, capacity, price)

        assertNotNull("挂入P挡后触发结算", finalRecord)
        assertEquals(10.0, finalRecord!!.distanceKm, 0.01)
        assertEquals("吾悦华府", finalRecord.startLocation)
        assertEquals("公司地下车库", finalRecord.endLocation)
        assertEquals(finalPark, finalCheckpoint)
    }

    @Test
    fun `overnight parked vehicle uses unlock departure timestamp as exact start time`() {
        // 昨晚 19:00 停车
        val yesterdayPark = TripCheckpoint(
            vin = vin,
            timestampEpochMs = 1_000_000L,
            mileage = 23300.0,
            soc = 90.0,
            isDriving = false,
            gear = "P",
            locationName = "吾悦华府"
        )
        // 今早 07:35 小组件/App 点击解锁（意图出发）
        val unlockTs = 1_000_000L + 12 * 3600 * 1000L // 12小时后解锁
        // 今早 07:53 到达目的地挂入 P 挡
        val arrival = TripCheckpoint(
            vin = vin,
            timestampEpochMs = unlockTs + 18 * 60 * 1000L, // 解锁后行驶 18 分钟到达
            mileage = 23305.0,
            soc = 89.2,
            isDriving = false,
            gear = "P",
            locationName = "周家岗"
        )

        val (record, _) = TripReconciliationEngine.reconcile(
            previous = yesterdayPark,
            current = arrival,
            batteryCapacityKwh = capacity,
            pricePerKwh = price,
            unlockDepartureEpochMs = unlockTs
        )

        assertNotNull(record)
        assertEquals(5.0, record!!.distanceKm, 0.01)
        assertEquals("起始时间应精准锚定在今早解锁时刻", unlockTs, record.startTimeEpochMs)
        assertEquals("结束时间应为到达时刻", arrival.timestampEpochMs, record.endTimeEpochMs)
        assertEquals("历时应准确为18分钟", 18 * 60 * 1000L, record.endTimeEpochMs - record.startTimeEpochMs)
    }

    @Test
    fun `overnight parked vehicle without unlock falls back to estimated drive time instead of 12 hours`() {
        val yesterdayPark = TripCheckpoint(
            vin = vin,
            timestampEpochMs = 1_000_000L,
            mileage = 23300.0,
            soc = 90.0,
            isDriving = false,
            gear = "P",
            locationName = "吾悦华府"
        )
        val arrivalNextMorning = TripCheckpoint(
            vin = vin,
            timestampEpochMs = 1_000_000L + 12 * 3600 * 1000L, // 隔夜12小时
            mileage = 23305.0, // 仅行驶 5.0 km
            soc = 89.2,
            isDriving = false,
            gear = "P",
            locationName = "周家岗"
        )

        val (record, _) = TripReconciliationEngine.reconcile(
            previous = yesterdayPark,
            current = arrivalNextMorning,
            batteryCapacityKwh = capacity,
            pricePerKwh = price,
            unlockDepartureEpochMs = null
        )

        assertNotNull(record)
        assertEquals(5.0, record!!.distanceKm, 0.01)
        val durationMs = record.endTimeEpochMs - record.startTimeEpochMs
        assertTrue("未记录解锁时，历时应被合理校准，不得跨越隔夜12小时", durationMs < 60 * 60 * 1000L)
    }

    @Test
    fun `overnight commute of 60km without unlock estimates drive time reasonably instead of 12 hours`() {
        val yesterdayPark = TripCheckpoint(
            vin = vin,
            timestampEpochMs = 1_000_000L,
            mileage = 23300.0,
            soc = 90.0,
            isDriving = false,
            gear = "P",
            locationName = "吾悦华府"
        )
        val arrivalNextMorning = TripCheckpoint(
            vin = vin,
            timestampEpochMs = 1_000_000L + 12 * 3600 * 1000L, // 隔夜12小时
            mileage = 23360.0, // 行驶 60.0 km (跨城通勤 > 50km)
            soc = 75.0,
            isDriving = false,
            gear = "P",
            locationName = "异地园区"
        )

        val (record, _) = TripReconciliationEngine.reconcile(
            previous = yesterdayPark,
            current = arrivalNextMorning,
            batteryCapacityKwh = capacity,
            pricePerKwh = price,
            unlockDepartureEpochMs = null
        )

        assertNotNull(record)
        assertEquals(60.0, record!!.distanceKm, 0.01)
        val durationMs = record.endTimeEpochMs - record.startTimeEpochMs
        assertTrue("即便超过50km长途，经历隔夜停放时历时也应被合理反推，不得跨越12小时", durationMs < 3 * 3600 * 1000L)
    }

    @Test
    fun `regenerative braking with slight soc rise under 1_5 percent does not tag charged and records zero consumption`() {
        val summit = TripCheckpoint(
            vin = vin,
            timestampEpochMs = 1000L,
            mileage = 23300.0,
            soc = 80.0,
            isDriving = false,
            gear = "P",
            locationName = "山顶"
        )
        // 盘山长下坡 5 公里，动能回收反充 0.5% 电量
        val downhillEnd = TripCheckpoint(
            vin = vin,
            timestampEpochMs = 2000L,
            mileage = 23305.0,
            soc = 80.5, // 回充 0.5%
            isDriving = false,
            gear = "P",
            locationName = "山脚"
        )

        val (record, _) = TripReconciliationEngine.reconcile(summit, downhillEnd, capacity, price)

        assertNotNull(record)
        assertEquals(5.0, record!!.distanceKm, 0.01)
        assertFalse("长下坡动能回收微幅反充不得误打途中补能标签", record.chargedDuringTrip)
        assertEquals(0.0, record.consumedKwh, 0.01)
        assertEquals(0.0, record.avgKwhPer100Km, 0.01)
    }

    @Test
    fun `short distance under 1_5 km with large energy spike clamps average consumption to 32_0 to prevent visual outlier`() {
        val start = TripCheckpoint(
            vin = vin,
            timestampEpochMs = 1000L,
            mileage = 23300.0,
            soc = 90.0,
            isDriving = false,
            gear = "P"
        )
        // 起步刚开 0.5km，开大空调急加速消耗 0.5% SoC -> 0.34 kWh (直接换算 0.34 / 0.5 * 100 = 68.0 kWh/100km)
        val end = TripCheckpoint(
            vin = vin,
            timestampEpochMs = 2000L,
            mileage = 23300.5,
            soc = 89.5,
            isDriving = false,
            gear = "P"
        )

        val (record, _) = TripReconciliationEngine.reconcile(start, end, capacity, price)

        assertNotNull(record)
        assertEquals(0.5, record!!.distanceKm, 0.01)
        assertTrue("短途能耗被平滑钳位在32.0内，防止数学毛刺", record.avgKwhPer100Km <= 32.0)
    }

    @Test
    fun `short reposition under 0_5 km is ignored and does not generate trip`() {
        val start = TripCheckpoint(
            vin = vin,
            timestampEpochMs = 1000L,
            mileage = 23300.0,
            soc = 90.0,
            isDriving = false,
            gear = "P"
        )
        val shortMove = TripCheckpoint(
            vin = vin,
            timestampEpochMs = 2000L,
            mileage = 23300.2, // 仅位移 200 米挪车
            soc = 90.0,
            isDriving = false,
            gear = "P"
        )

        val (record, nextCheckpoint) = TripReconciliationEngine.reconcile(start, shortMove, capacity, price)

        assertNull("短距挪车不计入单程账单", record)
        assertEquals(start.mileage, nextCheckpoint.mileage, 0.01)
    }

    @Test
    fun `charging during parking avoids negative consumption and tags charged`() {
        val start = TripCheckpoint(
            vin = vin,
            timestampEpochMs = 1000L,
            mileage = 23300.0,
            soc = 50.0,
            isDriving = false,
            gear = "P"
        )
        val chargedArrival = TripCheckpoint(
            vin = vin,
            timestampEpochMs = 5000L,
            mileage = 23330.0, // 行驶 30km
            soc = 80.0,        // 途中有补能，电量从 50% 升至 80%
            isDriving = false,
            gear = "P"
        )

        val (record, _) = TripReconciliationEngine.reconcile(start, chargedArrival, capacity, price)

        assertNotNull(record)
        assertTrue("应打上途中补能标签", record!!.chargedDuringTrip)
        assertEquals(0.0, record.consumedKwh, 0.01)
        assertEquals(0.0, record.avgKwhPer100Km, 0.01)
    }

    @Test
    fun `stationary sentry drain while parked does not pollute driving consumption`() {
        // 1. 昨晚 19:00 停车基准
        val yesterdayPark = TripCheckpoint(
            vin = vin,
            timestampEpochMs = 1_000_000L,
            mileage = 23300.0,
            soc = 90.0,
            isDriving = false,
            gear = "P"
        )
        // 2. 夜间哨兵开启，掉电至 87.0%，里程未变化 (0 km)
        val stationaryDrain = TripCheckpoint(
            vin = vin,
            timestampEpochMs = 1_000_000L + 10 * 3600 * 1000L,
            mileage = 23300.0,
            soc = 87.0,
            isDriving = false,
            gear = "P"
        )
        val (intermediateRecord, calibratedCheckpoint) = TripReconciliationEngine.reconcile(
            previous = yesterdayPark,
            current = stationaryDrain,
            batteryCapacityKwh = capacity,
            pricePerKwh = price
        )
        assertNull("静止未位移不生成行程单", intermediateRecord)
        assertEquals("静止时基准电量应校准为最新真实剩余电量", 87.0, calibratedCheckpoint.soc, 0.01)

        // 3. 今早起步行驶 5km 到达目的地，电量降至 85.0%
        val arrival = TripCheckpoint(
            vin = vin,
            timestampEpochMs = stationaryDrain.timestampEpochMs + 20 * 60 * 1000L,
            mileage = 23305.0,
            soc = 85.0,
            isDriving = false,
            gear = "P"
        )
        val (finalRecord, _) = TripReconciliationEngine.reconcile(
            previous = calibratedCheckpoint,
            current = arrival,
            batteryCapacityKwh = capacity,
            pricePerKwh = price
        )
        assertNotNull(finalRecord)
        assertEquals(5.0, finalRecord!!.distanceKm, 0.01)
        assertEquals("起点电量应为 87.0%", 87.0, finalRecord.startSoc, 0.01)
        assertEquals("终点电量应为 85.0%", 85.0, finalRecord.endSoc, 0.01)
        // 仅计入 2.0% 行驶掉电：2% * 67.7 = 1.354 -> 1.4 kWh
        assertEquals(1.4, finalRecord.consumedKwh, 0.1)
    }

    @Test
    fun `overnight charging while parked updates baseline and next drive calculates accurate power`() {
        // 1. 昨晚 30% 低电量停车
        val yesterdayLowPark = TripCheckpoint(
            vin = vin,
            timestampEpochMs = 1_000_000L,
            mileage = 23300.0,
            soc = 30.0,
            isDriving = false,
            gear = "P"
        )
        // 2. 夜间家充慢充，充至 100.0%，里程未变化
        val fullChargeStationary = TripCheckpoint(
            vin = vin,
            timestampEpochMs = 1_000_000L + 8 * 3600 * 1000L,
            mileage = 23300.0,
            soc = 100.0,
            isDriving = false,
            gear = "P"
        )
        val (_, fullCheckpoint) = TripReconciliationEngine.reconcile(
            previous = yesterdayLowPark,
            current = fullChargeStationary,
            batteryCapacityKwh = capacity,
            pricePerKwh = price
        )
        assertEquals("夜间充满后基准电量自动跃迁为100%", 100.0, fullCheckpoint.soc, 0.01)

        // 3. 次日清晨拔枪行驶 5.0km，到达剩余 98.0%
        val morningArrival = TripCheckpoint(
            vin = vin,
            timestampEpochMs = fullChargeStationary.timestampEpochMs + 18 * 60 * 1000L,
            mileage = 23305.0,
            soc = 98.0,
            isDriving = false,
            gear = "P"
        )
        val (record, _) = TripReconciliationEngine.reconcile(
            previous = fullCheckpoint,
            current = morningArrival,
            batteryCapacityKwh = capacity,
            pricePerKwh = price
        )
        assertNotNull(record)
        assertFalse("充电是在昨夜静止时完成的，行车不应打上途中补能", record!!.chargedDuringTrip)
        assertEquals(100.0, record.startSoc, 0.01)
        assertEquals(98.0, record.endSoc, 0.01)
        // 消耗 2%：2% * 67.7 = 1.354 -> 1.4 kWh
        assertEquals(1.4, record.consumedKwh, 0.1)
    }

    @Test
    fun `resolves model battery capacity correctly`() {
        assertEquals(67.7, TripReconciliationEngine.resolveBatteryCapacityKwh("C16 纯电"), 0.01)
        assertEquals(28.4, TripReconciliationEngine.resolveBatteryCapacityKwh("C16 增程"), 0.01)
        assertEquals(69.9, TripReconciliationEngine.resolveBatteryCapacityKwh("C10 纯电"), 0.01)
        assertEquals(28.4, TripReconciliationEngine.resolveBatteryCapacityKwh("C10 增程"), 0.01)
        assertEquals(78.5, TripReconciliationEngine.resolveBatteryCapacityKwh("C11 纯电"), 0.01)
        assertEquals(43.7, TripReconciliationEngine.resolveBatteryCapacityKwh("C11 增程"), 0.01)
        assertEquals(41.3, TripReconciliationEngine.resolveBatteryCapacityKwh("T03"), 0.01)
    }

    @Test
    fun `trip record JSON serialization round trip preserves precision`() {
        val record = TripRecord(
            id = "test-trip-1",
            vin = vin,
            startTimeEpochMs = 1000L,
            endTimeEpochMs = 2000L,
            startMileage = 23300.0,
            endMileage = 23325.5,
            distanceKm = 25.5,
            startSoc = 90.0,
            endSoc = 83.5,
            consumedKwh = 4.4,
            avgKwhPer100Km = 17.2,
            estimatedCostYuan = 2.55,
            fuelConsumedLiters = null,
            startLocation = "汉阳区",
            endLocation = "经开区",
            type = TripType.DRIVING,
            chargedDuringTrip = false
        )

        val json = record.toJson()
        val parsed = TripRecord.fromJson(json)

        assertNotNull(parsed)
        assertEquals(record.id, parsed!!.id)
        assertEquals(record.distanceKm, parsed.distanceKm, 0.01)
        assertEquals(record.consumedKwh, parsed.consumedKwh, 0.01)
        assertEquals(record.startLocation, parsed.startLocation)
        assertEquals(record.endLocation, parsed.endLocation)
    }

    @Test
    fun `summary computation aggregates total distance and average energy correctly`() {
        val trip1 = TripRecord(
            id = "t1", vin = vin, startTimeEpochMs = 100, endTimeEpochMs = 200,
            startMileage = 100.0, endMileage = 120.0, distanceKm = 20.0,
            startSoc = 90.0, endSoc = 85.0, consumedKwh = 3.0, avgKwhPer100Km = 15.0,
            estimatedCostYuan = 1.74
        )
        val trip2 = TripRecord(
            id = "t2", vin = vin, startTimeEpochMs = 300, endTimeEpochMs = 400,
            startMileage = 120.0, endMileage = 150.0, distanceKm = 30.0,
            startSoc = 85.0, endSoc = 75.0, consumedKwh = 6.0, avgKwhPer100Km = 20.0,
            estimatedCostYuan = 3.48
        )

        val summary = TripRecordStore.computeSummary(listOf(trip1, trip2))
        assertEquals(50.0, summary.totalDistanceKm, 0.01)
        assertEquals(9.0, summary.totalKwh, 0.01)
        // (9.0 / 50.0) * 100 = 18.0
        assertEquals(18.0, summary.avgKwhPer100Km, 0.01)
        assertEquals(5.22, summary.totalCostYuan, 0.01)
        assertEquals(2, summary.tripCount)
    }

    @Test
    fun `resolves model fuel tank capacity correctly`() {
        assertEquals(50.0, VehicleBatteryCapacityTable.resolveFuelTankCapacityLiters("C16 增程"), 0.01)
        assertEquals(50.0, VehicleBatteryCapacityTable.resolveFuelTankCapacityLiters("C10 增程"), 0.01)
        assertEquals(47.0, VehicleBatteryCapacityTable.resolveFuelTankCapacityLiters("C11 增程"), 0.01)
        assertEquals(47.0, VehicleBatteryCapacityTable.resolveFuelTankCapacityLiters("C01 增程"), 0.01)
        assertEquals(0.0, VehicleBatteryCapacityTable.resolveFuelTankCapacityLiters("C16 纯电"), 0.01)
        assertEquals(0.0, VehicleBatteryCapacityTable.resolveFuelTankCapacityLiters("T03"), 0.01)
    }

    @Test
    fun `reev driving with range extender running consumes both fuel and electricity and aggregates cost`() {
        val reevCapacity = 28.4
        val fuelTank = 50.0
        val fuelPrice = 8.35

        val start = TripCheckpoint(
            vin = vin,
            timestampEpochMs = 1000L,
            mileage = 10000.0,
            soc = 80.0,
            isDriving = false,
            gear = "P",
            fuelSoc = 80.0
        )
        val end = TripCheckpoint(
            vin = vin,
            timestampEpochMs = 5000L,
            mileage = 10060.0,
            soc = 78.0,
            isDriving = false,
            gear = "P",
            fuelSoc = 74.0
        )

        val (record, _) = TripReconciliationEngine.reconcile(
            previous = start,
            current = end,
            batteryCapacityKwh = reevCapacity,
            pricePerKwh = price,
            fuelTankCapacityLiters = fuelTank,
            pricePerFuelLiter = fuelPrice
        )

        assertNotNull(record)
        assertEquals(60.0, record!!.distanceKm, 0.01)
        assertEquals(3.0, record.fuelConsumedLiters!!, 0.05)
        assertEquals(0.6, record.consumedKwh, 0.05)
        assertEquals(25.40, record.estimatedCostYuan, 0.1)
    }

    @Test
    fun `reev refueling at gas station while parked updates baseline without negative consumption`() {
        val reevCapacity = 28.4
        val fuelTank = 50.0
        val lowFuelPark = TripCheckpoint(
            vin = vin,
            timestampEpochMs = 1000L,
            mileage = 10000.0,
            soc = 80.0,
            isDriving = false,
            gear = "P",
            fuelSoc = 15.0
        )
        val refueledStationary = TripCheckpoint(
            vin = vin,
            timestampEpochMs = 2000L,
            mileage = 10000.0,
            soc = 80.0,
            isDriving = false,
            gear = "P",
            fuelSoc = 100.0
        )

        val (intermediateRecord, nextCheckpoint) = TripReconciliationEngine.reconcile(
            previous = lowFuelPark,
            current = refueledStationary,
            batteryCapacityKwh = reevCapacity,
            pricePerKwh = price,
            fuelTankCapacityLiters = fuelTank
        )

        assertNull("加油未行驶不生成行程单", intermediateRecord)
        assertEquals("基准燃油百分比应自动校准为满油 100%", 100.0, nextCheckpoint.fuelSoc!!, 0.01)
    }
}
