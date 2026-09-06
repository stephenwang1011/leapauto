package com.leapauto.app

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetSentryMapperTest {

    @Test
    fun `numeric sentry telemetry drives the enabled icon and toggle direction`() {
        val enabled = WidgetSentryMapper.presentation(WidgetSentryMapper.state(1))
        val disabled = WidgetSentryMapper.presentation(WidgetSentryMapper.state(0))

        assertTrue(enabled.showEnabledIcon)
        assertEquals("sentryOff", enabled.command)
        assertEquals("关闭哨兵模式", enabled.contentDescription)
        assertFalse(disabled.showEnabledIcon)
        assertEquals("sentryOn", disabled.command)
        assertEquals("开启哨兵模式", disabled.contentDescription)
    }

    @Test
    fun `boolean and string protocol values are parsed consistently`() {
        assertEquals(true, WidgetSentryMapper.state(true))
        assertEquals(false, WidgetSentryMapper.state(false))
        assertEquals(true, WidgetSentryMapper.state("1"))
        assertEquals(false, WidgetSentryMapper.state("0"))
        assertEquals(true, WidgetSentryMapper.state("true"))
        assertEquals(false, WidgetSentryMapper.state("false"))
    }

    @Test
    fun `unknown sentry telemetry can only request the safe enable direction`() {
        val presentation = WidgetSentryMapper.presentation(WidgetSentryMapper.state("unknown"))

        assertNull(WidgetSentryMapper.state(2))
        assertFalse(presentation.showEnabledIcon)
        assertEquals("sentryOn", presentation.command)
        assertEquals("开启哨兵模式，当前状态待同步", presentation.contentDescription)
    }

    @Test
    fun `json signal parsing accepts decoded numeric telemetry`() {
        assertEquals(true, WidgetSentryMapper.state(JSONObject().put("sentryMode", 1)))
        assertEquals(false, WidgetSentryMapper.state(JSONObject().put("sentryMode", 0)))
        assertNull(WidgetSentryMapper.state(JSONObject()))
    }
}
