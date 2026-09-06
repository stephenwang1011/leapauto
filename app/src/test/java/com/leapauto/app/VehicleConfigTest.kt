package com.leapauto.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VehicleConfigTest {
    @Test
    fun supportedModelsMatchConfigurationOptions() {
        assertEquals(
            listOf("C01", "B01", "T03", "C11", "C16", "D99", "D19", "B05", "A05", "B10", "C10", "A10", "Lafa5"),
            SUPPORTED_VEHICLE_MODELS
        )
    }

    @Test
    fun oldTwoFieldConstructionRemainsCompatibleAndModelAndColorDefaultBlank() {
        val config = SessionStore.VehicleConfig("2026", SessionStore.VehiclePowerType.PURE_ELECTRIC)
        assertEquals("2026", config.modelYear)
        assertEquals(SessionStore.VehiclePowerType.PURE_ELECTRIC, config.powerType)
        assertTrue(config.model.isBlank())
        assertTrue(config.color.isBlank())
    }

    @Test
    fun configuredDisplayModelOverridesReportedModelAndTrimsWhitespace() {
        assertEquals("C10", VehicleStatusMapper.resolveDisplayModel("  C10  ", "C16"))
        assertEquals("C16", VehicleStatusMapper.resolveDisplayModel(" ", "C16"))
        assertEquals("", VehicleStatusMapper.resolveDisplayModel(null, null))
    }

    @Test
    fun vehicleConfigurationStorageKeysAreIsolatedByVin() {
        assertEquals(
            "vehicle_config_VIN-A_color",
            VehicleConfigStorageKeys.field("VIN-A", "color")
        )
        assertEquals(
            "vehicle_config_VIN-B_color",
            VehicleConfigStorageKeys.field("VIN-B", "color")
        )
        assertTrue(
            VehicleConfigStorageKeys.field("VIN-A", "color") !=
                VehicleConfigStorageKeys.field("VIN-B", "color")
        )
    }
}
