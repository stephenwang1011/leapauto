package com.leapauto.app

import com.leapauto.app.SessionStore.VehiclePowerType.PURE_ELECTRIC
import com.leapauto.app.SessionStore.VehiclePowerType.RANGE_EXTENDER
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VehiclePowerTypeResolverTest {
    @Test
    fun `unsaved configuration uses the same vehicle default after login and in background`() {
        val car = Vehicle(vin = "test-c01", carType = "2022 C01", powerType = PURE_ELECTRIC)
        val vehicles = listOf(Vehicle("test-other", "C11 REEV", powerType = RANGE_EXTENDER), car)
        val mainConfig = VehiclePowerTypeResolver.fromVehicleConfig(
            car.vin, null, car.carType, car.powerType, vehicles
        )
        val backgroundConfig = VehiclePowerTypeResolver.fromVehicleConfig(
            car.vin, null, "", null, vehicles
        )
        val status = SignalTable.decode(JSONObject("""{"2188":291,"3235":0}"""))

        assertEquals(PURE_ELECTRIC, mainConfig)
        assertEquals(mainConfig, backgroundConfig)
        listOf(mainConfig, backgroundConfig).forEach { configured ->
            val resolved = VehiclePowerTypeResolver.fromStatus(status, configured, car.carType)
            assertEquals(PURE_ELECTRIC, resolved)
            assertEquals("291", VehicleStatusMapper.widgetRange(status, car.carType, resolved.toStatusPowerType()))
        }
    }

    @Test
    fun `vehicle defaults stay VIN scoped and never override user configuration`() {
        val vehicles = listOf(Vehicle("test-c01", "C01", powerType = PURE_ELECTRIC))
        assertNull(VehiclePowerTypeResolver.fromVehicleConfig("test-other", null, "C01", null, vehicles))
        assertNull(VehiclePowerTypeResolver.fromVehicleConfig("", null, "", null, vehicles))
        assertEquals(
            RANGE_EXTENDER,
            VehiclePowerTypeResolver.fromVehicleConfig("test-c01", RANGE_EXTENDER, "C01", PURE_ELECTRIC, vehicles)
        )
        assertEquals(
            PURE_ELECTRIC,
            VehiclePowerTypeResolver.fromVehicleConfig("test-c01", PURE_ELECTRIC, "C01 REEV", RANGE_EXTENDER, vehicles)
        )
    }

    @Test
    fun `explicit reported marker takes priority over legacy vehicle list defaults`() {
        val vehicles = listOf(Vehicle("test-c01", "C01 REEV", powerType = PURE_ELECTRIC))
        assertEquals(
            RANGE_EXTENDER,
            VehiclePowerTypeResolver.fromVehicleConfig("test-c01", null, "", null, vehicles)
        )
    }

    @Test
    fun `legacy C01 range stays visible in both widgets with zero fuel placeholder`() {
        val status = SignalTable.decode(JSONObject("""{"2188":291,"3235":0,"1204":58}"""))
        val powerType = VehiclePowerTypeResolver.fromStatus(status, PURE_ELECTRIC, "2022 C01")
        val range = VehicleStatusMapper.widgetRange(status, "2022 C01", powerType.toStatusPowerType())
        val mainRange = VehicleStatusMapper.remainingRange(status, "2022 C01", powerType.toStatusPowerType())
        val electricRange = VehicleStatusMapper.electricRemainingRange(status)
        val fuelRange = VehicleStatusMapper.fuelRemainingRange(status)
        val fuelSoc = VehicleStatusMapper.fuelSocPercent(status)
        val wide = WidgetRangePresentationMapper.fromValues(
            range, electricRange, fuelRange, powerType, fuelSocPercent = fuelSoc
        )
        val compact = CompactWidgetRangePresentationMapper.fromValues(
            range, 58, powerType, electricRange, fuelRange, fuelSoc = fuelSoc
        )

        assertEquals(PURE_ELECTRIC, powerType)
        assertEquals("291", mainRange)
        assertEquals(mainRange, range)
        assertNull(electricRange)
        assertFalse(wide.rangeExtender)
        assertEquals("291", wide.totalRange)
        assertFalse(compact.rangeExtender)
        assertEquals("291 km", compact.rangeLabel)
    }

    @Test
    fun `configured power type overrides conflicting model telemetry and old snapshot`() {
        assertEquals(
            PURE_ELECTRIC,
            VehiclePowerTypeResolver.resolve(PURE_ELECTRIC, "C01 REEV", RANGE_EXTENDER, true)
        )
        assertEquals(
            RANGE_EXTENDER,
            VehiclePowerTypeResolver.resolve(RANGE_EXTENDER, "C01 BEV", PURE_ELECTRIC)
        )
    }

    @Test
    fun `explicit model markers override old inferred snapshots`() {
        assertEquals(
            PURE_ELECTRIC,
            VehiclePowerTypeResolver.resolve(null, "C01 BEV", RANGE_EXTENDER, true)
        )
        assertEquals(
            RANGE_EXTENDER,
            VehiclePowerTypeResolver.resolve(null, "C01 REEV", PURE_ELECTRIC)
        )
    }

    @Test
    fun `model markers distinguish REEV from EV without guessing from C01 or year`() {
        listOf("C01 REEV", "C01 reev", "C01 ReEv", "C01 \u589e\u7a0b").forEach {
            assertEquals(RANGE_EXTENDER, VehiclePowerTypeResolver.fromCarType(it))
        }
        listOf("C01 EV", "C01 bev", "C01 \u7eaf\u7535").forEach {
            assertEquals(PURE_ELECTRIC, VehiclePowerTypeResolver.fromCarType(it))
        }
        listOf(null, "", "2022 C01", "C11", "C16").forEach {
            assertNull(VehiclePowerTypeResolver.fromCarType(it))
        }
    }

    @Test
    fun `reported pure electric model uses legacy range without saved configuration`() {
        val status = JSONObject("""{"liveRemainingRange":291,"fuelSoc":0}""")
        val powerType = VehiclePowerTypeResolver.fromStatus(status, null, "C01 BEV")

        assertEquals(PURE_ELECTRIC, powerType)
        assertEquals("291", VehicleStatusMapper.widgetRange(status, "C01 BEV", powerType.toStatusPowerType()))
    }

    @Test
    fun `both configured power types preserve the selected modern range mode`() {
        val status = SignalTable.decode(JSONObject(
            """{"3256":540,"3257":444,"3258":984,"3259":896,"3260":291,"3261":1187,"3235":100}"""
        ))
        listOf(Triple(0, "444", "984"), Triple(1, "291", "1187")).forEach { (mode, electric, combined) ->
            status.put("rangeMode", mode)
            listOf(PURE_ELECTRIC to electric, RANGE_EXTENDER to combined).forEach { (configured, expected) ->
                val resolved = VehiclePowerTypeResolver.fromStatus(status, configured, "C01")
                assertEquals(configured, resolved)
                assertEquals(expected, VehicleStatusMapper.remainingRange(status, "C01", resolved.toStatusPowerType()))
                assertEquals(expected, VehicleStatusMapper.widgetRange(status, "C01", resolved.toStatusPowerType()))
            }
        }
    }

    @Test
    fun `range extender keeps empty fuel data and hybrid layout`() {
        val status = SignalTable.decode(JSONObject(
            """{"3262":1,"3260":206,"3259":0,"3261":206,"3235":0}"""
        ))
        val powerType = VehiclePowerTypeResolver.fromStatus(status, RANGE_EXTENDER, "C01")
        val range = VehicleStatusMapper.widgetRange(status, "C01", powerType.toStatusPowerType())
        val electric = VehicleStatusMapper.electricRemainingRange(status)
        val fuel = VehicleStatusMapper.fuelRemainingRange(status)
        val fuelSoc = VehicleStatusMapper.fuelSocPercent(status)
        val wide = WidgetRangePresentationMapper.fromValues(range, electric, fuel, powerType, fuelSocPercent = fuelSoc)
        val compact = CompactWidgetRangePresentationMapper.fromValues(range, 58, powerType, electric, fuel, fuelSoc = fuelSoc)

        assertEquals("206", range)
        assertTrue(wide.rangeExtender)
        assertEquals("0", wide.fuelRange)
        assertTrue(wide.fuelProgressKnown)
        assertEquals(0, wide.fuelProgress)
        assertTrue(compact.rangeExtender)
        assertEquals("0km", compact.fuelRangeLabel)
        assertEquals("0%", compact.fuelSocLabel)
    }

    @Test
    fun `unknown power type uses existing fuel inference only as a fallback`() {
        assertEquals(RANGE_EXTENDER, VehiclePowerTypeResolver.fromStatus(JSONObject("""{"fuelSoc":0}"""), null, "C01"))
        assertEquals(RANGE_EXTENDER, VehiclePowerTypeResolver.fromStatus(
            JSONObject("""{"fuelRangeDynamic":80,"combinedRangeDynamic":200}"""), null, "C01"
        ))
        assertNull(VehiclePowerTypeResolver.fromStatus(JSONObject("""{"fuelRangeDynamic":80}"""), null, "C01"))
        assertNull(VehiclePowerTypeResolver.fromStatus(JSONObject(), null, "C01"))
        assertEquals(PURE_ELECTRIC, VehiclePowerTypeResolver.resolve(null, "C01", PURE_ELECTRIC, true))
    }

    @Test
    fun `missing range stays unknown instead of inventing a cached or zero value`() {
        val status = JSONObject("""{"fuelSoc":0}""")
        val powerType = VehiclePowerTypeResolver.fromStatus(status, PURE_ELECTRIC, "C01")
        val range = VehicleStatusMapper.widgetRange(status, "C01", powerType.toStatusPowerType())
        assertNull(range)
        assertEquals("-- km", CompactWidgetRangePresentationMapper.fromValues(range, null, powerType).rangeLabel)
        assertNull(null.toStatusPowerType())
    }
}
