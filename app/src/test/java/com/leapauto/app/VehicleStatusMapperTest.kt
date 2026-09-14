package com.leapauto.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Test

class VehicleStatusMapperTest {

    @Test
    fun remainingRangeUsesStandardModeField() {
        val status = mapOf(
            "rangeMode" to 0,
            "maxRange" to "630km",
            "expectedMileage" to "364km"
        )

        assertEquals("630", VehicleStatusMapper.remainingRange(status))
    }

    @Test
    fun remainingRangeUsesDynamicModeField() {
        val status = mapOf(
            "rangeMode" to 1,
            "maxRange" to "630km",
            "expectedMileage" to "364km"
        )

        assertEquals("364", VehicleStatusMapper.remainingRange(status))
    }

    @Test
    fun legacyT03UsesExpectedMileageWithoutRangeMode() {
        val values = mapOf<String, Any?>("expectedMileage" to 228)
        assertEquals("228", VehicleStatusMapper.remainingRange(values, "零跑 T03"))
    }

    @Test
    fun legacyC11UsesLiveRemainingRangeWithoutModernRangeSignals() {
        val values = mapOf<String, Any?>("liveRemainingRange" to 79)
        assertEquals("79", VehicleStatusMapper.remainingRange(values, "老款 C11 纯电"))
    }

    @Test
    fun c11DoesNotLetLegacyRangeOverrideModernSignals() {
        val values = mapOf<String, Any?>(
            "liveRemainingRange" to 79,
            "rangeMode" to 1,
            "expectedMileage" to 206
        )
        assertEquals("206", VehicleStatusMapper.remainingRange(values, "新款 C11 纯电"))
    }

    @Test
    fun hybridRangeUsesDynamicFuelElectricAndCombinedGroup() {
        val values = org.json.JSONObject("""{"rangeMode":1,"maxRange":196,"maxFuelRange":80,"3256":80,"3257":196,"3259":71,"3260":137,"3261":208}""")
        assertEquals("71", VehicleStatusMapper.fuelRange(values))
        assertEquals("137", VehicleStatusMapper.electricRange(values))
        assertEquals("208", VehicleStatusMapper.combinedRange(values))
        assertEquals("71", VehicleStatusMapper.fuelRemainingRange(values))
        assertEquals("80", VehicleStatusMapper.fuelTotalRange(values))
        assertEquals("137", VehicleStatusMapper.electricRemainingRange(values))
        assertEquals("196", VehicleStatusMapper.electricTotalRange(values))
        assertEquals("208", VehicleStatusMapper.widgetRange(values, "C10 增程"))
    }

    @Test
    fun hybridRangeUsesStandardFuelElectricAndCombinedGroup() {
        val values = org.json.JSONObject("""{"rangeMode":0,"3256":201,"3257":182,"3258":383,"3259":181,"3260":119,"3261":300}""")
        assertEquals("201", VehicleStatusMapper.fuelRange(values))
        assertEquals("182", VehicleStatusMapper.electricRange(values))
        assertEquals("383", VehicleStatusMapper.combinedRange(values))
        assertEquals("201", VehicleStatusMapper.fuelRemainingRange(values))
        assertEquals("182", VehicleStatusMapper.electricRemainingRange(values))
        assertEquals("383", VehicleStatusMapper.widgetRange(values, "C10 增程"))
    }

    @Test
    fun decodedHybridRangeUsesStandardFuelElectricAndCombinedGroup() {
        // Simulate SignalTable.decode() output where numeric IDs are mapped to named fields.
        val values = org.json.JSONObject(
            """{"rangeMode":0,"fuelRangeStandard":201,"maxRange":182,"combinedRangeStandard":383,"fuelRangeDynamic":181,"expectedMileage":119,"combinedRangeDynamic":300}"""
        )
        assertEquals("201", VehicleStatusMapper.fuelRemainingRange(values))
        assertEquals("182", VehicleStatusMapper.electricRemainingRange(values))
        assertEquals("383", VehicleStatusMapper.widgetRange(values, "C10 增程"))
    }

    @Test
    fun decodedConfirmedStandardRangeSignalsAreRemainingValues() {
        val values = SignalTable.decode(
            org.json.JSONObject("""{"rangeMode":0,"3256":90,"3257":116,"3258":206}""")
        )

        assertEquals("90", VehicleStatusMapper.fuelRange(values))
        assertEquals("116", VehicleStatusMapper.electricRange(values))
        assertEquals("206", VehicleStatusMapper.combinedRange(values))
        assertNull(VehicleStatusMapper.electricTotalRange(values))
        assertNull(VehicleStatusMapper.fuelTotalRange(values))
    }

    @Test
    fun pureElectricVehicleIgnoresIncidentalHybridSignals() {
        // Some pure-electric C10 payloads still contain the hybrid signal group.
        // User-selected power type must keep the widget on the single total range.
        val values = org.json.JSONObject(
            """{"rangeMode":1,"expectedMileage":291,"2188":291,"3260":291,"3257":444,"3259":896,"3256":540,"3261":1187}"""
        )

        assertEquals("291", VehicleStatusMapper.widgetRange(values, "2025 C10 纯电"))
        assertEquals("291", VehicleStatusMapper.remainingRange(values, "2025 C10 纯电"))
    }

    @Test
    fun explicitPureElectricConfigurationUsesElectricRangeOnly() {
        val values = org.json.JSONObject(
            """{"rangeMode":1,"2188":291,"3260":291,"3259":896,"3261":1187}"""
        )

        assertEquals(
            "291",
            VehicleStatusMapper.remainingRange(
                values,
                "2025 C10",
                VehicleStatusMapper.PowerType.PURE_ELECTRIC
            )
        )
        assertEquals(
            "291",
            VehicleStatusMapper.widgetRange(
                values,
                "2025 C10",
                VehicleStatusMapper.PowerType.PURE_ELECTRIC
            )
        )
    }

