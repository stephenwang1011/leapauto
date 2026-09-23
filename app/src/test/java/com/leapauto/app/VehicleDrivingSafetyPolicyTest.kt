package com.leapauto.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VehicleDrivingSafetyPolicyTest {

    @Test
    fun `isDrivingGear returns true for D and R gears`() {
        assertTrue(VehicleDrivingSafetyPolicy.isDrivingGear("D"))
        assertTrue(VehicleDrivingSafetyPolicy.isDrivingGear("D挡"))
        assertTrue(VehicleDrivingSafetyPolicy.isDrivingGear("drive"))
        assertTrue(VehicleDrivingSafetyPolicy.isDrivingGear("DRIVE"))
        assertTrue(VehicleDrivingSafetyPolicy.isDrivingGear("前进"))
        assertTrue(VehicleDrivingSafetyPolicy.isDrivingGear("3"))

        assertTrue(VehicleDrivingSafetyPolicy.isDrivingGear("R"))
        assertTrue(VehicleDrivingSafetyPolicy.isDrivingGear("R挡"))
        assertTrue(VehicleDrivingSafetyPolicy.isDrivingGear("reverse"))
        assertTrue(VehicleDrivingSafetyPolicy.isDrivingGear("REVERSE"))
        assertTrue(VehicleDrivingSafetyPolicy.isDrivingGear("倒车"))
        assertTrue(VehicleDrivingSafetyPolicy.isDrivingGear("1"))
    }

    @Test
    fun `isDrivingGear returns false for P and N gears or null`() {
        assertFalse(VehicleDrivingSafetyPolicy.isDrivingGear("P"))
        assertFalse(VehicleDrivingSafetyPolicy.isDrivingGear("P挡"))
        assertFalse(VehicleDrivingSafetyPolicy.isDrivingGear("PARK"))
        assertFalse(VehicleDrivingSafetyPolicy.isDrivingGear("驻车"))
        assertFalse(VehicleDrivingSafetyPolicy.isDrivingGear("N"))
        assertFalse(VehicleDrivingSafetyPolicy.isDrivingGear("N挡"))
        assertFalse(VehicleDrivingSafetyPolicy.isDrivingGear("NEUTRAL"))
        assertFalse(VehicleDrivingSafetyPolicy.isDrivingGear("空挡"))
        assertFalse(VehicleDrivingSafetyPolicy.isDrivingGear(""))
        assertFalse(VehicleDrivingSafetyPolicy.isDrivingGear("   "))
        assertFalse(VehicleDrivingSafetyPolicy.isDrivingGear(null))
    }

    @Test
    fun `driving prohibited hint message matches verbatim`() {
        assertEquals("为了您的安全，请停车后在操作", VehicleDrivingSafetyPolicy.DRIVING_OPERATION_PROHIBITED_HINT)
    }
}
