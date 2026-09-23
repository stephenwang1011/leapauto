package com.leapauto.app.trip

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

class TripStoreTest {

    private lateinit var tempDir: File
    private lateinit var tripStore: TripStore
    private val testVin = "TEST_VIN_1234567890"

    @Before
    fun setUp() {
        tempDir = File(System.getProperty("java.io.tmpdir"), "trip_test_" + System.currentTimeMillis())
        tempDir.mkdirs()
        tripStore = TripStore(baseDir = tempDir)
        TripStore.resetActiveTripForTesting()
    }

    @After
    fun tearDown() {
        TripStore.resetActiveTripForTesting()
        tempDir.deleteRecursively()
    }

    @Test
    fun `driving gear starts new active trip when moving`() {
        val now = 100_000L
        val trip = tripStore.processTelemetry(
            vin = testVin,
            totalMileageStr = "1200.0 km",
            socStr = "85.0%",
            speedStr = "45 km/h",
            gearStatus = "D",
            isDriving = true,
            isShutDown = false,
            currentAddress = "科技园A座",
            nowEpochMs = now
        )

        // 刚刚启动行程，尚未结束结算，返回 null
        assertNull(trip)
        val active = TripStore.getActiveTrip()
        assertNotNull(active)
        assertEquals(testVin, active?.vin)
        assertEquals(1200.0, active?.startMileageKm ?: 0.0, 0.001)
        assertEquals(85.0, active?.startSocPercent ?: 0.0, 0.001)
        assertEquals("科技园A座", active?.startAddress)
        assertEquals(45.0, active?.maxSpeedKmh ?: 0.0, 0.001)
    }

    @Test
    fun `ongoing driving updates mileage and max speed`() {
        val t1 = 100_000L
        tripStore.processTelemetry(
            vin = testVin,
            totalMileageStr = "1200.0 km",
            socStr = "85.0%",
            speedStr = "30 km/h",
            gearStatus = "D",
            isDriving = true,
            isShutDown = false,
            currentAddress = "出发点",
            nowEpochMs = t1
        )

        val t2 = t1 + 60_000L // 1分钟后车速提高到 80，里程增加 1km
        tripStore.processTelemetry(
            vin = testVin,
            totalMileageStr = "1201.0 km",
            socStr = "84.5%",
            speedStr = "80 km/h",
            gearStatus = "D",
            isDriving = true,
            isShutDown = false,
            currentAddress = "途中",
            nowEpochMs = t2
        )

        val active = TripStore.getActiveTrip()
        assertNotNull(active)
        assertEquals(1201.0, active?.currentMileageKm ?: 0.0, 0.001)
        assertEquals(80.0, active?.maxSpeedKmh ?: 0.0, 0.001)
        assertEquals(84.5, active?.currentSocPercent ?: 0.0, 0.001)
    }

    @Test
    fun `shutdown completes and persists valid trip record`() {
        val t1 = 100_000L
        // 出发
        tripStore.processTelemetry(
            vin = testVin,
            totalMileageStr = "1200.0 km",
            socStr = "85.0%",
            speedStr = "40 km/h",
            gearStatus = "D",
            isDriving = true,
            isShutDown = false,
            currentAddress = "吾悦华府",
            nowEpochMs = t1
        )

        val t2 = t1 + 1800_000L // 30分钟后行驶了 18.5 km，到达并熄火
        val completed = tripStore.processTelemetry(
            vin = testVin,
            totalMileageStr = "1218.5 km",
            socStr = "81.0%",
            speedStr = "0 km/h",
            gearStatus = "P",
            isDriving = false,
            isShutDown = true,
            currentAddress = "软件园二期",
            nowEpochMs = t2
        )

        assertNotNull(completed)
        assertEquals(18.5, completed?.distanceKm ?: 0.0, 0.001)
        assertEquals(4.0, completed?.socDeltaPercent ?: 0.0, 0.001)
        assertEquals(1800L, completed?.durationSeconds)
        assertEquals("吾悦华府", completed?.startAddress)
        assertEquals("软件园二期", completed?.endAddress)
        assertEquals(37.0, completed?.avgSpeedKmh ?: 0.0, 0.1)
        assertNotNull(completed?.energyConsumptionKwhPer100Km)

        // 验证持久化存储
        val saved = tripStore.getTrips(testVin)
        assertEquals(1, saved.size)
        assertEquals(completed?.id, saved[0].id)
        assertEquals("30分钟", saved[0].formattedDuration)
    }