    @Test
    fun explicitRangeExtenderConfigurationUsesCombinedRange() {
        val values = org.json.JSONObject(
            """{"rangeMode":1,"3260":137,"3259":71,"3261":208}"""
        )

        assertEquals(
            "208",
            VehicleStatusMapper.remainingRange(
                values,
                "2025 C10",
                VehicleStatusMapper.PowerType.RANGE_EXTENDER
            )
        )
        assertEquals(
            "208",
            VehicleStatusMapper.widgetRange(
                values,
                "2025 C10",
                VehicleStatusMapper.PowerType.RANGE_EXTENDER
            )
        )
    }

    @Test
    fun confirmedFuelPercentageInfersCombinedRangeWhenPowerTypeIsUnset() {
        val values = SignalTable.decode(
            org.json.JSONObject("""{"rangeMode":1,"3260":137,"3259":71,"3261":208,"3235":12.8}""")
        )

        assertEquals(
            "208",
            VehicleStatusMapper.remainingRange(values, "C10", null)
        )
    }

    @Test
    fun hybridVehicleKeepsZeroFuelRangeVisibleWhenSignalIsPresent() {
        val values = org.json.JSONObject(
            """{"rangeMode":1,"3260":206,"3259":0,"3261":206}"""
        )

        assertEquals("0", VehicleStatusMapper.fuelRange(values))
        assertEquals("206", VehicleStatusMapper.electricRange(values))
        assertEquals("206", VehicleStatusMapper.combinedRange(values))
        assertEquals("206", VehicleStatusMapper.widgetRange(values, "C10 增程"))
    }

    @Test
    fun missingFuelRangeIsDerivedFromCombinedAndElectricValues() {
        val values = org.json.JSONObject(
            """{"rangeMode":1,"3260":340,"3261":430}"""
        )

        assertEquals("90", VehicleStatusMapper.fuelRange(values))
        assertEquals("340", VehicleStatusMapper.electricRange(values))
    }

    @Test
    fun remainingRangeDoesNotCrossGroupsForMissingOrUnknownMode() {
        val values = mapOf(
            "maxRange" to "630km",
            "expectedMileage" to "364km"
        )

        assertNull(VehicleStatusMapper.remainingRange(values))
        assertNull(VehicleStatusMapper.remainingRange(values + ("rangeMode" to 9)))
    }

    @Test
    fun remainingRangeReturnsNullWhenSelectedFieldIsMissing() {
        assertNull(VehicleStatusMapper.remainingRange(mapOf("rangeMode" to 0)))
        assertNull(VehicleStatusMapper.remainingRange(mapOf("rangeMode" to 1)))
    }

    @Test
    fun `soc is normalized into percentage bounds`() {
        assertEquals(100, VehicleStatusMapper.soc(mapOf("soc" to "101%")))
        assertEquals(0, VehicleStatusMapper.soc(emptyMap<String, Any?>()))
    }

    @Test
    fun `soc rounds decimal percentages upward`() {
        assertEquals(83, VehicleStatusMapper.soc(mapOf("soc" to "82.1%")))
        assertEquals(83, VehicleStatusMapper.soc(mapOf("soc" to "82.7%")))
        assertEquals(83, VehicleStatusMapper.soc(mapOf("soc" to "82.5%")))
        assertEquals(0.825f, VehicleStatusMapper.socFraction(mapOf("soc" to "82.5%")), 0.0001f)
    }

    @Test
    fun `display soc omits decimals and rounds upward`() {
        assertEquals("83%", VehicleStatusMapper.displaySoc("82.1%"))
        assertEquals("83%", VehicleStatusMapper.displaySoc("82.7"))
        assertEquals("82%", VehicleStatusMapper.displaySoc(82))
        assertNull(VehicleStatusMapper.displaySoc("unknown"))
    }

    @Test
    fun `display precise soc formats as integer percentage without decimals`() {
        assertEquals("83%", VehicleStatusMapper.displayPreciseSoc("82.50"))
        assertEquals("83%", VehicleStatusMapper.displayPreciseSoc("82.5%"))
        assertEquals("98%", VehicleStatusMapper.displayPreciseSoc("98.3%"))
        assertEquals("100%", VehicleStatusMapper.displayPreciseSoc("101.2"))
        assertNull(VehicleStatusMapper.displayPreciseSoc("unknown"))
    }

    @Test
    fun `precise soc percent normalizes signal 100003 for widget progress`() {
        assertEquals(30, VehicleStatusMapper.preciseSocPercent("30"))
        assertEquals(75, VehicleStatusMapper.preciseSocPercent("74.6"))
        assertEquals(100, VehicleStatusMapper.preciseSocPercent("101.2"))
        assertNull(VehicleStatusMapper.preciseSocPercent("unknown"))
    }

    @Test
    fun `confirmed electric and fuel percentages prefer precise signal when available`() {
        val values = SignalTable.decode(
            org.json.JSONObject("""{"1204":76.6,"100003":99.9,"3235":12.8}""")
        )

        assertEquals(100, VehicleStatusMapper.electricSocPercent(values))
        assertEquals(13, VehicleStatusMapper.fuelSocPercent(values))
    }

    @Test
    fun `electric percentage falls back to legacy precise signal only when 1204 is absent`() {
        val values = SignalTable.decode(org.json.JSONObject("""{"100003":74.6}"""))

        assertEquals(75, VehicleStatusMapper.electricSocPercent(values))
        assertNull(VehicleStatusMapper.fuelSocPercent(values))
    }
}
