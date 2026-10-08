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
        assertEquals("剩1小时20分", model.labelText)
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
            chargeRemainTime = "已充满",
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
}