    @Test
    fun `filters out trivial moving under minimum distance and duration threshold`() {
        val t1 = 100_000L
        tripStore.processTelemetry(
            vin = testVin,
            totalMileageStr = "1200.0 km",
            socStr = "85.0%",
            speedStr = "5 km/h",
            gearStatus = "D",
            isDriving = true,
            isShutDown = false,
            nowEpochMs = t1
        )

        // 仅过了 10 秒，位移仅 0.05 km 原地挂挡误触即熄火
        val t2 = t1 + 10_000L
        val trip = tripStore.processTelemetry(
            vin = testVin,
            totalMileageStr = "1200.05 km",
            socStr = "85.0%",
            speedStr = "0 km/h",
            gearStatus = "P",
            isDriving = false,
            isShutDown = true,
            nowEpochMs = t2
        )

        assertNull(trip)
        assertTrue(tripStore.getTrips(testVin).isEmpty())
    }

    @Test
    fun `switching vin closes old trip and clears properly`() {
        val now = 100_000L
        tripStore.processTelemetry(
            vin = testVin,
            totalMileageStr = "1000.0 km",
            socStr = "90.0%",
            speedStr = "35 km/h",
            gearStatus = "D",
            isDriving = true,
            isShutDown = false,
            nowEpochMs = now
        )

        val otherVin = "ANOTHER_VIN_9999"
        val closed = tripStore.processTelemetry(
            vin = otherVin,
            totalMileageStr = "1005.0 km",
            socStr = "88.0%",
            speedStr = "0 km/h",
            gearStatus = "P",
            isDriving = false,
            isShutDown = true,
            nowEpochMs = now + 600_000L
        )

        assertNotNull(closed)
        assertEquals(testVin, closed?.vin)

        // 清空测试
        tripStore.clearTrips(testVin)
        assertTrue(tripStore.getTrips(testVin).isEmpty())
    }

    @Test
    fun `clearing trips permanently deletes file and removes legacy mock data`() {
        val trip = TripRecord(
            id = "mock_test_1",
            vin = testVin,
            startTimeEpochMs = 100_000L,
            endTimeEpochMs = 101_800L,
            startMileageKm = 1000.0,
            endMileageKm = 1018.5,
            distanceKm = 18.5,
            durationSeconds = 1800L,
            startSocPercent = 85.0,
            endSocPercent = 81.0,
            socDeltaPercent = 4.0,
            avgSpeedKmh = 37.0,
            startAddress = "软件园二期",
            endAddress = "吾悦华府 A区"
        )
        tripStore.saveTrip(trip)
        assertEquals(1, tripStore.getTrips(testVin).size)

        // 测试自动清理历史演练 mock 数据
        tripStore.removeLegacyMockTripsIfPresent(testVin)
        assertTrue(tripStore.getTrips(testVin).isEmpty())
    }

    @Test
    fun `disabled recorder ignores telemetry`() {
        tripStore.setTripRecordEnabled(false)
        assertFalse(tripStore.isTripRecordEnabled())

        val trip = tripStore.processTelemetry(
            vin = testVin,
            totalMileageStr = "1000.0 km",
            socStr = "90.0%",
            speedStr = "50 km/h",
            gearStatus = "D",
            isDriving = true,
            isShutDown = false
        )
        assertNull(trip)
        assertNull(TripStore.getActiveTrip())
    }

