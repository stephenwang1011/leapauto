package com.leapauto.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetRangePresentationTest {
    @Test
    fun `pure electric tone follows soc boundaries`() {
        assertEquals(WidgetPureRangeTone.NORMAL, WidgetPureRangeToneMapper.fromSoc(41))
        assertEquals(WidgetPureRangeTone.WARNING, WidgetPureRangeToneMapper.fromSoc(40))
        assertEquals(WidgetPureRangeTone.WARNING, WidgetPureRangeToneMapper.fromSoc(21))
        assertEquals(WidgetPureRangeTone.CRITICAL, WidgetPureRangeToneMapper.fromSoc(20))
        assertEquals(WidgetPureRangeTone.CRITICAL, WidgetPureRangeToneMapper.fromSoc(0))
    }

    @Test
    fun `range extender exposes independent component progress values`() {
        val presentation = WidgetRangePresentationMapper.fromValues(
            totalRange = "208 km",
            electricRange = "137",
            fuelRange = "71 km",
            electricTotalRange = "196",
            fuelTotalRange = "80",
            powerType = SessionStore.VehiclePowerType.RANGE_EXTENDER
        )
        assertTrue(presentation.rangeExtender)
        assertEquals("137", presentation.electricRange)
        assertEquals("71", presentation.fuelRange)
        assertEquals(69, presentation.electricProgress)
        assertEquals(88, presentation.fuelProgress)
        assertTrue(presentation.electricProgressKnown)
        assertTrue(presentation.fuelProgressKnown)
    }

    @Test
    fun `zero fuel range remains visible with an empty blue bar`() {
        val presentation = WidgetRangePresentationMapper.fromValues(
            totalRange = "206",
            electricRange = "206",
            fuelRange = "0",
            electricTotalRange = "520",
            fuelTotalRange = "80",
            powerType = SessionStore.VehiclePowerType.RANGE_EXTENDER
        )
        assertEquals("0", presentation.fuelRange)
        assertEquals(0, presentation.fuelProgress)
        assertTrue(presentation.fuelProgressKnown)
    }

    @Test
    fun `component bars use their own total range`() {
        val presentation = WidgetRangePresentationMapper.fromValues(
            totalRange = "999",
            electricRange = "120",
            fuelRange = "80",
            electricTotalRange = "240",
            fuelTotalRange = "100",
            powerType = SessionStore.VehiclePowerType.RANGE_EXTENDER
        )

        assertEquals(50, presentation.electricProgress)
        assertEquals(80, presentation.fuelProgress)
    }

    @Test
    fun `missing or zero energy total leaves the affected bar empty`() {
        val presentation = WidgetRangePresentationMapper.fromValues(
            totalRange = "208",
            electricRange = "137",
            fuelRange = "71",
            electricTotalRange = null,
            fuelTotalRange = "0",
            powerType = SessionStore.VehiclePowerType.RANGE_EXTENDER
        )

        assertEquals(0, presentation.electricProgress)
        assertEquals(0, presentation.fuelProgress)
        assertFalse(presentation.electricProgressKnown)
        assertFalse(presentation.fuelProgressKnown)
    }

    @Test
    fun `hybrid electric bar uses precise soc signal when available`() {
        val presentation = WidgetRangePresentationMapper.fromValues(
            totalRange = "169",
            electricRange = "71",
            fuelRange = "98",
            electricTotalRange = "90",
            fuelTotalRange = "116",
            electricSocPercent = 30,
            fuelSocPercent = 12,
            powerType = SessionStore.VehiclePowerType.RANGE_EXTENDER
        )

        assertEquals(30, presentation.electricProgress)
        assertEquals(12, presentation.fuelProgress)
    }

    @Test
    fun `hybrid fuel bar uses confirmed fuel percentage instead of range ratio`() {
        val presentation = WidgetRangePresentationMapper.fromValues(
            totalRange = "169",
            electricRange = "71",
            fuelRange = "98",
            electricTotalRange = "90",
            fuelTotalRange = "116",
            fuelSocPercent = 13,
            powerType = SessionStore.VehiclePowerType.RANGE_EXTENDER
        )

        assertEquals(13, presentation.fuelProgress)
    }

    @Test
    fun `confirmed fuel percentage infers range extender when power type is unset`() {
        val presentation = WidgetRangePresentationMapper.fromValues(
            totalRange = "208",
            electricRange = "137",
            fuelRange = "71",
            powerType = null,
            electricSocPercent = 76,
            fuelSocPercent = 13
        )

        assertTrue(presentation.rangeExtender)
        assertEquals(76, presentation.electricProgress)
        assertEquals(13, presentation.fuelProgress)
    }

    @Test
    fun `pure electric keeps the original single range presentation`() {
        val presentation = WidgetRangePresentationMapper.fromValues(
            totalRange = "291",
            electricRange = "291",
            fuelRange = "896",
            powerType = SessionStore.VehiclePowerType.PURE_ELECTRIC
        )
        assertFalse(presentation.rangeExtender)
        assertEquals("291", presentation.totalRange)
        assertEquals(null, presentation.electricRange)
        assertEquals(null, presentation.fuelRange)
    }

    @Test
    fun `legacy range extender snapshot keeps its available total range`() {
        val presentation = WidgetRangePresentationMapper.fromValues(
            totalRange = "208",
            electricRange = null,
            fuelRange = null,
            powerType = SessionStore.VehiclePowerType.RANGE_EXTENDER
        )

        assertFalse(presentation.rangeExtender)
        assertEquals("208", presentation.totalRange)
        assertEquals(null, presentation.electricRange)
        assertEquals(null, presentation.fuelRange)
    }

    @Test
    fun `widget range label preserves km when source already contains a shorthand unit`() {
        assertEquals("340km", formatWidgetRangeLabel("340k"))
        assertEquals("340km", formatWidgetRangeLabel("340 km"))
        assertEquals("--km", formatWidgetRangeLabel(null))
    }
}
