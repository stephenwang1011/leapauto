package com.leapauto.app

import org.junit.Assert.assertEquals
import org.junit.Test

class TrunkStateTest {

    @Test
    fun mapsOnlyVerifiedClosedAndOpenValues() {
        assertEquals(TrunkState.CLOSED, TrunkStateMapper.fromSignal(0))
        assertEquals(TrunkState.OPEN, TrunkStateMapper.fromSignal(1))
        assertEquals(TrunkState.CLOSED, TrunkStateMapper.fromSignal(false))
        assertEquals(TrunkState.OPEN, TrunkStateMapper.fromSignal(true))
        assertEquals(TrunkState.CLOSED, TrunkStateMapper.fromSignal("0"))
        assertEquals(TrunkState.OPEN, TrunkStateMapper.fromSignal("1"))
        assertEquals(TrunkState.UNKNOWN, TrunkStateMapper.fromSignal(2))
        assertEquals(TrunkState.UNKNOWN, TrunkStateMapper.fromSignal(1.5))
        assertEquals(TrunkState.UNKNOWN, TrunkStateMapper.fromSignal(Double.NaN))
        assertEquals(TrunkState.UNKNOWN, TrunkStateMapper.fromSignal(null))
    }

    @Test
    fun unknownStateNeverBindsACommand() {
        assertEquals(null, TrunkControlPresentationMapper.fromState(TrunkState.UNKNOWN).command)
        assertEquals("trunkOpen", TrunkControlPresentationMapper.fromState(TrunkState.CLOSED).command)
        assertEquals("trunkClose", TrunkControlPresentationMapper.fromState(TrunkState.OPEN).command)
    }
}
