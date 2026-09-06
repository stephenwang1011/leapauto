package com.leapauto.app

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VehicleSignalMapDebugTest {
    @Test
    fun extractsRawSignalMapFromNestedVehicleResponse() {
        val response = JSONObject(
            """{"data":{"result":{"signalMap":{"1182":29,"47":true}}}}"""
        )

        val signalMap = LeapmotorApi.extractSignalMap(response)

        assertEquals(2, signalMap.length())
        assertEquals(29, signalMap.getInt("1182"))
        assertTrue(signalMap.getBoolean("47"))
    }

    @Test
    fun returnsEmptySignalMapWhenResponseIsMissingOrEmpty() {
        assertEquals(0, LeapmotorApi.extractSignalMap(JSONObject("""{"data":{}}""")).length())
        assertEquals(0, LeapmotorApi.extractSignalMap(JSONObject("""{"signalMap":{}}""")).length())
    }

    @Test
    fun extractsLegacyT03NamedSignalObjectWithoutSignalMapWrapper() {
        val response = JSONObject(
            """{"data":{"soc":55,"expectedMileage":228,"longitude":115.554944,"latitude":35.589339,"acSwitch":false}}"""
        )

        val signalMap = LeapmotorApi.extractSignalMap(response)

        assertEquals(5, signalMap.length())
        assertEquals(55, signalMap.getInt("soc"))
        assertEquals(115.554944, signalMap.getDouble("longitude"), 0.000001)
        assertEquals(35.589339, signalMap.getDouble("latitude"), 0.000001)
    }

    @Test
    fun extractsNamedRangeSignalsWithoutAWrapper() {
        val response = JSONObject(
            """{"data":{"soc":76.6,"fuelSoc":12.8,"electricRangeStandard":116}}"""
        )

        val signalMap = LeapmotorApi.extractSignalMap(response)

        assertEquals(76.6, signalMap.getDouble("soc"), 0.0001)
        assertEquals(12.8, signalMap.getDouble("fuelSoc"), 0.0001)
        assertEquals(116, signalMap.getInt("electricRangeStandard"))
    }

    @Test
    fun extractsNamedDynamicHybridRangeSignalsWithoutAWrapper() {
        val response = JSONObject(
            """{"data":{"rangeMode":1,"expectedMileage":116,"fuelRangeDynamic":98,"combinedRangeDynamic":169,"fuelSoc":12.8}}"""
        )

        val signalMap = LeapmotorApi.extractSignalMap(response)

        assertEquals(1, signalMap.getInt("rangeMode"))
        assertEquals(116, signalMap.getInt("expectedMileage"))
        assertEquals(98, signalMap.getInt("fuelRangeDynamic"))
        assertEquals(169, signalMap.getInt("combinedRangeDynamic"))
        assertEquals(12.8, signalMap.getDouble("fuelSoc"), 0.0001)
    }

    @Test
    fun decoderDoesNotPromoteUnverifiedLocationSignals() {
        val decoded = SignalTable.decode(
            JSONObject("""{"2190":32.094512,"2191":112.127561}""")
        )

        assertTrue(!decoded.has("latitude"))
        assertTrue(!decoded.has("longitude"))
    }

    @Test
    fun decoderPromotesOnlyVerifiedLocationSignals() {
        val decoded = SignalTable.decode(
            JSONObject("""{"3725":32.094512,"3724":112.127561}""")
        )

        assertEquals(32.094512, decoded.getDouble("latitude"), 0.000001)
        assertEquals(112.127561, decoded.getDouble("longitude"), 0.000001)
    }

    @Test
    fun decoderUsesSemanticNamesForConfirmedRangeAndFuelPercentageSignals() {
        val decoded = SignalTable.decode(
            JSONObject("""{"3257":116,"3256":90,"3258":206,"3235":12.8}""")
        )

        assertEquals(116, decoded.getInt("electricRangeStandard"))
        assertEquals(90, decoded.getInt("fuelRangeStandard"))
        assertEquals(206, decoded.getInt("combinedRangeStandard"))
        assertEquals(12.8, decoded.getDouble("fuelSoc"), 0.0001)
    }

    @Test
    fun decoderPreservesP2RawLocationContractAndStatusTime() {
        val decoded = SignalTable.decode(
            JSONObject("""{"2":112.127561,"3":32.094512,"sts":1700000000}""")
        )

        assertEquals(112.127561, decoded.getDouble("2"), 0.000001)
        assertEquals(32.094512, decoded.getDouble("3"), 0.000001)
        assertEquals(1700000000, decoded.getInt("sts"))
    }

    @Test
    fun formatsSignalMapAsIndentedJson() {
        val formatted = VehicleSignalMapDebugFormatter.format(JSONObject("""{"1182":29,"47":true}"""))

        assertTrue(formatted.startsWith("{\n"))
        assertTrue(formatted.contains("\n  \"1182\": 29"))
        assertTrue(formatted.contains("\n  \"47\": true"))
        assertTrue(formatted.endsWith("\n}"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsFormattingAnEmptySignalMap() {
        VehicleSignalMapDebugFormatter.format(JSONObject())
    }
}
