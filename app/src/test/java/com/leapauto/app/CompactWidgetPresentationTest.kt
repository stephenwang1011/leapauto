package com.leapauto.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CompactWidgetPresentationTest {

    @Test
    fun `compact range normalizes units and follows the shared battery tone thresholds`() {
        val normal = CompactWidgetRangePresentationMapper.fromValues("316km", 58)
        val warning = CompactWidgetRangePresentationMapper.fromValues("120k", 40)
        val critical = CompactWidgetRangePresentationMapper.fromValues("48", 20)

        assertEquals("316 km", normal.rangeLabel)
        assertEquals("58", normal.socLabel)
        assertEquals(58, normal.progress)
        assertTrue(normal.progressKnown)
        assertEquals(WidgetPureRangeTone.NORMAL, normal.tone)
        assertEquals("120 km", warning.rangeLabel)
        assertEquals(WidgetPureRangeTone.WARNING, warning.tone)
        assertEquals("48 km", critical.rangeLabel)
        assertEquals(WidgetPureRangeTone.CRITICAL, critical.tone)
    }

    @Test
    fun `compact range keeps missing data visibly unknown`() {
        val presentation = CompactWidgetRangePresentationMapper.fromValues("--", null)

        assertEquals("-- km", presentation.rangeLabel)
        assertEquals("--", presentation.socLabel)
        assertEquals(0, presentation.progress)
        assertFalse(presentation.progressKnown)
        assertEquals(WidgetPureRangeTone.NORMAL, presentation.tone)
    }

    @Test
    fun `compact range extender exposes total electric and fuel ranges`() {
        val presentation = CompactWidgetRangePresentationMapper.fromValues(
            range = "445km",
            soc = 58,
            powerType = SessionStore.VehiclePowerType.RANGE_EXTENDER,
            electricRange = "316km",
            fuelRange = "129km",
            electricSoc = 58,
            fuelSoc = 29
        )

        assertTrue(presentation.rangeExtender)
        assertEquals("445km", presentation.totalRangeLabel)
        assertEquals("316km", presentation.electricRangeLabel)
        assertEquals("129km", presentation.fuelRangeLabel)
        assertEquals("58%", presentation.electricSocLabel)
        assertEquals("29%", presentation.fuelSocLabel)
        assertEquals(58, presentation.electricProgress)
        assertEquals(29, presentation.fuelProgress)
        assertTrue(presentation.electricProgressKnown)
        assertTrue(presentation.fuelProgressKnown)
    }

    @Test
    fun `compact range extender remains visible with missing component telemetry`() {
        val presentation = CompactWidgetRangePresentationMapper.fromValues(
            range = "445",
            soc = 58,
            powerType = SessionStore.VehiclePowerType.RANGE_EXTENDER
        )

        assertTrue(presentation.rangeExtender)
        assertEquals("445km", presentation.totalRangeLabel)
        assertEquals("--km", presentation.electricRangeLabel)
        assertEquals("--km", presentation.fuelRangeLabel)
        assertFalse(presentation.electricProgressKnown)
        assertFalse(presentation.fuelProgressKnown)
    }

    @Test
    fun `electric-only telemetry does not infer a range extender`() {
        val presentation = CompactWidgetRangePresentationMapper.fromValues(
            range = "316",
            soc = 58,
            electricRange = "316"
        )

        assertFalse(presentation.rangeExtender)
    }

    @Test
    fun `compact lock button toggles only from confirmed telemetry`() {
        val locked = CompactWidgetLockPresentationMapper.fromState(true)
        val unlocked = CompactWidgetLockPresentationMapper.fromState(false)
        val unknown = CompactWidgetLockPresentationMapper.fromState(null)

        assertEquals("已锁车", locked.label)
        assertEquals("unlock", locked.command)
        assertEquals("110", Commands.build(requireNotNull(locked.command)).cmdid)
        assertEquals("未锁车", unlocked.label)
        assertEquals("lock", unlocked.command)
        assertEquals("110", Commands.build(requireNotNull(unlocked.command)).cmdid)
        assertEquals("门锁", unknown.label)
        assertNull(unknown.command)
    }
}
