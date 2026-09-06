package com.leapauto.app

import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetVehicleAppearanceTest {

    @Test
    fun `widget uses configured model and color only for display appearance`() {
        val appearance = ControlWidget.resolveWidgetAppearance(
            config = SessionStore.VehicleConfig(
                modelYear = "2026",
                powerType = SessionStore.VehiclePowerType.PURE_ELECTRIC,
                model = "C10",
                color = "metal_black"
            ),
            reportedCarType = "C16 纯电"
        )

        assertEquals("C10", appearance.model)
        assertEquals("metal_black", appearance.color.id)
        assertEquals(R.drawable.vehicle_lafa5_liquid_silver, appearance.imageResource)
    }

    @Test
    fun `widget normalizes B05 to shared Lafa5 appearance`() {
        val appearance = ControlWidget.resolveWidgetAppearance(
            config = SessionStore.VehicleConfig(
                model = "B05",
                color = "speed_orange"
            ),
            reportedCarType = "B05 纯电"
        )

        assertEquals("Lafa5", appearance.model)
        assertEquals("speed_orange", appearance.color.id)
        assertEquals(R.drawable.vehicle_lafa5_liquid_silver, appearance.imageResource)
    }

    @Test
    fun `widget falls back to reported model when configuration has not been saved`() {
        val appearance = ControlWidget.resolveWidgetAppearance(
            config = SessionStore.VehicleConfig(),
            reportedCarType = "C16 纯电"
        )

        assertEquals("C16", appearance.model)
        assertEquals("liquid_silver", appearance.color.id)
        assertEquals(R.drawable.vehicle_lafa5_liquid_silver, appearance.imageResource)
    }
}
