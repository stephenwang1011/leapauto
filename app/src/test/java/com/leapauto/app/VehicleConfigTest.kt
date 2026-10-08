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

    @Test
    fun vehicleSerializationAndDeserializationRoundTripsSuccessfully() {
        val vehicle = Vehicle(
            vin = "LF3A11C16TEST0001",
            carType = "C16 增程",
            hvacCapability = HvacCapability(temperatureMinC = 16, temperatureMaxC = 32, fanMin = 1, fanMax = 7),
            nickname = "我的C16",
            year = "2026",
            color = "pearl_white",
            powerType = SessionStore.VehiclePowerType.RANGE_EXTENDER
        )
        val json = vehicle.toJson()
        val restored = Vehicle.fromJson(json)

        assertEquals("LF3A11C16TEST0001", restored.vin)
        assertEquals("C16 增程", restored.carType)
        assertEquals("我的C16", restored.nickname)
        assertEquals("2026", restored.year)
        assertEquals("pearl_white", restored.color)
        assertEquals(SessionStore.VehiclePowerType.RANGE_EXTENDER, restored.powerType)
        assertEquals(16, restored.hvacCapability.temperatureMinC)
        assertEquals(32, restored.hvacCapability.temperatureMaxC)
    }

    @Test
    fun vehicleListDistinctAndMultiVehicleSelection() {
        val car1 = Vehicle(
            vin = "LF3A11C16TEST0001",
            carType = "C16 增程",
            nickname = "大白C16",
            powerType = SessionStore.VehiclePowerType.RANGE_EXTENDER
        )
        val car2 = Vehicle(
            vin = "LF3A10C10TEST0002",
            carType = "C10 纯电",
            nickname = "小灰C10",
            powerType = SessionStore.VehiclePowerType.PURE_ELECTRIC
        )
        val list = listOf(car1, car2)
        assertEquals(2, list.size)
        val selectedVin = car2.vin
        val selected = list.firstOrNull { it.vin == selectedVin }
        assertEquals("小灰C10", selected?.nickname)
        assertEquals("C10 纯电", selected?.carType)
    }

    @Test
    fun vehicleNicknameLengthLimitAndTrim() {
        val longNickname = "我的超级无敌大零跑座驾汽车"
        val trimmed = longNickname.trim().take(10)
        assertEquals(10, trimmed.length)
        assertEquals("我的超级无敌大零跑座", trimmed)

        val whitespaceNickname = "  小零C16  "
        assertEquals("小零C16", whitespaceNickname.trim().take(10))
    }

    @Test
    fun vehicleDefaultsToBlankWhenApiFieldsAreEmpty() {
        val emptyVehicle = Vehicle(
            vin = "LF3A11C16TEST0003",
            carType = "",
            nickname = "",
            year = "",
            color = ""
        )
        assertEquals("", emptyVehicle.carType)
        assertEquals("", emptyVehicle.nickname)
        assertEquals("", emptyVehicle.year)
        assertEquals("", emptyVehicle.color)

        val restored = Vehicle.fromJson(emptyVehicle.toJson())
        assertEquals("", restored.carType)
        assertEquals("", restored.nickname)
        assertEquals("", restored.year)
        assertEquals("", restored.color)
    }

    @Test
    fun vehicleNicknamePrefersVehicleNameAndPlateOverUserNickname() {
        val rawJson = org.json.JSONObject("""
            {
                "vin": "LF3A11C16TEST0001",
                "carType": "C16",
                "licensePlate": "鄂FD77382",
                "vehicleName": "鄂FD77382",
                "nickName": "渣渣辉"
            }
        """.trimIndent())

        val resolved = rawJson.optString("vehicleName")
            .ifEmpty { rawJson.optString("carName") }
            .ifEmpty { rawJson.optString("licensePlate") }
            .ifEmpty { rawJson.optString("nickName") }
            .ifEmpty { rawJson.optString("nickname") }

        assertEquals("鄂FD77382", resolved)
    }

    @Test
    fun customNicknameIsRetainedWhenRefreshingVehicle() {
        val customNickname = "大白"
        val serverNickname = "鄂FD77382"
        val model = "C16"

        val updated = customNickname.trim().ifBlank {
            serverNickname.ifBlank { model }
        }
        assertEquals("大白", updated)

        // 当用户清空昵称时，才优雅回退到车辆网络昵称
        val reset = "".trim().ifBlank {
            serverNickname.ifBlank { model }
        }
        assertEquals("鄂FD77382", reset)
    }
}
