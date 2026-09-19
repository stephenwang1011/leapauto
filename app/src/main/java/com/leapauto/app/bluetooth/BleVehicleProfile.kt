package com.leapauto.app.bluetooth

import org.json.JSONObject

enum class BleCloudSaveStatus { UNSAVED, PENDING, SAVING, SAVED, FAILED }

data class BleCloudSyncState(
    val configuration: BleCloudSaveStatus = BleCloudSaveStatus.UNSAVED,
    val calibration: BleCloudSaveStatus = BleCloudSaveStatus.UNSAVED
)

data class BleVehicleProfile(
    val metadata: BleVehicleMetadata? = null,
    val calibration: BleCalibration? = null,
    val uploadedConfiguration: BlePassiveConfiguration? = null,
    val calibrationRequested: Boolean = false,
    val cloudState: BleCloudSyncState = BleCloudSyncState()
) {
    init {
        require(uploadedConfiguration != null || cloudState.configuration == BleCloudSaveStatus.UNSAVED) {
            "Missing Bluetooth cloud configuration"
        }
        require(calibrationRequested || cloudState.calibration == BleCloudSaveStatus.UNSAVED) {
            "Missing Bluetooth calibration request"
        }
    }

    val effectiveCalibration: BleCalibration get() = calibration ?: BleCalibration.DEFAULT

    fun interrupted(): BleVehicleProfile = copy(cloudState = BleCloudSyncState(
        cloudState.configuration.recovered(), cloudState.calibration.recovered()
    ))

    override fun toString(): String = "BleVehicleProfile(identity=redacted, cloudState=$cloudState)"

    internal fun toJson(): JSONObject = JSONObject().apply {
        put("schema", 1)
        put("metadata", metadata?.let { JSONObject().apply {
            put("address", it.address)
            put("version", it.controllerVersion)
            put("fetchedAt", it.fetchedAtMillis)
        } } ?: JSONObject.NULL)
        put("calibration", calibration?.toProtocolText() ?: JSONObject.NULL)
        put("configuration", uploadedConfiguration?.let { JSONObject().apply {
            put("enabled", it.enabled)
            put("autoUnlock", it.autoUnlock)
            put("autoLock", it.autoLock)
            put("buttonEnabled", it.buttonEnabled)
        } } ?: JSONObject.NULL)
        put("calibrationRequested", calibrationRequested)
        put("configurationStatus", cloudState.configuration.name)
        put("calibrationStatus", cloudState.calibration.name)
    }

    companion object {
        const val METADATA_MAX_AGE_MILLIS = 24 * 60 * 60 * 1_000L

        fun freshMetadata(metadata: BleVehicleMetadata?, nowMillis: Long): BleVehicleMetadata? =
            metadata?.takeIf { it.fetchedAtMillis <= nowMillis &&
                nowMillis - it.fetchedAtMillis <= METADATA_MAX_AGE_MILLIS }

        internal fun fromJson(json: JSONObject): BleVehicleProfile {
            require(json.opt("schema") == 1) { "Invalid BLE profile schema" }
            val metadata = nullableObject(json, "metadata")?.let {
                BleVehicleMetadata(strictString(it, "address"), strictString(it, "version"),
                    strictInteger(it, "fetchedAt"))
            }
            val calibration = when (val value = json.opt("calibration")) {
                JSONObject.NULL -> null
                is String -> requireNotNull(BleCalibration.parseOrNull(value)) { "Invalid BLE calibration" }
                else -> throw IllegalArgumentException("Invalid BLE calibration")
            }
            val configuration = nullableObject(json, "configuration")?.let {
                BlePassiveConfiguration(strictBoolean(it, "enabled"), strictBoolean(it, "autoUnlock"),
                    strictBoolean(it, "autoLock"), strictBoolean(it, "buttonEnabled"))
            }
            return BleVehicleProfile(metadata, calibration, configuration, strictBoolean(json, "calibrationRequested"),
                BleCloudSyncState(BleCloudSaveStatus.valueOf(strictString(json, "configurationStatus")),
                    BleCloudSaveStatus.valueOf(strictString(json, "calibrationStatus")))).interrupted()
        }

        private fun nullableObject(json: JSONObject, key: String): JSONObject? = when (val value = json.opt(key)) {
            JSONObject.NULL -> null
            is JSONObject -> value
            else -> throw IllegalArgumentException("Invalid BLE profile object")
        }

        private fun strictString(json: JSONObject, key: String): String =
            requireNotNull(json.opt(key) as? String) { "Invalid BLE profile string" }

        private fun strictInteger(json: JSONObject, key: String): Long = when (val value = json.opt(key)) {
            is Int -> value.toLong()
            is Long -> value
            else -> throw IllegalArgumentException("Invalid BLE profile integer")
        }

        private fun strictBoolean(json: JSONObject, key: String): Boolean =
            requireNotNull(json.opt(key) as? Boolean) { "Invalid BLE profile flag" }
    }
}

private fun BleCloudSaveStatus.recovered(): BleCloudSaveStatus =
    if (this == BleCloudSaveStatus.SAVING) BleCloudSaveStatus.FAILED else this