    @Test
    fun `groupByDate groups trips accurately with daily summaries`() {
        val now = 1758280000000L // 模拟基准时间
        val dayMillis = 86_400_000L

        val trip1 = TripRecord(
            id = "1",
            vin = testVin,
            startTimeEpochMs = now,
            endTimeEpochMs = now + 1800_000L,
            startMileageKm = 1000.0,
            endMileageKm = 1018.5,
            distanceKm = 18.5,
            durationSeconds = 1800L,
            startSocPercent = 85.0,
            endSocPercent = 81.0,
            socDeltaPercent = 4.0,
            avgSpeedKmh = 37.0,
            maxSpeedKmh = 75.0,
            startAddress = "家",
            endAddress = "公司",
            energyConsumptionKwhPer100Km = 14.0
        )

        val trip2 = TripRecord(
            id = "2",
            vin = testVin,
            startTimeEpochMs = now + 3600_000L,
            endTimeEpochMs = now + 4800_000L,
            startMileageKm = 1018.5,
            endMileageKm = 1028.5,
            distanceKm = 10.0,
            durationSeconds = 1200L,
            startSocPercent = 81.0,
            endSocPercent = 78.5,
            socDeltaPercent = 2.5,
            avgSpeedKmh = 30.0,
            maxSpeedKmh = 65.0,
            startAddress = "公司",
            endAddress = "商场",
            energyConsumptionKwhPer100Km = 16.0
        )

        val yesterdayTrip = TripRecord(
            id = "3",
            vin = testVin,
            startTimeEpochMs = now - dayMillis,
            endTimeEpochMs = now - dayMillis + 2400_000L,
            startMileageKm = 975.0,
            endMileageKm = 1000.0,
            distanceKm = 25.0,
            durationSeconds = 2400L,
            startSocPercent = 90.0,
            endSocPercent = 85.0,
            socDeltaPercent = 5.0,
            avgSpeedKmh = 37.5,
            maxSpeedKmh = 80.0,
            startAddress = "机场",
            endAddress = "家",
            energyConsumptionKwhPer100Km = 13.0
        )

        val groups = TripGroupHelper.groupByDate(listOf(trip1, trip2, yesterdayTrip))
        assertEquals(2, groups.size)

        // 第一组为最新日期：包含 trip1 和 trip2 两趟
        val firstDay = groups[0]
        assertEquals(2, firstDay.trips.size)
        assertEquals(28.5, firstDay.totalDistanceKm, 0.001)
        assertEquals(6.5, firstDay.totalSocDeltaPercent, 0.001)
        assertEquals(15.0, firstDay.avgEnergyConsumption ?: 0.0, 0.001)
        assertEquals(34.2, firstDay.avgSpeedKmh, 0.1)

        // 第二组为前一天：包含 1 趟
        val secondDay = groups[1]
        assertEquals(1, secondDay.trips.size)
        assertEquals(25.0, secondDay.totalDistanceKm, 0.001)
        assertEquals(13.0, secondDay.avgEnergyConsumption ?: 0.0, 0.001)

        // 空列表测试
        assertTrue(TripGroupHelper.groupByDate(emptyList()).isEmpty())
    }

    @Test
    fun `saving trips caps records strictly to maximum 30 days rolling over oldest day`() {
        val baseTime = 1758280000000L
        val dayMillis = 86_400_000L

        // 模拟连续 35 天的行程保存
        for (i in 0 until 35) {
            val trip = TripRecord(
                id = "day_$i",
                vin = testVin,
                startTimeEpochMs = baseTime + i * dayMillis,
                endTimeEpochMs = baseTime + i * dayMillis + 1800_000L,
                startMileageKm = 1000.0 + i * 20.0,
                endMileageKm = 1020.0 + i * 20.0,
                distanceKm = 20.0,
                durationSeconds = 1800L,
                startSocPercent = 85.0,
                endSocPercent = 80.0,
                socDeltaPercent = 5.0,
                avgSpeedKmh = 40.0,
                maxSpeedKmh = 80.0,
                startAddress = "起点",
                endAddress = "终点",
                energyConsumptionKwhPer100Km = 13.5
            )
            tripStore.saveTrip(trip)
        }

        val allSaved = tripStore.getTrips(testVin)
        val distinctDays = allSaved.map { it.dateGroupKey }.distinct()

        // 严格断言最多只保留最新的 30 天
        assertEquals(30, distinctDays.size)

        // 最早的第 0~4 天（最久远）应该已被淘汰覆盖
        val oldestKeptDay = allSaved.last()
        assertEquals(baseTime + 5 * dayMillis, oldestKeptDay.startTimeEpochMs)
    }

    @Test
    fun `bluetooth connect trigger initializes active trip even when parked in P gear`() {
        val t1 = 100_000L
        val trip = tripStore.processTelemetry(
            vin = testVin,
            totalMileageStr = "1200.0 km",
            socStr = "90.0%",
            speedStr = "0 km/h",
            gearStatus = "P",
            isDriving = false,
            isShutDown = false,
            currentAddress = "小区地下车库",
            nowEpochMs = t1,
            isBluetoothConnectTrigger = true
        )

        // 连上蓝牙时处于静止，应成功锁定起点
        assertNull(trip)
        val active = TripStore.getActiveTrip()
        assertNotNull(active)
        assertEquals(1200.0, active?.startMileageKm ?: 0.0, 0.001)
        assertEquals(90.0, active?.startSocPercent ?: 0.0, 0.001)
        assertEquals("小区地下车库", active?.startAddress)

        // 20分钟后到达目的地熄火，行驶 8.5km
        val t2 = t1 + 1200_000L
        val completed = tripStore.processTelemetry(
            vin = testVin,
            totalMileageStr = "1208.5 km",
            socStr = "88.0%",
            speedStr = "0 km/h",
            gearStatus = "P",
            isDriving = false,
            isShutDown = true,
            currentAddress = "办公大楼",
            nowEpochMs = t2
        )

        assertNotNull(completed)
        assertEquals(8.5, completed?.distanceKm ?: 0.0, 0.001)
        assertEquals(2.0, completed?.socDeltaPercent ?: 0.0, 0.001)
        assertEquals("小区地下车库", completed?.startAddress)
        assertEquals("办公大楼", completed?.endAddress)
    }

