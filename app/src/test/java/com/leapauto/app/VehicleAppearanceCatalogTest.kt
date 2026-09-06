package com.leapauto.app

import org.junit.Assert.assertEquals
import org.junit.Test

class VehicleAppearanceCatalogTest {

    @Test
    fun `every supported model resolves to the one bundled image`() {
        SUPPORTED_VEHICLE_MODELS.forEach { model ->
            assertEquals(
                R.drawable.vehicle_lafa5_liquid_silver,
                VehicleAppearanceCatalog.resolveAppearance(model, null).imageResource
            )
        }
    }

    @Test
    fun `b05 remains normalized to lafa5`() {
        val appearance = VehicleAppearanceCatalog.resolveAppearance("B05", "speed_orange")

        assertEquals("Lafa5", appearance.model)
        assertEquals("speed_orange", appearance.color.id)
    }

    @Test
    fun `invalid or blank color falls back to liquid silver`() {
        val appearance = VehicleAppearanceCatalog.resolveAppearance("C10", "unknown_color")

        assertEquals("C10", appearance.model)
        assertEquals("liquid_silver", appearance.color.id)
    }
}
