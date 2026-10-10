package com.leapauto.app

import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VehicleHomeStatusTest {
    @Test
    fun `window warning only appears when an open window is known`() {
        assertEquals(false, VehicleHomeStatus.windowWarningVisible(false, emptyList()))
        assertEquals(false, VehicleHomeStatus.windowWarningVisible(true, emptyList()))
        assertEquals(true, VehicleHomeStatus.windowWarningVisible(true, listOf("左前", "右后")))
        assertEquals("", VehicleHomeStatus.windowSummary(false, emptyList()))
        assertEquals("", VehicleHomeStatus.windowSummary(true, emptyList()))
        assertEquals("车窗未关闭", VehicleHomeStatus.windowSummary(true, listOf("左前", "右后")))
    }

    @Test
    fun `has any window open detects list or percent presence`() {
        assertFalse(VehicleHomeStatus.hasAnyWindowOpen(false, listOf("左前")))
        assertFalse(VehicleHomeStatus.hasAnyWindowOpen(true, emptyList(), listOf(0, 0, 0, 0)))
        assertTrue(VehicleHomeStatus.hasAnyWindowOpen(true, listOf("左前"), listOf(0, 0, 0, 0)))
        assertTrue(VehicleHomeStatus.hasAnyWindowOpen(true, emptyList(), listOf(15, 0, 0, 0)))
    }

    @Test
    fun `resolve window item returns closed vent and open descriptions`() {
        val closed = VehicleHomeStatus.resolveWindowItem("lf", "左前", "主驾", emptyList(), 0)
        assertFalse(closed.isOpen)
        assertEquals("已完全关闭", closed.statusText)

        val vent = VehicleHomeStatus.resolveWindowItem("rf", "右前", "副驾", listOf("右前"), 15)
        assertTrue(vent.isOpen)
        assertEquals("微开通风 15%", vent.statusText)

        val opened = VehicleHomeStatus.resolveWindowItem("lr", "左后", "左后", emptyList(), 50)
        assertTrue(opened.isOpen)
        assertEquals("已开启 50%", opened.statusText)

        val unclosedWithoutPercent = VehicleHomeStatus.resolveWindowItem("rr", "右后", "右后", listOf("右后"), null)
        assertTrue(unclosedWithoutPercent.isOpen)
        assertEquals("车窗未关", unclosedWithoutPercent.statusText)
    }

    @Test
    fun `roof opening summary handles closed vent and opened states`() {
        assertEquals("天窗已关闭", VehicleHomeStatus.roofOpeningSummary(null))
        assertEquals("天窗已关闭", VehicleHomeStatus.roofOpeningSummary(0))
        assertEquals("天窗微开 15%", VehicleHomeStatus.roofOpeningSummary(15))
        assertEquals("天窗开启 60%", VehicleHomeStatus.roofOpeningSummary(60))
    }

    @Test
    fun `soc band follows normalized percentage thresholds`() {
        assertEquals(VehicleHomeStatus.SocBand.NORMAL, VehicleHomeStatus.socBand("40.1%"))
        assertEquals(VehicleHomeStatus.SocBand.WARNING, VehicleHomeStatus.socBand("40%"))
        assertEquals(VehicleHomeStatus.SocBand.WARNING, VehicleHomeStatus.socBand("20.1%"))
        assertEquals(VehicleHomeStatus.SocBand.CRITICAL, VehicleHomeStatus.socBand("20%"))
        assertEquals(VehicleHomeStatus.SocBand.CRITICAL, VehicleHomeStatus.socBand("0%"))
        assertEquals(VehicleHomeStatus.SocBand.NORMAL, VehicleHomeStatus.socBand(null))
        assertEquals(VehicleHomeStatus.SocBand.NORMAL, VehicleHomeStatus.socBand("unknown"))
    }

    @Test
    fun `resolved soc falls back to a valid normalized field`() {
        assertEquals("35%", VehicleHomeStatus.resolvedSoc("not-a-number", "35%"))
        assertEquals("35.5%", VehicleHomeStatus.resolvedSoc(null, "35.5%"))
        assertEquals(null, VehicleHomeStatus.resolvedSoc("unknown", null))
    }

    @Test
    fun `resolved soc prefers precise signal over standard soc`() {
        assertEquals("99%", VehicleHomeStatus.resolvedSoc("99%", "30%"))
    }

    @Test
    fun `lock and sentry text preserve unknown state`() {
        assertEquals("车门已锁", VehicleHomeStatus.lockSummary(true))
        assertEquals("车辆未锁", VehicleHomeStatus.lockSummary(false))
        assertEquals("门锁状态未知", VehicleHomeStatus.lockSummary(null))
        assertEquals("哨兵已开", VehicleHomeStatus.sentrySummary(true))
        assertEquals("哨兵状态未知", VehicleHomeStatus.sentrySummary(null))
    }

    @Test
    fun `indoor temperature band preserves cold comfortable and hot thresholds`() {
        assertEquals(
            VehicleHomeStatus.IndoorTemperatureBand.UNKNOWN,
            VehicleHomeStatus.indoorTemperatureBand(null)
        )
        assertEquals(
            VehicleHomeStatus.IndoorTemperatureBand.UNKNOWN,
            VehicleHomeStatus.indoorTemperatureBand("-- °C")
        )
        assertEquals(
            VehicleHomeStatus.IndoorTemperatureBand.COLD,
            VehicleHomeStatus.indoorTemperatureBand("15.9 °C")
        )
        assertEquals(
            VehicleHomeStatus.IndoorTemperatureBand.COMFORTABLE,
            VehicleHomeStatus.indoorTemperatureBand("16℃")
        )
        assertEquals(
            VehicleHomeStatus.IndoorTemperatureBand.COMFORTABLE,
            VehicleHomeStatus.indoorTemperatureBand("26 °C")
        )
        assertEquals(
            VehicleHomeStatus.IndoorTemperatureBand.HOT,
            VehicleHomeStatus.indoorTemperatureBand("26.1°C")
        )
    }

    @Test
    fun `driving presentation requires positive speed and exposes formatted speed`() {
        assertEquals(
            VehicleHomeStatus.DrivingPresentation("行驶中", "32 km/h"),
            VehicleHomeStatus.drivingPresentation("32 km/h", "P挡", true)
        )
        assertEquals(
            VehicleHomeStatus.DrivingPresentation("行驶中", "7.5 km/h"),
            VehicleHomeStatus.drivingPresentation("7.5", "D挡", false)
        )
        assertEquals(
            VehicleHomeStatus.DrivingPresentation(null),
            VehicleHomeStatus.drivingPresentation("0 km/h", "P挡", true)
        )
        assertEquals(
            VehicleHomeStatus.DrivingPresentation(null),
            VehicleHomeStatus.drivingPresentation(null, null, true)
        )
        assertEquals(
            VehicleHomeStatus.DrivingPresentation(null),
            VehicleHomeStatus.drivingPresentation(null, "D挡", false)
        )
    }

    @Test
    fun `detailed driving state formats D, R, N, P gears per specification`() {
        // D挡前进且有速度
        val dMoving = VehicleHomeStatus.resolveDetailedDrivingState("D挡", "70 km/h")
        assertEquals("D挡 · 70km/h", dMoving?.label)
        assertEquals(true, dMoving?.isMoving)

        // D挡静止无速度
        val dStopped = VehicleHomeStatus.resolveDetailedDrivingState("D挡", "0 km/h")
        assertEquals("D挡 · 0km/h", dStopped?.label)
        assertEquals(false, dStopped?.isMoving)

        val dNullSpeed = VehicleHomeStatus.resolveDetailedDrivingState("D", null)
        assertEquals("D挡 · 0km/h", dNullSpeed?.label)
        assertEquals(false, dNullSpeed?.isMoving)

        // R挡倒车且有速度
        val rMoving = VehicleHomeStatus.resolveDetailedDrivingState("R挡", "4 km/h")
        assertEquals("R挡 · 4km/h", rMoving?.label)
        assertEquals(true, rMoving?.isMoving)

        // R挡静止无速度
        val rStopped = VehicleHomeStatus.resolveDetailedDrivingState("R", "0")
        assertEquals("R挡 · 0km/h", rStopped?.label)
        assertEquals(false, rStopped?.isMoving)

        // N挡空挡（不显示速度）
        val nGear = VehicleHomeStatus.resolveDetailedDrivingState("N挡", "0 km/h")
        assertEquals("N挡", nGear?.label)
        assertEquals(false, nGear?.isMoving)

        // N挡但车辆已下电/已熄火 -> 显示已驻车
        val nGearShutDown = VehicleHomeStatus.resolveDetailedDrivingState("N挡", "0 km/h", isShutDown = true)
        assertEquals("已驻车", nGearShutDown?.label)
        assertEquals(false, nGearShutDown?.isMoving)

        // P挡驻车（显示已驻车，不显示速度）
        val pGear = VehicleHomeStatus.resolveDetailedDrivingState("P挡", "0 km/h")
        assertEquals("已驻车", pGear?.label)
        assertEquals(false, pGear?.isMoving)
    }

    @Test
    fun `vehicle shutdown detection correctly identifies power-off states`() {
        // 闭锁状态判定下电/驻车
        assertTrue(VehicleHomeStatus.isVehicleShutDown(locked = true))

        // BCM ON3 状态: 0=下电, 1=上电
        assertTrue(VehicleHomeStatus.isVehicleShutDown(bcmKeyPositionOn3 = "0"))
        assertTrue(VehicleHomeStatus.isVehicleShutDown(bcmKeyPositionOn3 = 0))
        assertFalse(VehicleHomeStatus.isVehicleShutDown(bcmKeyPositionOn3 = "1"))

        // 整车状态: 0, 1, 3=下电/休眠/驻车, 2=行驶中
        assertTrue(VehicleHomeStatus.isVehicleShutDown(vehicleState = 0))
        assertTrue(VehicleHomeStatus.isVehicleShutDown(vehicleState = 1))
        assertTrue(VehicleHomeStatus.isVehicleShutDown(vehicleState = 3))
        assertFalse(VehicleHomeStatus.isVehicleShutDown(vehicleState = 2))

        // 手刹/电子驻车制动已拉起
        assertTrue(VehicleHomeStatus.isVehicleShutDown(parkingBrakeState = "1"))
    }

    @Test
    fun `power summary prioritizes charging then positive-speed driving`() {
        assertEquals(
            "3.3 kW",
            VehicleHomeStatus.powerSummary(1, "32 km/h", true, "379.2 V", "8.8 A")
        )
        assertEquals(
            "19.0 kW",
            VehicleHomeStatus.powerSummary(0, "32 km/h", true, "379.2 V", "-50 A")
        )
        assertEquals(
            "未充电",
            VehicleHomeStatus.powerSummary(0, "0 km/h", false, "379.2 V", "8.8 A")
        )
        assertEquals(
            "待同步",
            VehicleHomeStatus.powerSummary(1, "0 km/h", false, "--", "8.8 A")
        )
        assertEquals(
            "未充电",
            VehicleHomeStatus.powerSummary(null, null, null, "379.2 V", "8.8 A")
        )
    }

    @Test
    fun `lock buttons highlight only the confirmed matching state`() {
        assertEquals(
            VehicleHomeStatus.LockButtonPresentation(unlockActive = true, lockActive = false),
            VehicleHomeStatus.lockButtonPresentation(false)
        )
        assertEquals(
            VehicleHomeStatus.LockButtonPresentation(unlockActive = false, lockActive = true),
            VehicleHomeStatus.lockButtonPresentation(true)
        )
        assertEquals(
            VehicleHomeStatus.LockButtonPresentation(unlockActive = false, lockActive = false),
            VehicleHomeStatus.lockButtonPresentation(null)
        )
    }

    @Test
    fun `updated label uses today yesterday and calendar date`() {
        val zone = ZoneId.of("Asia/Shanghai")
        val now = LocalDateTime.of(2026, 8, 24, 20, 5).atZone(zone).toInstant().toEpochMilli()
        val today = LocalDateTime.of(2026, 8, 24, 19, 3).atZone(zone).toInstant().toEpochMilli()
        val yesterday = LocalDateTime.of(2026, 8, 23, 23, 6).atZone(zone).toInstant().toEpochMilli()
        val earlier = LocalDateTime.of(2026, 8, 12, 8, 9).atZone(zone).toInstant().toEpochMilli()

        assertEquals("今天19:03更新", VehicleHomeStatus.updatedLabel(today, now, zone))
        assertEquals("19:03更新", VehicleHomeStatus.updatedLabel(today, now, zone, omitTodayPrefix = true))
        assertEquals("昨天23:06更新", VehicleHomeStatus.updatedLabel(yesterday, now, zone))
        assertEquals("昨天23:06更新", VehicleHomeStatus.updatedLabel(yesterday, now, zone, omitTodayPrefix = true))
        assertEquals("8/12 08:09更新", VehicleHomeStatus.updatedLabel(earlier, now, zone))
    }

    @Test
    fun `is status fresh verifies within thirty minutes threshold`() {
        val now = 100_000_000L
        assertTrue(VehicleHomeStatus.isStatusFresh(now - 10 * 60 * 1000L, nowEpochMs = now))
        assertTrue(VehicleHomeStatus.isStatusFresh(now - 30 * 60 * 1000L, nowEpochMs = now))
        assertFalse(VehicleHomeStatus.isStatusFresh(now - 31 * 60 * 1000L, nowEpochMs = now))
        assertFalse(VehicleHomeStatus.isStatusFresh(0L, nowEpochMs = now))
        assertFalse(VehicleHomeStatus.isStatusFresh(-1L, nowEpochMs = now))
    }

    @Test
    fun `vehicle status defaults all door open states to false`() {
        val status = VehicleStatus(
            soc = "80%",
            preciseSoc = "80.0%",
            mileage = "400km",
            totalMileage = "12000km",
            averageEnergyConsumption = "15.0",
            chargingPower = null,
            chargeRemainTime = null,
            batteryVoltage = null,
            batteryCurrent = null,
            chargeType = null,
            minBatteryTemp = null,
            healthyChargeEnabled = null,
            rangeMode = "0",
            speed = null,
            isDriving = false,
            gearStatus = "P",
            locked = true,
            acSwitch = false,
            acSetting = "24",
            acCoolingAndHeating = null,
            indoorTemp = "22",
            acAirVolume = "2",
            windshieldDefrost = false,
            rearWindowHeating = false,
            tires = emptyList(),
            chargeLabel = "未充电",
            chargeState = 0
        )
        assertEquals(false, status.driverDoorOpen)
        assertEquals(false, status.passengerDoorOpen)
        assertEquals(false, status.leftRearDoorOpen)
        assertEquals(false, status.rightRearDoorOpen)
        assertEquals(false, status.anyDoorOpen)
        assertEquals(TrunkState.UNKNOWN, status.trunkState)
    }

    @Test
    fun `lock button presentation accurately reports locked and unlocked states`() {
        val lockedPresentation = VehicleHomeStatus.lockButtonPresentation(true)
        assertEquals(false, lockedPresentation.unlockActive)
        assertEquals(true, lockedPresentation.lockActive)

        val unlockedPresentation = VehicleHomeStatus.lockButtonPresentation(false)
        assertEquals(true, unlockedPresentation.unlockActive)
        assertEquals(false, unlockedPresentation.lockActive)

        val nullPresentation = VehicleHomeStatus.lockButtonPresentation(null)
        assertEquals(false, nullPresentation.unlockActive)
        assertEquals(false, nullPresentation.lockActive)
    }

    @Test
    fun `main activity implements 15s lock anti-bounce protection and stepped polling`() {
        val projectDir = projectDirectory()
        val mainActivity = java.io.File(projectDir, "app/src/main/java/com/leapauto/app/MainActivity.kt").readText()

        // 15s lock protection check
        assertTrue(mainActivity.contains("val lockProtected = nowMs - lastLockActionEpochMs < 15_000L"))
        assertTrue(mainActivity.contains("optimisticLockState"))
        assertTrue(mainActivity.contains("scheduleLockStatusRefreshes"))

        // Stepped polling checkpoints: 1200ms, 1500ms, 2000ms, 2500ms
        assertTrue(mainActivity.contains("listOf(1_200L, 1_500L, 2_000L, 2_500L)"))
        assertTrue(mainActivity.contains("800L"))
    }

    private fun projectDirectory(): java.io.File {
        val workingDirectory = requireNotNull(System.getProperty("user.dir"))
        return generateSequence(java.io.File(workingDirectory)) { it.parentFile }
            .first { java.io.File(it, "app").isDirectory }
    }
}
