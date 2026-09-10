package com.leapauto.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VehicleHealthDiagnosticsTest {

    private fun createBaseStatus(): VehicleStatus = VehicleStatus(
        soc = "80%",
        preciseSoc = "80.0%",
        fuelSoc = "65%",
        mileage = "428km",
        fuelMileage = "350km",
        electricMileage = "180km",
        combinedMileage = "530km",
        rangeExtender = true,
        totalMileage = "12000",
        averageEnergyConsumption = "14.8",
        chargingPower = null,
        chargeRemainTime = null,
        batteryVoltage = "385",
        batteryCurrent = "0",
        chargeType = null,
        minBatteryTemp = "25",
        batteryPreheatEnabled = false,
        healthyChargeEnabled = true,
        rangeMode = "0",
        speed = "0",
        isDriving = false,
        gearStatus = "P",
        locked = true,
        isShutDown = false,
        acSwitch = false,
        acSetting = "24",
        acCoolingAndHeating = null,
        climateMode = null,
        acOperateMode = null,
        recirculationMode = 0,
        indoorTemp = "24",
        acAirVolume = "0",
        windshieldDefrost = false,
        rearWindowHeating = false,
        sentryMode = true,
        windowStatusAvailable = true,
        openWindows = emptyList(),
        tires = listOf(
            TireStatus("左前", "2.4", "26", false),
            TireStatus("右前", "2.4", "26", false),
            TireStatus("左后", "2.5", "26", false),
            TireStatus("右后", "2.5", "26", false)
        ),
        chargeLabel = "未充电",
        chargeState = 0,
        locationSummary = null,
        trunkState = TrunkState.CLOSED,
        driverDoorOpen = false,
        passengerDoorOpen = false,
        leftRearDoorOpen = false,
        rightRearDoorOpen = false,
        anyDoorOpen = false
    )

    @Test
    fun `null status returns waiting state`() {
        val report = VehicleHealthDiagnostics.evaluate(null)
        assertEquals(0, report.score)
        assertEquals(HealthCheckLevel.WARNING, report.level)
        assertTrue(report.issues.isNotEmpty())
    }

    @Test
    fun `healthy vehicle achieves 100 score and good level across all systems`() {
        val status = createBaseStatus()
        val report = VehicleHealthDiagnostics.evaluate(status)

        assertEquals(100, report.score)
        assertEquals(HealthCheckLevel.GOOD, report.level)
        assertTrue(report.issues.isEmpty())
        assertEquals(4, report.systems.size)
        assertTrue(report.systems.all { it.level == HealthCheckLevel.GOOD })
    }

    @Test
    fun `unlocked vehicle and open window deduct score and provide quick fixes`() {
        val status = createBaseStatus().copy(
            locked = false,
            openWindows = listOf("左前", "右前")
        )
        val report = VehicleHealthDiagnostics.evaluate(status)

        assertEquals(90, report.score)
        assertEquals(HealthCheckLevel.WARNING, report.level)
        assertEquals(2, report.issues.size)

        val lockIssue = report.issues.firstOrNull { it.fixCommand == "lock" }
        assertNotNull(lockIssue)
        assertEquals("一键锁车", lockIssue?.fixLabel)

        val windowIssue = report.issues.firstOrNull { it.fixCommand == "windowClose" }
        assertNotNull(windowIssue)
        assertEquals("一键关窗", windowIssue?.fixLabel)
    }

    @Test
    fun `tire warning deducts score and flags chassis system as warning`() {
        val status = createBaseStatus().copy(
            tires = listOf(
                TireStatus("左前", "1.6", "26", true),
                TireStatus("右前", "2.4", "26", false),
                TireStatus("左后", "2.5", "26", false),
                TireStatus("右后", "2.5", "26", false)
            )
        )
        val report = VehicleHealthDiagnostics.evaluate(status)

        assertEquals(90, report.score)
        val chassisSystem = report.systems.first { it.name == "底盘与制动" }
        assertEquals(HealthCheckLevel.WARNING, chassisSystem.level)
        assertTrue(chassisSystem.statusText.contains("需留意"))
    }

    @Test
    fun `extreme cold battery triggers battery preheat recommendation`() {
        val status = createBaseStatus().copy(
            minBatteryTemp = "-20"
        )
        val report = VehicleHealthDiagnostics.evaluate(status)

        assertEquals(90, report.score)
        val batteryIssue = report.issues.firstOrNull { it.fixCommand == "batteryPreheat" }
        assertNotNull(batteryIssue)
        assertEquals("电池预热", batteryIssue?.fixLabel)
    }

    @Test
    fun `open trunk flags body system and provides trunk close fix`() {
        val status = createBaseStatus().copy(
            trunkState = TrunkState.OPEN
        )
        val report = VehicleHealthDiagnostics.evaluate(status)

        assertEquals(90, report.score)
        val trunkIssue = report.issues.firstOrNull { it.fixCommand == "trunkClose" }
        assertNotNull(trunkIssue)
        assertEquals("关后备箱", trunkIssue?.fixLabel)
    }
}
