package com.leapauto.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChargingCenterPillPresentationTest {

    @Test
    fun chargingWithRemainingTimeShowsRemainingTimeAndBoltIcon() {
        val model = ChargingCenterPillPresentation.resolve(
            chargeState = 1,
            chargeGunConnected = true,
            chargeRemainTime = "约1小时20分钟",
            chargeScheduleEnabled = false,
            chargeScheduleStart = null
        )
        assertEquals(ChargingPillType.CHARGING, model.type)
        assertEquals("剩1时20分", model.labelText)
        assertTrue(model.hasBoltIcon)
    }

    @Test
    fun chargingWithoutRemainingTimeShowsChargingAndBoltIcon() {
        val model = ChargingCenterPillPresentation.resolve(
            chargeState = 1,
            chargeGunConnected = true,
            chargeRemainTime = null,
            chargeScheduleEnabled = false,
            chargeScheduleStart = null
        )
        assertEquals(ChargingPillType.CHARGING, model.type)
        assertEquals("充电中", model.labelText)
        assertTrue(model.hasBoltIcon)
    }

    @Test
    fun gunConnectedAndChargedShowsCharged() {
        val model = ChargingCenterPillPresentation.resolve(
            chargeState = 2,
            chargeGunConnected = true,
            chargeRemainTime = "0分钟",
            soc = "100%",
            chargeScheduleEnabled = false,
            chargeScheduleStart = null
        )
        assertEquals(ChargingPillType.CHARGED, model.type)
        assertEquals("已充满", model.labelText)
        assertFalse(model.hasBoltIcon)
    }

    @Test
    fun gunConnectedWithScheduledChargingShowsStartTime() {
        val model = ChargingCenterPillPresentation.resolve(
            chargeState = 0,
            chargeGunConnected = true,
            chargeRemainTime = null,
            chargeScheduleEnabled = true,
            chargeScheduleStart = "23:00"
        )
        assertEquals(ChargingPillType.SCHEDULED_WAITING, model.type)
        assertEquals("23:00开始充电", model.labelText)
        assertFalse(model.hasBoltIcon)
    }

    @Test
    fun gunConnectedWithScheduledChargingWaitingStateShowsStartTime() {
        val model = ChargingCenterPillPresentation.resolve(
            chargeState = 4,
            chargeGunConnected = true,
            chargeRemainTime = null,
            chargeScheduleEnabled = true,
            chargeScheduleStart = "22:30"
        )
        assertEquals(ChargingPillType.SCHEDULED_WAITING, model.type)
        assertEquals("22:30开始充电", model.labelText)
        assertFalse(model.hasBoltIcon)
    }

    @Test
    fun gunConnectedWithScheduledChargingWaitingStateWithoutStartTimeShowsFallback() {
        val model = ChargingCenterPillPresentation.resolve(
            chargeState = 4,
            chargeGunConnected = true,
            chargeRemainTime = null,
            chargeScheduleEnabled = null,
            chargeScheduleStart = null
        )
        assertEquals(ChargingPillType.SCHEDULED_WAITING, model.type)
        assertEquals("预约等待中", model.labelText)
        assertFalse(model.hasBoltIcon)
    }

    @Test
    fun gunConnectedOnlyShowsGunConnected() {
        val model = ChargingCenterPillPresentation.resolve(
            chargeState = 0,
            chargeGunConnected = true,
            chargeRemainTime = null,
            chargeScheduleEnabled = false,
            chargeScheduleStart = null
        )
        assertEquals(ChargingPillType.GUN_CONNECTED, model.type)
        assertEquals("充电枪已插", model.labelText)
        assertFalse(model.hasBoltIcon)
    }

    @Test
    fun gunNotConnectedShowsDefaultChargingCenter() {
        val model = ChargingCenterPillPresentation.resolve(
            chargeState = 0,
            chargeGunConnected = false,
            chargeRemainTime = null,
            chargeScheduleEnabled = false,
            chargeScheduleStart = null
        )
        assertEquals(ChargingPillType.DEFAULT, model.type)
        assertEquals("充电中心", model.labelText)
        assertFalse(model.hasBoltIcon)
    }

    @Test
    fun chargerUnlockedShowsUnlockedPrompt() {
        val model = ChargingCenterPillPresentation.resolve(
            chargeState = 2,
            chargeGunConnected = true,
            chargeRemainTime = null,
            chargeScheduleEnabled = false,
            chargeScheduleStart = null,
            chargerUnlocked = true
        )
        assertEquals(ChargingPillType.GUN_CONNECTED, model.type)
        assertEquals("充电枪已解锁", model.labelText)
        assertFalse(model.hasBoltIcon)
    }

    @Test
    fun healthyLimitReachedShowsChargingCompleted() {
        val model = ChargingCenterPillPresentation.resolve(
            chargeState = 2,
            chargeGunConnected = true,
            chargeRemainTime = null,
            chargeScheduleEnabled = false,
            chargeScheduleStart = null,
            soc = "90%",
            isHealthyChargeEnabled = true,
            healthyChargeLimitSoc = 90
        )
        assertEquals(ChargingPillType.CHARGED, model.type)
        assertEquals("充电完成", model.labelText)
        assertFalse(model.hasBoltIcon)
    }

    @Test
    fun endedOrPausedAtLimitOrUnknownSocShowsGunConnectedOrFull() {
        for (state in listOf(2, 6)) {
            for (soc in listOf(null, "未知", "--", "80%", "90%", "99%")) {
                val model = ChargingCenterPillPresentation.resolve(state, true, "已充满", false, null, soc)
                assertEquals(ChargingPillType.GUN_CONNECTED, model.type)
                assertEquals("充电枪已插", model.labelText)
                assertFalse(model.hasBoltIcon)
            }
            val full = ChargingCenterPillPresentation.resolve(state, false, null, false, null, "100%")
            assertEquals(ChargingPillType.CHARGED, full.type)
            assertEquals("已充满", full.labelText)
        }
    }

    @Test
    fun activeAtFullSocWithZeroOrInvalidTimeStillShowsCharging() {
        for (time in listOf("0分钟", "已充满", "未知", null)) {
            val model = ChargingCenterPillPresentation.resolve(1, true, time, true, "23:00", "100%")
            assertEquals(ChargingPillType.CHARGING, model.type)
            assertEquals("充电中", model.labelText)
            assertTrue(model.hasBoltIcon)
        }
        assertEquals("剩45分", ChargingCenterPillPresentation.resolve(1, true, "45分钟", false, null, "100%").labelText)
    }

    @Test
    fun waitingAndFaultRemainVisibleWithoutConnectionFlagAndIdleDoesNotInferStopped() {
        assertEquals("预约等待中", ChargingCenterPillPresentation.resolve(4, false, "0分钟", false, null, "100%").labelText)
        assertEquals("充电故障", ChargingCenterPillPresentation.resolve(3, true, "0分钟", true, "23:00", "100%").labelText)
        assertEquals("充电中心", ChargingCenterPillPresentation.resolve(0, false, "已充满", false, null, "100%").labelText)
        assertEquals("充电枪已插", ChargingCenterPillPresentation.resolve(0, true, "已充满", false, null, "100%").labelText)
    }
}
