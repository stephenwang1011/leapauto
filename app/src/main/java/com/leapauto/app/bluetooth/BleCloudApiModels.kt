package com.leapauto.app.bluetooth

import com.leapauto.app.Session
import org.json.JSONObject
import java.util.Locale

data class BleVehicleMetadata(
    val address: String,
    val controllerVersion: String,
    val fetchedAtMillis: Long
) {
    init {
        require(normalizeAddress(address) == address) { "Invalid Bluetooth vehicle address" }
        require(controllerVersion.isNotBlank() && controllerVersion.length <= 64 &&
            controllerVersion.none { it.isISOControl() }) { "Invalid Bluetooth controller version" }
        require(fetchedAtMillis >= 0) { "Invalid Bluetooth metadata timestamp" }
    }

    override fun toString(): String = "BleVehicleMetadata(redacted)"

    companion object {
        private val COMPACT_ADDRESS = Regex("[0-9A-Fa-f]{12}")
        private val COLON_ADDRESS = Regex("(?:[0-9A-Fa-f]{2}:){5}[0-9A-Fa-f]{2}")

        fun normalizeAddress(value: String): String? {
            val trimmed = value.trim()
            val compact = when {
                COMPACT_ADDRESS.matches(trimmed) -> trimmed
                COLON_ADDRESS.matches(trimmed) -> trimmed.replace(":", "")
                else -> return null
            }.uppercase(Locale.ROOT)
            if (compact == "000000000000" || compact == "FFFFFFFFFFFF") return null
            return compact.chunked(2).joinToString(":")
        }

        fun fromResponse(response: JSONObject, expectedVin: String, fetchedAtMillis: Long): BleVehicleMetadata? {
            require(expectedVin.isNotBlank()) { "Missing Bluetooth vehicle scope" }
            BleCloudApiModels.requireSuccess(response)
            val data = response.opt("data") as? JSONObject
                ?: throw IllegalArgumentException("Missing Bluetooth vehicle data")
            if (data.has("vin")) require(data.opt("vin") == expectedVin) { "Bluetooth vehicle scope changed" }
            val config = optionalObject(data, "config") ?: return null
            val bluetooth = optionalObject(config, "4") ?: return null
            val address = (bluetooth.opt("mac") as? String)?.let(::normalizeAddress)
                ?: throw IllegalArgumentException("Invalid Bluetooth vehicle address")
            val version = bluetooth.opt("version") as? String
                ?: throw IllegalArgumentException("Invalid Bluetooth controller version")
            return BleVehicleMetadata(address, version, fetchedAtMillis)
        }

        private fun optionalObject(parent: JSONObject, key: String): JSONObject? {
            val value = parent.opt(key)
            if (value == null || value == JSONObject.NULL) return null
            return value as? JSONObject ?: throw IllegalArgumentException("Invalid Bluetooth vehicle configuration")
        }
    }
}

internal data class BleCloudRequestScope(
    val oldAccountId: String?,
    val gatewayAccountId: String?,
    val vin: String,
    val deviceId: String,
    val generation: Long
) {
    fun matches(session: Session): Boolean = this == capture(session)

    override fun toString(): String = "BleCloudRequestScope(redacted)"

    companion object {
        fun capture(session: Session): BleCloudRequestScope = BleCloudRequestScope(
            session.oldAuth?.accountId, session.newAuth?.accountId,
            session.selectedVin, session.deviceId, session.generation
        )
    }
}

internal object BleCloudApiModels {
    fun requireSuccess(response: JSONObject) {
        require(isZero(response.opt("code"))) { "Bluetooth cloud request was not accepted" }
        if (response.has("result")) require(isZero(response.opt("result"))) {
            "Bluetooth cloud result was not accepted"
        }
        if (response.has("success")) require(response.opt("success") == true) {
            "Bluetooth cloud request was not successful"
        }
    }

    fun configurationParameters(vin: String, configuration: BlePassiveConfiguration): Map<String, String> {
        require(vin.isNotBlank()) { "Missing Bluetooth vehicle scope" }
        // These are saved preferences, not the effective flags sent to the vehicle.
        val conf = JSONObject()
            .put("bleKeySwitch", configuration.enabled)
            .put("bleKeyUnlock", configuration.autoUnlock)
            .put("bleKeyLock", configuration.autoLock)
            .put("bleKeyBtn", configuration.buttonEnabled)
        return linkedMapOf("vin" to vin, "conf" to conf.toString())
    }

    fun calibrationParameters(vin: String, params: String?, model: String): Map<String, String> {
        require(vin.isNotBlank()) { "Missing Bluetooth vehicle scope" }
        require(model.isNotBlank() && model.length <= 128 && model.none { it.isISOControl() }) {
            "Invalid Bluetooth calibration device model"
        }
        require(params == null || BleCalibration.parseOrNull(params) != null) {
            "Invalid Bluetooth calibration parameters"
        }
        return linkedMapOf(
            "vin" to vin,
            "anchorType" to "0",
            "model" to model,
            "operate" to if (params == null) "delete" else "add",
            "params" to params.orEmpty()
        )
    }

    private fun isZero(value: Any?): Boolean = when (value) {
        is Number -> value.toString() == "0"
        is String -> value == "0"
        else -> false
    }
}
