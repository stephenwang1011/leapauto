package com.leapauto.app

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class TirePressureMappingTest {
    @Test
    fun decoderMapsFourTirePressureSignalsToVehiclePositions() {
        val decoded = SignalTable.decode(
            JSONObject(
                """{"2646":241,"2653":242,"2660":243,"2667":244}"""
            )
        )

        assertEquals(241, decoded.getInt("leftFrontTirePressure"))
        assertEquals(242, decoded.getInt("rightFrontTirePressure"))
        assertEquals(243, decoded.getInt("leftRearTirePressure"))
        assertEquals(244, decoded.getInt("rightRearTirePressure"))
    }

    @Test
    fun decoderKeepsTireWarningSignalBindingsUnchanged() {
        val decoded = SignalTable.decode(
            JSONObject(
                """{"2641":1,"2648":2,"2655":3,"2662":4}"""
            )
        )

        assertEquals(1, decoded.getInt("leftFrontTirePressureState"))
        assertEquals(2, decoded.getInt("rightFrontTirePressureState"))
        assertEquals(3, decoded.getInt("rightRearTirePressureState"))
        assertEquals(4, decoded.getInt("leftRearTirePressureState"))
    }
}
