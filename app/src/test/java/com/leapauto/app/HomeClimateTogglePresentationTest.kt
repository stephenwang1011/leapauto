package com.leapauto.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeClimateTogglePresentationTest {
    @Test
    fun enabledClimateUsesOnIconAndTurnsOff() {
        val presentation = HomeClimateTogglePresentationMapper.from(acSwitch = true, controlBusy = false)

        assertEquals(HomeClimateToggleIcon.ON, presentation.icon)
        assertEquals("关闭空调", presentation.contentDescription)
        assertEquals("acOff", presentation.command)
        assertTrue(presentation.enabled)
    }

    @Test
    fun disabledClimateUsesOffIconAndTurnsOn() {
        val presentation = HomeClimateTogglePresentationMapper.from(acSwitch = false, controlBusy = false)

        assertEquals(HomeClimateToggleIcon.OFF, presentation.icon)
        assertEquals("开启空调", presentation.contentDescription)
        assertEquals("acOn", presentation.command)
        assertTrue(presentation.enabled)
    }

    @Test
    fun unsyncedOrBusyClimateCannotSendACommand() {
        val unsynced = HomeClimateTogglePresentationMapper.from(acSwitch = null, controlBusy = false)
        val busy = HomeClimateTogglePresentationMapper.from(acSwitch = true, controlBusy = true)

        assertFalse(unsynced.enabled)
        assertNull(unsynced.command)
        assertFalse(busy.enabled)
        assertNull(busy.command)
        assertEquals(HomeClimateToggleIcon.ON, busy.icon)
    }
}
