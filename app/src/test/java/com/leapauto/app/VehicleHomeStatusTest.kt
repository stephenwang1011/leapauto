package com.leapauto.app

import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
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
    fun `resolved soc prefers confirmed 1204 over legacy precise signal`() {
        assertEquals("30%", VehicleHomeStatus.resolvedSoc("99%", "30%"))
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
            VehicleHomeStatus.DrivingPresentation("已停车"),
            VehicleHomeStatus.drivingPresentation(null, null, true)
        )
        assertEquals(
            VehicleHomeStatus.DrivingPresentation(null),
            VehicleHomeStatus.drivingPresentation(null, "D挡", false)
        )
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

        assertEquals("状态更新 今天 19:03", VehicleHomeStatus.updatedLabel(today, now, zone))
        assertEquals("状态更新 昨天 23:06", VehicleHomeStatus.updatedLabel(yesterday, now, zone))
        assertEquals("状态更新 8/12 08:09", VehicleHomeStatus.updatedLabel(earlier, now, zone))
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
}
