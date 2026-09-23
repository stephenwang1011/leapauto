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

    @Test
    fun `healthy vehicle systems are all collapsed by default`() {
        val status = createBaseStatus()
        val report = VehicleHealthDiagnostics.evaluate(status)

        assertTrue(report.systems.all { !it.defaultExpanded })
    }

    @Test
    fun `abnormal systems are expanded by default while healthy systems remain collapsed`() {
        val status = createBaseStatus().copy(
            tires = listOf(
                TireStatus("左前", "1.6", "26", true),
                TireStatus("右前", "2.4", "26", false),
                TireStatus("左后", "2.5", "26", false),
                TireStatus("右后", "2.5", "26", false)
            )
        )
        val report = VehicleHealthDiagnostics.evaluate(status)

        val chassisSystem = report.systems.first { it.name == "底盘与制动" }
        assertTrue("异常分类应默认展开", chassisSystem.defaultExpanded)

        val otherSystems = report.systems.filter { it.name != "底盘与制动" }
        assertTrue("正常分类应默认收起", otherSystems.all { !it.defaultExpanded })
    }

    @Test
    fun `unit formatting avoids duplicate symbols for voltage, tire pressure and temperature`() {
        val status = createBaseStatus().copy(
            batteryVoltage = "384.5 V",
            tires = listOf(
                TireStatus("左前", "256 kPa", "26", false),
                TireStatus("右前", "256 kPa", "26", false),
                TireStatus("左后", "256 kPa", "26", false),
                TireStatus("右后", "256 kPa", "26", false)
            ),
            minBatteryTemp = "25 °C",
            indoorTemp = "22 °C",
            acSetting = "24 °C",
            acSwitch = true
        )
        val report = VehicleHealthDiagnostics.evaluate(status)

        val powertrain = report.systems.first { it.name == "动力与三电" }
        val voltageItem = powertrain.items.first { it.title == "高压母线电压" }
        assertTrue("母线电压不应包含双重单位VV", voltageItem.detail.contains("384.5V") && !voltageItem.detail.contains("VV"))

        val batteryTempItem = powertrain.items.first { it.title == "动力电池包温控" }
        assertTrue("电池温度不应包含双重摄氏度符号", batteryTempItem.detail.contains("25°C") && !batteryTempItem.detail.contains("°C°C"))

        val chassis = report.systems.first { it.name == "底盘与制动" }
        val tireItem = chassis.items.first { it.title == "四轮胎压监测" }
        assertTrue("胎压不应包含混杂的kPabar单位", !tireItem.detail.contains("kPabar") && tireItem.detail.contains("256 kPa"))

        val climate = report.systems.first { it.name == "环控与电气" }
        val acItem = climate.items.first { it.title == "空调座舱环境" }
        assertTrue("空调温度不应包含双重摄氏度符号", !acItem.detail.contains("°C°C") && acItem.detail.contains("24°C") && acItem.detail.contains("22°C"))
    }

    @Test
    fun `diagnostics comprehensively checks 21 total items across all four vehicle domains`() {
        val status = createBaseStatus()
        val report = VehicleHealthDiagnostics.evaluate(status)

        assertEquals(21, report.systems.sumOf { it.items.size })
        val powertrain = report.systems.first { it.name == "动力与三电" }
        assertEquals(6, powertrain.items.size)

        val chassis = report.systems.first { it.name == "底盘与制动" }
        assertEquals(4, chassis.items.size)

        val body = report.systems.first { it.name == "车身与密闭" }
        assertEquals(5, body.items.size)

        val climate = report.systems.first { it.name == "环控与电气" }
        assertEquals(6, climate.items.size)
    }

    @Test
    fun `open sunshade flags body system and provides sunshade close fix`() {
        val status = createBaseStatus().copy(
            roofOpeningPercent = 80
        )
        val report = VehicleHealthDiagnostics.evaluate(status)

        val sunshadeIssue = report.issues.firstOrNull { it.fixCommand == "sunshadeClose" }
        assertNotNull("遮阳帘未关应提供关闭快捷修复", sunshadeIssue)
        assertEquals("关闭遮阳帘", sunshadeIssue?.fixLabel)
    }

    @Test
    fun `locked vehicle with active climate comfort and fridge provides quick fixes`() {
        val status = createBaseStatus().copy(
            locked = true,
            acSwitch = true,
            windshieldDefrost = true,
            driverSeatVentilation = 2,
            driverSeatHeating = 2,
            passengerSeatVentilation = 1,
            passengerSeatHeating = 1,
            steeringWheelHeating = true,
            rearviewMirrorHeating = true,
            fridgeStatus = FridgeStatus(enabled = true, parkEnable = false)
        )
        val report = VehicleHealthDiagnostics.evaluate(status)

        assertTrue(report.issues.any { it.fixCommand == "acOff" && it.fixLabel == "关闭空调" })
        assertTrue(report.issues.any { it.fixCommand == "driverSeatVentilation_0" && it.fixLabel == "关主驾通风" })
        assertTrue(report.issues.any { it.fixCommand == "driverSeatHeating_0" && it.fixLabel == "关主驾加热" })
        assertTrue(report.issues.any { it.fixCommand == "passengerSeatVentilation_0" && it.fixLabel == "关副驾通风" })
        assertTrue(report.issues.any { it.fixCommand == "passengerSeatHeating_0" && it.fixLabel == "关副驾加热" })
        assertTrue(report.issues.any { it.fixCommand == "steeringWheelHeating_0" && it.fixLabel == "关方向盘加热" })
        assertTrue(report.issues.any { it.fixCommand == "rearviewMirrorHeating_off" && it.fixLabel == "关后视镜加热" })
        assertTrue(report.issues.any { it.fixCommand == "fridgeOff" && it.fixLabel == "关闭冰箱" })
    }
}
