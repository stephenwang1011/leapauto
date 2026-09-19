package com.leapauto.app.ui

import com.leapauto.app.bluetooth.BleCalibration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BluetoothCalibrationInputTest {
    @Test
    fun inputRoundTripsDefaultAndEveryProtocolBoundary() {
        for (calibration in listOf(BleCalibration.DEFAULT, BleCalibration(0, 0, 0, 0),
            BleCalibration(255, 65_535, 255, 255), BleCalibration(69, 100, 4, 21))) {
            assertEquals(calibration, BluetoothCalibrationInput.from(calibration).parseOrNull())
        }
        assertEquals("2.00", BluetoothCalibrationInput.from(BleCalibration.DEFAULT).coefficient)
    }

    @Test
    fun decimalInputUsesExactHundredthsWithoutFloatingPointRounding() {
        val base = BluetoothCalibrationInput.from(BleCalibration.DEFAULT)
        for ((text, expected) in mapOf("1" to 100, "1." to 100, "1.2" to 120, "1.23" to 123, "0.01" to 1)) {
            assertEquals(expected, base.copy(coefficient = text).parseOrNull()?.coefficientHundredths)
        }
    }

    @Test
    fun malformedOrOutOfRangeFieldsCannotBeSaved() {
        val base = BluetoothCalibrationInput.from(BleCalibration.DEFAULT)
        val invalid = listOf(base.copy(signal = ""), base.copy(signal = "256"), base.copy(signal = "-1"),
            base.copy(signal = "1.0"), base.copy(unlock = "256"), base.copy(lock = "1000"),
            base.copy(coefficient = "655.36"), base.copy(coefficient = "1.234"), base.copy(coefficient = ".5"),
            base.copy(coefficient = "1e2"), base.copy(coefficient = "1,25"), base.copy(coefficient = "NaN"),
            base.copy(coefficient = " 2.00"), base.copy(coefficient = "-1.00"))
        invalid.forEach { assertNull(it.parseOrNull()) }
    }

    @Test
    fun pendingTakesPrecedenceOverPreviousVehicleApplication() {
        assertEquals("等待车辆应用", bluetoothCalibrationVehicleStatus(applied = true, pending = true))
        assertEquals("等待车辆应用", bluetoothCalibrationVehicleStatus(applied = false, pending = true))
        assertEquals("车辆已应用", bluetoothCalibrationVehicleStatus(applied = true, pending = false))
        assertEquals("车辆尚未确认", bluetoothCalibrationVehicleStatus(applied = false, pending = false))
    }
}
