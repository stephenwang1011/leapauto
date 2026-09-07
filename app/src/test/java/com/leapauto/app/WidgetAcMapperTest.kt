package com.leapauto.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetAcMapperTest {

    @Test
    fun `enabled air conditioner preserves tone and defaults to cooling`() {
        val defaultActive = WidgetAcMapper.presentation(true)
        assertTrue(defaultActive.showEnabledIcon)
        assertEquals(ClimateTemperatureTone.COOLING, defaultActive.tone)

        val coolingActive = WidgetAcMapper.presentation(true, ClimateTemperatureTone.COOLING)
        assertTrue(coolingActive.showEnabledIcon)
        assertEquals(ClimateTemperatureTone.COOLING, coolingActive.tone)

        val heatingActive = WidgetAcMapper.presentation(true, ClimateTemperatureTone.HEATING)
        assertTrue(heatingActive.showEnabledIcon)
        assertEquals(ClimateTemperatureTone.HEATING, heatingActive.tone)

        val ventActive = WidgetAcMapper.presentation(true, ClimateTemperatureTone.VENTILATION)
        assertTrue(ventActive.showEnabledIcon)
        assertEquals(ClimateTemperatureTone.VENTILATION, ventActive.tone)

        val off = WidgetAcMapper.presentation(false, ClimateTemperatureTone.COOLING)
        assertFalse(off.showEnabledIcon)
        assertEquals(ClimateTemperatureTone.DEFAULT, off.tone)
    }

    @Test
    fun `enabled air conditioner uses original active icon and turns off`() {
        val presentation = WidgetAcMapper.presentation(WidgetAcMapper.state(true))

        assertTrue(presentation.showEnabledIcon)
        assertEquals("acOff", presentation.command)
        assertEquals("170", Commands.build(requireNotNull(presentation.command)).cmdid)
        assertEquals("关闭空调", presentation.contentDescription)
    }

    @Test
    fun `disabled air conditioner keeps standard icon and turns on`() {
        val presentation = WidgetAcMapper.presentation(WidgetAcMapper.state(false))

        assertFalse(presentation.showEnabledIcon)
        assertEquals("acOn", presentation.command)
        assertEquals("170", Commands.build(requireNotNull(presentation.command)).cmdid)
        assertEquals("开启空调", presentation.contentDescription)
    }

    @Test
    fun `only protocol numeric zero and one are accepted`() {
        assertEquals(false, WidgetAcMapper.state(0))
        assertEquals(true, WidgetAcMapper.state(1))
    }

    @Test
    fun `enabled and disabled icons share one stable widget slot`() {
        val enabled = WidgetAcMapper.presentation(true)
        val disabled = WidgetAcMapper.presentation(false)

        assertEquals(enabled.showEnabledIcon, !disabled.showEnabledIcon)
        assertEquals("acOff", enabled.command)
        assertEquals("acOn", disabled.command)
    }

    @Test
    fun `only confirmed AC commands update the local widget state`() {
        assertEquals(true, WidgetAcMapper.confirmedStateForCommand("acOn", true))
        assertEquals(false, WidgetAcMapper.confirmedStateForCommand("acOff", true))
        assertNull(WidgetAcMapper.confirmedStateForCommand("acOff", false))
        assertNull(WidgetAcMapper.confirmedStateForCommand("lock", true))
    }

    @Test
    fun `blank control result id leaves AC widget state unchanged`() {
        // ControlService treats a blank msgID as unconfirmed because no result=0 can be queried.
        val stateAfterBlankMessageId =
            WidgetAcMapper.confirmedStateForCommand("acOff", executionConfirmed = false)

        assertNull(stateAfterBlankMessageId)
    }

    @Test
    fun `unknown air conditioner state never sends a control command`() {
        val presentation = WidgetAcMapper.presentation(WidgetAcMapper.state("unknown"))

        assertFalse(presentation.showEnabledIcon)
        assertNull(presentation.command)
        assertEquals("空调状态未知，打开 App 查看", presentation.contentDescription)
    }

    @Test
    fun `non-protocol telemetry values remain unknown and cannot control the air conditioner`() {
        val nonProtocolValues = listOf<Any?>(0.5, 2, -1, "1", "true", "on", null)

        nonProtocolValues.forEach { value ->
            val presentation = WidgetAcMapper.presentation(WidgetAcMapper.state(value))

            assertFalse("value=$value", presentation.showEnabledIcon)
            assertNull("value=$value", presentation.command)
            assertEquals("value=$value", "空调状态未知，打开 App 查看", presentation.contentDescription)
        }
    }
}
