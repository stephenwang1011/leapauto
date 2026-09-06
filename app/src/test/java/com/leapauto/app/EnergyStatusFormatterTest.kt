package com.leapauto.app

import org.junit.Assert.assertEquals
import org.junit.Test

class EnergyStatusFormatterTest {

    @Test
    fun `range mode maps verified raw values`() {
        assertEquals("标准续航", EnergyStatusFormatter.rangeMode("0"))
        assertEquals("动态续航", EnergyStatusFormatter.rangeMode("1"))
        assertEquals("状态未知", EnergyStatusFormatter.rangeMode("9"))
    }
}
