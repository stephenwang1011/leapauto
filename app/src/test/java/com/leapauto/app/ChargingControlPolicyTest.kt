package com.leapauto.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ChargingControlPolicyTest {
    @Test
    fun `only charging actions use the isolated path`() {
        listOf("startCharging", "stopCharging", "unlockCharger").forEach {
            assertTrue(ChargingControlPolicy.supports(it))
        }
        listOf("", "lock", "acOff", "StartCharging").forEach {
            assertFalse(ChargingControlPolicy.supports(it))
        }
        assertThrows(IllegalArgumentException::class.java) {
            ChargingControlPolicy.outcome("lock", ChargingControlQueryOutcome.CONFIRMED, 0)
        }
    }

    @Test
    fun `start requires active charging from a post submission read`() {
        assertEquals(ChargingControlOutcome.SUCCESS, outcome("startCharging", 1))
        listOf(null, -1, 0, 2, 3, 4, 5, 6, 99).forEach {
            assertEquals(ChargingControlOutcome.AWAITING_CONFIRMATION, outcome("startCharging", it))
        }
    }

    @Test
    fun `stop requires explicitly known inactive state and rejects fault or unknown`() {
        listOf(0, 2, 4, 6).forEach {
            assertEquals(ChargingControlOutcome.SUCCESS, outcome("stopCharging", it))
        }
        listOf(null, -1, 1, 3, 5, 99).forEach {
            assertEquals(ChargingControlOutcome.AWAITING_CONFIRMATION, outcome("stopCharging", it))
        }
    }

    @Test
    fun `missing polling id can be confirmed by new telemetry but never by acceptance alone`() {
        assertEquals(ChargingControlOutcome.SUCCESS, outcome("startCharging", 1, ChargingControlQueryOutcome.NO_ID))
        assertEquals(ChargingControlOutcome.SUCCESS, outcome("stopCharging", 6, ChargingControlQueryOutcome.NO_ID))
        listOf("startCharging", "stopCharging", "unlockCharger").forEach {
            assertEquals(ChargingControlOutcome.AWAITING_CONFIRMATION, outcome(it, null, ChargingControlQueryOutcome.NO_ID))
        }
    }

    @Test
    fun `unconfirmed polling cannot be hidden by matching telemetry`() {
        assertEquals(ChargingControlOutcome.AWAITING_CONFIRMATION, outcome("startCharging", 1, ChargingControlQueryOutcome.UNCONFIRMED))
        assertEquals(ChargingControlOutcome.AWAITING_CONFIRMATION, outcome("stopCharging", 6, ChargingControlQueryOutcome.UNCONFIRMED))
    }

    @Test
    fun `charger unlock has no telemetry target and requires verified query success`() {
        listOf(null, 0, 1, 2, 3, 4, 6).forEach { state ->
            assertFalse(ChargingControlPolicy.telemetryMatches("unlockCharger", state))
            assertEquals(ChargingControlOutcome.SUCCESS, outcome("unlockCharger", state))
            assertEquals(ChargingControlOutcome.AWAITING_CONFIRMATION, outcome("unlockCharger", state, ChargingControlQueryOutcome.NO_ID))
            assertEquals(ChargingControlOutcome.AWAITING_CONFIRMATION, outcome("unlockCharger", state, ChargingControlQueryOutcome.UNCONFIRMED))
        }
    }

    @Test
    fun `failures after acceptance stay distinct from submission failure`() {
        val rejected = requireNotNull(ChargingControlPolicy.failureFeedback(false, ChargingControlFailureStage.SUBMISSION))
        assertEquals(ControlFeedbackKind.ERROR, rejected.kind)
        ChargingControlFailureStage.entries.filter { it != ChargingControlFailureStage.SESSION_SAVE }.forEach { stage ->
            val accepted = requireNotNull(ChargingControlPolicy.failureFeedback(true, stage))
            assertEquals(ControlFeedbackKind.WARNING, accepted.kind)
            assertTrue(accepted.message.contains("已发送"))
            assertTrue(accepted.message.contains("待确认"))
        }
        assertTrue(requireNotNull(ChargingControlPolicy.failureFeedback(true, ChargingControlFailureStage.TELEMETRY)).message.contains("车况刷新失败"))
        assertTrue(requireNotNull(ChargingControlPolicy.failureFeedback(true, ChargingControlFailureStage.RESULT_QUERY)).message.contains("结果查询异常"))
    }

    @Test
    fun `session persistence errors do not produce vehicle confirmation feedback`() {
        assertNull(ChargingControlPolicy.failureFeedback(false, ChargingControlFailureStage.SESSION_SAVE))
        assertNull(ChargingControlPolicy.failureFeedback(true, ChargingControlFailureStage.SESSION_SAVE))
    }

    @Test
    fun `charging readback updates energy fields while preserving other optimistic controls`() {
        val current = statusFixture().copy(
            locked = false, acSwitch = true, acSetting = "18", driverSeatHeating = 3,
            healthyChargeEnabled = true, chargeScheduleEnabled = true, chargeScheduleSocLimit = 80
        )
        val refreshed = statusFixture().copy(
            soc = "81%", preciseSoc = "81.2%", mileage = "410 km", electricMileage = "410 km",
            combinedMileage = "610 km", rangeMode = "动态", chargeState = 1, chargeLabel = "充电中",
            chargeGunConnected = true, chargingPower = "7.0 kW", chargeRemainTime = "60分钟", chargeSoc = 81,
            chargeType = "交流充电", batteryVoltage = "400 V", batteryCurrent = "17.5 A", minBatteryTemp = "26℃"
        )
        val merged = ChargingControlPolicy.mergeTelemetry(current, refreshed)
        assertEquals(refreshed.soc, merged.soc)
        assertEquals(refreshed.preciseSoc, merged.preciseSoc)
        assertEquals(refreshed.mileage, merged.mileage)
        assertEquals(refreshed.electricMileage, merged.electricMileage)
        assertEquals(refreshed.combinedMileage, merged.combinedMileage)
        assertEquals(refreshed.rangeMode, merged.rangeMode)
        assertEquals(refreshed.chargeState, merged.chargeState)
        assertEquals(refreshed.chargeLabel, merged.chargeLabel)
        assertTrue(merged.chargeGunConnected)
        assertEquals(refreshed.chargingPower, merged.chargingPower)
        assertEquals(refreshed.chargeRemainTime, merged.chargeRemainTime)
        assertEquals(refreshed.chargeSoc, merged.chargeSoc)
        assertEquals(refreshed.chargeType, merged.chargeType)
        assertEquals(refreshed.batteryVoltage, merged.batteryVoltage)
        assertEquals(refreshed.batteryCurrent, merged.batteryCurrent)
        assertEquals(refreshed.minBatteryTemp, merged.minBatteryTemp)
        // Restoring only the readback fields must reproduce every original non-charging field.
        assertEquals(current, ChargingControlPolicy.mergeTelemetry(merged, current))
    }

    @Test
    fun `missing charging readback clears stale measurements and gun state`() {
        val current = statusFixture().copy(
            chargeGunConnected = true, soc = "80%", preciseSoc = "80.5%", chargeState = 1, chargeSoc = 80,
            chargingPower = "7 kW", batteryCurrent = "17 A", batteryVoltage = "400 V", minBatteryTemp = "25℃"
        )
        val merged = ChargingControlPolicy.mergeTelemetry(current, statusFixture())
        assertFalse(merged.chargeGunConnected)
        assertNull(merged.chargeState)
        assertNull(merged.soc)
        assertNull(merged.chargeSoc)
        assertNull(merged.preciseSoc)
        assertNull(merged.chargingPower)
        assertNull(merged.batteryCurrent)
        assertNull(merged.batteryVoltage)
        assertNull(merged.minBatteryTemp)
    }

    @Test
    fun `late confirmation rejects pre submission reads and expires at bounded deadline`() {
        assertFalse(ChargingControlPolicy.canConfirmFromRefresh(1_000L, 999L, 2_000L))
        assertFalse(ChargingControlPolicy.canConfirmFromRefresh(1_000L, 1_000L, 2_000L))
        assertTrue(ChargingControlPolicy.canConfirmFromRefresh(1_000L, 1_001L, 2_000L))
        assertTrue(ChargingControlPolicy.canConfirmFromRefresh(1_000L, 60_000L, 61_000L))
        assertFalse(ChargingControlPolicy.canConfirmFromRefresh(1_000L, 60_000L, 61_001L))
        assertFalse(ChargingControlPolicy.canConfirmFromRefresh(1_000L, 3_000L, 2_000L))
    }

    @Test
    fun `only confirmed outcome produces success feedback`() {
        listOf("startCharging", "stopCharging", "unlockCharger").forEach { name ->
            assertEquals(ControlFeedbackKind.SUCCESS, ChargingControlPolicy.feedback(name, ChargingControlOutcome.SUCCESS).kind)
            val pending = ChargingControlPolicy.feedback(name, ChargingControlOutcome.AWAITING_CONFIRMATION)
            assertEquals(ControlFeedbackKind.WARNING, pending.kind)
            assertTrue(pending.message.contains("待确认"))
        }
    }

    @Test
    fun `silent telemetry ignores control and schedule fields in signal response`() {
        val current = statusFixture().copy(
            locked = false, acSetting = "18 °C", leftFrontWindowPercent = 15,
            healthyChargeEnabled = true, chargeScheduleEnabled = true, chargeScheduleSocLimit = 80
        )
        val values = org.json.JSONObject().put("chargeState", 1).put("soc", 81)
            .put("batteryVoltage", 400).put("batteryCurrent", 10)
            .put("driverDoorLockStatus", 1).put("leftFrontWindowPercent", 0)
            .put("healthyChargeEnabled", false).put("chargesocSetting", 100)
        val merged = ChargingTelemetry.merge(current, values, "C16")
        assertEquals(1, merged.chargeState)
        assertEquals(81, merged.chargeSoc)
        assertEquals("400 V", merged.batteryVoltage)
        assertEquals(current, ChargingControlPolicy.mergeTelemetry(merged, current))
    }

    @Test
    fun `silent telemetry missing measurements stays unknown while preserving settings`() {
        val current = statusFixture().copy(chargingPower = "7 kW", chargeState = 1, healthyChargeEnabled = true)
        val merged = ChargingTelemetry.merge(current, org.json.JSONObject(), "C16")
        assertNull(merged.chargeState)
        assertNull(merged.chargingPower)
        assertTrue(merged.healthyChargeEnabled == true)
    }

    @Test
    fun `late full refresh retains newer charging fields but updates other fields`() {
        val order = ChargingTelemetryOrder()
        val fullRequest = order.begin(1, 0)
        val silentRequest = order.begin(1, 0)
        val newer = statusFixture().copy(chargeState = 1, chargingPower = "7 kW", soc = "81%", locked = false)
        assertTrue(order.accept(silentRequest, 1, 0))
        val oldFull = statusFixture().copy(chargeState = 0, chargingPower = null, soc = "80%", locked = true)
        val merged = requireNotNull(order.mergeFullRefresh(newer, oldFull, order.accept(fullRequest, 1, 0)))
        assertEquals(1, merged.chargeState)
        assertEquals("7 kW", merged.chargingPower)
        assertEquals("81%", merged.soc)
        assertEquals(true, merged.locked)
        assertEquals(oldFull, ChargingControlPolicy.mergeTelemetry(merged, oldFull))
    }

    @Test
    fun `old revision full refresh preserves control readback and null cannot seed stale charging state`() {
        val order = ChargingTelemetryOrder()
        val old = order.begin(1, 1)
        val refreshed = statusFixture().copy(chargeState = 1, chargeSoc = 80)
        val current = statusFixture().copy(chargeState = 6, chargeSoc = 82)
        val accepted = order.accept(old, 1, 2)
        assertFalse(accepted)
        assertEquals(6, order.mergeFullRefresh(current, refreshed, accepted)?.chargeState)
        assertNull(order.mergeFullRefresh(null, refreshed, accepted))
        val fresh = order.begin(1, 2)
        assertEquals(refreshed, order.mergeFullRefresh(null, refreshed, order.accept(fresh, 1, 2)))
    }

    private fun statusFixture() = VehicleStatus(
        soc = null, preciseSoc = null, mileage = null, totalMileage = null,
        averageEnergyConsumption = null, chargingPower = null, chargeRemainTime = null,
        batteryVoltage = null, batteryCurrent = null, chargeType = null, minBatteryTemp = null,
        healthyChargeEnabled = null, rangeMode = null, speed = null, isDriving = null,
        gearStatus = null, locked = null, acSwitch = null, acSetting = null,
        acCoolingAndHeating = null, indoorTemp = null, acAirVolume = null,
        windshieldDefrost = null, rearWindowHeating = null, tires = emptyList(),
        chargeLabel = "充电状态未知", chargeState = null
    )

    private fun outcome(
        name: String,
        state: Int?,
        query: ChargingControlQueryOutcome = ChargingControlQueryOutcome.CONFIRMED
    ) = ChargingControlPolicy.outcome(name, query, state)
}