    @Test
    fun `active trip snapshot survives memory wipe via disk persistence`() {
        val t1 = 100_000L
        // 步骤1：出发时创建活动快照（落盘）
        tripStore.processTelemetry(
            vin = testVin,
            totalMileageStr = "2000.0 km",
            socStr = "80.0%",
            speedStr = "0 km/h",
            gearStatus = "P",
            isDriving = false,
            isShutDown = false,
            currentAddress = "起点",
            nowEpochMs = t1,
            isBluetoothConnectTrigger = true
        )

        // 步骤2：模拟应用后台被系统杀死，静态内存被彻底回收重置
        TripStore.resetActiveTripForTesting()
        assertNull(TripStore.getActiveTrip())

        // 步骤3：在全新实例中到站熄火结算（应能自动从磁盘 active_trip_${vin}.json 恢复起点）
        val newStore = TripStore(baseDir = tempDir)
        val t2 = t1 + 900_000L
        val completed = newStore.processTelemetry(
            vin = testVin,
            totalMileageStr = "2006.2 km",
            socStr = "78.0%",
            speedStr = "0 km/h",
            gearStatus = "P",
            isDriving = false,
            isShutDown = true,
            currentAddress = "终点",
            nowEpochMs = t2
        )

        assertNotNull(completed)
        assertEquals(6.2, completed?.distanceKm ?: 0.0, 0.001)
        assertEquals("起点", completed?.startAddress)
        assertEquals("终点", completed?.endAddress)

        // 结算后活动行程文件应已自动清理
        assertNull(newStore.loadActiveTrip(testVin, t2))
    }

    @Test
    fun `composite threshold correctly identifies valid short trip and discards stationary idling`() {
        val t1 = 100_000L
        // 测试场景A：开动超过 0.2km（即使时间短），判定为有效行程
        tripStore.processTelemetry(
            vin = testVin,
            totalMileageStr = "1000.0 km",
            socStr = "80.0%",
            speedStr = "30 km/h",
            gearStatus = "D",
            isDriving = true,
            isShutDown = false,
            nowEpochMs = t1
        )
        val tripA = tripStore.processTelemetry(
            vin = testVin,
            totalMileageStr = "1000.25 km",
            socStr = "79.9%",
            speedStr = "0 km/h",
            gearStatus = "P",
            isDriving = false,
            isShutDown = true,
            nowEpochMs = t1 + 50_000L
        )
        assertNotNull(tripA)
        assertEquals(0.3, tripA?.distanceKm ?: 0.0, 0.05)

        // 清理后测试场景B：原地长时间开空调怠速（位移 0km，未超速），判定为无效行程丢弃
        tripStore.clearTrips(testVin)
        TripStore.resetActiveTripForTesting()

        tripStore.processTelemetry(
            vin = testVin,
            totalMileageStr = "1000.25 km",
            socStr = "79.9%",
            speedStr = "0 km/h",
            gearStatus = "P",
            isDriving = false,
            isShutDown = false,
            nowEpochMs = t1 + 100_000L,
            isBluetoothConnectTrigger = true
        )
        // 在车内坐了 10 分钟没有开动
        val tripB = tripStore.processTelemetry(
            vin = testVin,
            totalMileageStr = "1000.25 km",
            socStr = "79.0%",
            speedStr = "0 km/h",
            gearStatus = "P",
            isDriving = false,
            isShutDown = true,
            nowEpochMs = t1 + 700_000L
        )
        assertNull(tripB)
        assertTrue(tripStore.getTrips(testVin).isEmpty())
    }

