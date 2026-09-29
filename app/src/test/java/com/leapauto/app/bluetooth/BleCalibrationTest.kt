package com.leapauto.app.bluetooth

import java.util.Locale
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class BleCalibrationTest {
    @Test
    fun `defaults preserve original authentication text and little endian bytes`() {
        assertEquals("56;2.00;08;16", BleCalibration.DEFAULT.toProtocolText())
        assertArrayEquals(byteArrayOf(56, 0xC8.toByte(), 0, 8, 16), BleCalibration.DEFAULT.encoded())
        assertEquals(BleCalibration(), BleCalibration.parseOrNull("56;2.00;08;16"))
    }

    @Test
    fun `c16 default calibration matches leap3 verified protocol format`() {
        assertEquals("69;1.00;04;21", BleCalibration.C16_DEFAULT.toProtocolText())
        assertArrayEquals(byteArrayOf(69, 100, 0, 4, 21), BleCalibration.C16_DEFAULT.encoded())
        assertEquals(BleCalibration.C16_DEFAULT, BleCalibration.defaultForModel("C16"))
        assertEquals(BleCalibration.C16_DEFAULT, BleCalibration.defaultForModel("零跑C16"))
        assertEquals(BleCalibration.C16_DEFAULT, BleCalibration.defaultForModel("C10"))
        assertEquals(BleCalibration.DEFAULT, BleCalibration.defaultForModel("T03"))
        assertEquals(BleCalibration.DEFAULT, BleCalibration.defaultForModel("C11"))
        assertEquals(BleCalibration.DEFAULT, BleCalibration.defaultForModel(null))
    }

    @Test
    fun `protocol widths reject truncation and include the full unsigned boundaries`() {
        val zero = BleCalibration(0, 0, 0, 0)
        val maximum = BleCalibration(255, 65_535, 255, 255)
        assertEquals("0;0.00;00;00", zero.toProtocolText())
        assertArrayEquals(ByteArray(5), zero.encoded())
        assertEquals("255;655.35;255;255", maximum.toProtocolText())
        assertArrayEquals(ByteArray(5) { 0xFF.toByte() }, maximum.encoded())
        listOf(-1, 256, Int.MIN_VALUE, Int.MAX_VALUE).forEach { value ->
            assertThrows(IllegalArgumentException::class.java) { BleCalibration(distanceCalibration = value) }
            assertThrows(IllegalArgumentException::class.java) { BleCalibration(unlockCalibration = value) }
            assertThrows(IllegalArgumentException::class.java) { BleCalibration(lockCalibration = value) }
        }
        listOf(-1, 65_536, Int.MIN_VALUE, Int.MAX_VALUE).forEach { value ->
            assertThrows(IllegalArgumentException::class.java) { BleCalibration(coefficientHundredths = value) }
        }
    }

    @Test
    fun `hundredths encode exactly without floating point or locale dependence`() {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.GERMANY)
            for (coefficient in listOf(0, 1, 9, 10, 99, 100, 101, 175, 255, 256, 999, 1_000, 65_535)) {
                val calibration = BleCalibration(coefficientHundredths = coefficient)
                assertEquals(calibration, BleCalibration.parseOrNull(calibration.toProtocolText()))
                val bytes = calibration.encoded()
                assertEquals(coefficient, (bytes[1].toInt() and 255) or ((bytes[2].toInt() and 255) shl 8))
            }
            assertEquals("56;1.75;08;16", BleCalibration(coefficientHundredths = 175).toProtocolText())
        } finally {
            Locale.setDefault(previous)
        }
    }

    @Test
    fun `parser rejects malformed overflow fractional and unbounded input`() {
        listOf(
            "", "56;2;08;16", "56;2.0;08;16", "56;2.000;08;16", "56;2,00;08;16",
            "56;2e0;08;16", "56;+2.00;08;16", "-1;2.00;08;16", "256;2.00;08;16",
            "56;655.36;08;16", "56;2.00;256;16", "56;2.00;08;256", "56;2.00;08;16;",
            " 56;2.00;08;16", "56;2.00;08;16\n", "56;2.00;08;16\u0000", "56;2.00;08",
            "56;2.00;08;16;1;0", "5\u0666;2.00;08;16", "9".repeat(10_000)
        ).forEach { assertNull(it.take(32), BleCalibration.parseOrNull(it)) }
    }

    @Test
    fun `encoded buffers cannot mutate shared defaults or another caller`() {
        val first = BleCalibration.DEFAULT.encoded()
        first.fill(0)
        assertEquals("56;2.00;08;16", BleCalibration.DEFAULT.toProtocolText())
        assertArrayEquals(byteArrayOf(56, 0xC8.toByte(), 0, 8, 16), BleCalibration.DEFAULT.encoded())
    }

    @Test
    fun `verified c16 far calibration matches captured protocol 69 1 00 07 21`() {
        val far = BleCalibration(69, 100, 7, 21)
        assertEquals("69;1.00;07;21", far.toProtocolText())
        assertArrayEquals(byteArrayOf(69, 100, 0, 7, 21), far.encoded())
        assertEquals(far, BleCalibration.parseOrNull("69;1.00;07;21"))
    }
}