    @Test
    fun `ActiveTripSnapshot json serialization preserves all fields`() {
        val snapshot = ActiveTripSnapshot(
            vin = "VIN_SERIALIZE_TEST",
            startTimeEpochMs = 123456789L,
            startMileageKm = 15000.5,
            startSocPercent = 88.5,
            startAddress = "火车站",
            lastActiveTimeEpochMs = 123459999L,
            currentMileageKm = 15010.5,
            currentSocPercent = 86.0,
            currentAddress = "市区",
            maxSpeedKmh = 68.5
        )

        val json = snapshot.toJson()
        val restored = ActiveTripSnapshot.fromJson(json)

        assertEquals(snapshot.vin, restored.vin)
        assertEquals(snapshot.startTimeEpochMs, restored.startTimeEpochMs)
        assertEquals(snapshot.startMileageKm, restored.startMileageKm, 0.001)
        assertEquals(snapshot.startSocPercent, restored.startSocPercent, 0.001)
        assertEquals(snapshot.startAddress, restored.startAddress)
        assertEquals(snapshot.lastActiveTimeEpochMs, restored.lastActiveTimeEpochMs)
        assertEquals(snapshot.currentMileageKm, restored.currentMileageKm, 0.001)
        assertEquals(snapshot.currentSocPercent, restored.currentSocPercent, 0.001)
        assertEquals(snapshot.currentAddress, restored.currentAddress)
        assertEquals(snapshot.maxSpeedKmh, restored.maxSpeedKmh, 0.001)
    }

    @Test
    fun `trip record setting card and conditional entrance in energy card code contracts verified`() {
        val workingDirectory = requireNotNull(System.getProperty("user.dir"))
        val projectDir = generateSequence(File(workingDirectory)) { it.parentFile }
            .first { File(it, "app").isDirectory }
        val screenSource = File(projectDir, "app/src/main/java/com/leapauto/app/ui/LeapAutoScreen.kt").readText()

        assertTrue(screenSource.contains("TripRecordSettingCard("))
        assertTrue(screenSource.contains("开启自驾行程记录功能"))
        assertTrue(screenSource.contains("本功能为个人开发者实验性功能"))
        assertTrue(screenSource.contains("同意并开启"))
        assertTrue(screenSource.contains("if (tripRecordEnabled && pagerState.currentPage == EnergyHomePage.SUMMARY.ordinal)"))
    }

    @Test
    fun `underground garage start address backfills on emergence and falls back on ending`() {
        val t0 = 100_000L
        // 1. 地库出发无 GPS，传入空地址
        tripStore.processTelemetry(
            vin = testVin,
            totalMileageStr = "1000.0 km",
            socStr = "90.0%",
            speedStr = "15 km/h",
            gearStatus = "D",
            isDriving = true,
            isShutDown = false,
            currentAddress = "",
            nowEpochMs = t0
        )
        val active = TripStore.getActiveTrip()
        assertNotNull(active)
        assertEquals("", active?.startAddress)

        // 2. 出库后行驶中首次拿到地面 GPS 地标 "高新六路"
        val t1 = t0 + 60_000L
        tripStore.processTelemetry(
            vin = testVin,
            totalMileageStr = "1002.0 km",
            socStr = "89.5%",
            speedStr = "45 km/h",
            gearStatus = "D",
            isDriving = true,
            isShutDown = false,
            currentAddress = "高新六路",
            nowEpochMs = t1
        )
        val activeAfterEmergence = TripStore.getActiveTrip()
        assertEquals("高新六路", activeAfterEmergence?.startAddress)
        assertEquals("高新六路", activeAfterEmergence?.currentAddress)

        // 3. 继续行驶到达目的地下地库前拿到最后地面地标 "万达广场"
        val t2 = t1 + 300_000L
        tripStore.processTelemetry(
            vin = testVin,
            totalMileageStr = "1015.0 km",
            socStr = "85.0%",
            speedStr = "35 km/h",
            gearStatus = "D",
            isDriving = true,
            isShutDown = false,
            currentAddress = "万达广场",
            nowEpochMs = t2
        )

        // 4. 下到目的地地库停车熄火（GPS 信号丢失，传入空地址）
        val t3 = t2 + 60_000L
        val completed = tripStore.processTelemetry(
            vin = testVin,
            totalMileageStr = "1016.0 km",
            socStr = "84.5%",
            speedStr = "0 km/h",
            gearStatus = "P",
            isDriving = false,
            isShutDown = true,
            currentAddress = "",
            nowEpochMs = t3
        )

        assertNotNull(completed)
        assertEquals("高新六路", completed?.startAddress)
        assertEquals("万达广场", completed?.endAddress)
    }
}
