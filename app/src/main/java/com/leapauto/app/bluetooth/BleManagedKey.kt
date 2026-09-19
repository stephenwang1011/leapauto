package com.leapauto.app.bluetooth

import org.json.JSONObject
import org.json.JSONTokener

data class BleManagedKey(
    val accountId: String,
    val vin: String,
    val device: BleNearbyDevice,
    val certificateFingerprint: String,
    val desired: BlePassiveConfiguration = BlePassiveConfiguration(),
    val applied: BlePassiveConfiguration? = null,
    val requested: Boolean = false,
    val revision: Long = 0,
    val confirmedRevision: Long? = null,
    val authorizationGeneration: Long = -1,
    val deviceId: String = ""
) {
    init {
        require(validAccount(accountId) && validVin(vin)) { "Invalid Bluetooth binding scope" }
        require(ADDRESS.matches(device.address)) { "Invalid Bluetooth binding address" }
        require(device.name.length <= 248 && device.name.none { it.isISOControl() }) {
            "Invalid Bluetooth binding device name"
        }
        require(device.rssi in -128..127 && device.protocolMinor?.let { it in 0..255 } == true) {
            "Invalid Bluetooth binding device metadata"
        }
        require(FINGERPRINT.matches(certificateFingerprint)) { "Invalid Bluetooth certificate fingerprint" }
        require(revision >= 0 && revision < Long.MAX_VALUE) { "Invalid Bluetooth configuration revision" }
        require(confirmedRevision == null || (applied != null && confirmedRevision in 0..revision)) {
            "Invalid Bluetooth confirmed revision"
        }
        require(confirmedRevision != revision || applied == desired) { "Invalid Bluetooth acknowledged configuration" }
        require(authorizationGeneration >= -1) { "Invalid Bluetooth authorization generation" }
        require(deviceId.isEmpty() || validAccount(deviceId)) { "Invalid Bluetooth device scope" }
        require(deviceId.isNotEmpty() || hasDefaultCalibration) {
            "Custom Bluetooth calibration requires a device scope"
        }
    }

    val pending: Boolean get() = requested && confirmedRevision != revision
    val needsBackground: Boolean get() = requested && (desired.enabled || pending)
    private val hasDefaultCalibration: Boolean get() = desired.calibration == BleCalibration.DEFAULT &&
        (applied == null || applied.calibration == BleCalibration.DEFAULT)

    fun request(configuration: BlePassiveConfiguration, sessionGeneration: Long): BleManagedKey {
        require(sessionGeneration >= 0) { "Invalid Bluetooth authorization generation" }
        return copy(
            desired = configuration,
            requested = true,
            revision = Math.addExact(revision, 1),
            authorizationGeneration = sessionGeneration
        )
    }

    fun confirmed(ackRevision: Long, configuration: BlePassiveConfiguration): BleManagedKey =
        if (requested && ackRevision == revision && configuration == desired) {
            copy(applied = configuration, confirmedRevision = revision)
        } else this

    fun suspended(): BleManagedKey = copy(
        desired = BlePassiveConfiguration(calibration = desired.calibration),
        requested = true,
        revision = Math.addExact(revision, 1),
        authorizationGeneration = -1
    )

    fun forSession(sessionGeneration: Long, expectedDeviceId: String = deviceId): BleManagedKey {
        require(sessionGeneration >= 0) { "Invalid Bluetooth authorization generation" }
        require(expectedDeviceId.isEmpty() || validAccount(expectedDeviceId)) { "Invalid Bluetooth device scope" }
        if (deviceId != expectedDeviceId) {
            require(deviceId.isEmpty() && expectedDeviceId.isNotEmpty() && hasDefaultCalibration) {
                "Bluetooth device scope mismatch"
            }
            val migrated = copy(deviceId = expectedDeviceId)
            // Legacy records cannot authorize enabled behavior on an unidentified device.
            return if (desired.enabled || applied?.enabled == true) migrated.suspended() else migrated
        }
        return if (desired.enabled && authorizationGeneration != sessionGeneration) suspended() else this
    }

    fun matchesCertificate(certificate: BleKeyCertificate): Boolean =
        vin == certificate.vin && certificateFingerprint == BleKeyProtocol.certificateFingerprint(certificate)

    override fun toString(): String =
        "BleManagedKey(requested=$requested, pending=$pending, revision=$revision, identity=redacted)"

    // This representation contains vehicle identity and belongs only in encrypted storage.
    fun toJson(): JSONObject = JSONObject().apply {
        put("schemaVersion", 2)
        put("accountId", accountId)
        put("vin", vin)
        put("deviceId", deviceId)
        put("device", JSONObject().apply {
            put("address", device.address)
            put("name", device.name)
            put("rssi", device.rssi)
            put("protocolMinor", device.protocolMinor)
        })
        put("certificateFingerprint", certificateFingerprint)
        put("desired", configurationJson(desired))
        put("applied", applied?.let(::configurationJson) ?: JSONObject.NULL)
        put("requested", requested)
        put("revision", revision)
        put("confirmedRevision", confirmedRevision ?: JSONObject.NULL)
        put("authorizationGeneration", authorizationGeneration)
    }

    companion object {
        private val ADDRESS = Regex("(?:[0-9A-Fa-f]{2}:){5}[0-9A-Fa-f]{2}")
        private val FINGERPRINT = Regex("[0-9a-f]{64}")

        internal fun validAccount(value: String): Boolean = value.isNotBlank() && value.length <= 256 &&
            value == value.trim() && value.none { it.isISOControl() || it == ';' }

        internal fun validVin(value: String): Boolean =
            value.length == 17 && value.all { it in 'A'..'Z' || it in '0'..'9' }

        fun fromJson(
            json: JSONObject,
            expectedAccountId: String,
            expectedVin: String,
            expectedDeviceId: String = ""
        ): BleManagedKey {
            require(validAccount(expectedAccountId) && validVin(expectedVin)) { "Invalid Bluetooth binding scope" }
            require(expectedDeviceId.isEmpty() || validAccount(expectedDeviceId)) { "Invalid Bluetooth device scope" }
            val schema = integer(json, "schemaVersion")
            require(schema == 1L || schema == 2L) { "Unsupported Bluetooth binding schema" }
            require(string(json, "accountId") == expectedAccountId && string(json, "vin") == expectedVin) {
                "Bluetooth binding scope mismatch"
            }
            val deviceId = if (schema == 1L) {
                require(!json.has("deviceId")) { "Invalid legacy Bluetooth device scope" }
                ""
            } else string(json, "deviceId").also {
                require(it == expectedDeviceId) { "Bluetooth device scope mismatch" }
            }
            val device = json.opt("device") as? JSONObject ?: invalid()
            val applied = when (val value = json.opt("applied")) {
                JSONObject.NULL -> null
                is JSONObject -> configuration(value, schema)
                else -> invalid()
            }
            return BleManagedKey(
                accountId = expectedAccountId,
                vin = expectedVin,
                device = BleNearbyDevice(
                    string(device, "address"), string(device, "name"),
                    boundedInteger(device, "rssi", -128..127),
                    boundedInteger(device, "protocolMinor", 0..255)
                ),
                certificateFingerprint = string(json, "certificateFingerprint"),
                desired = configuration(json.opt("desired") as? JSONObject ?: invalid(), schema),
                applied = applied,
                requested = boolean(json, "requested"),
                revision = integer(json, "revision"),
                confirmedRevision = when (json.opt("confirmedRevision")) {
                    null, JSONObject.NULL -> null
                    else -> integer(json, "confirmedRevision")
                },
                authorizationGeneration = when (json.opt("authorizationGeneration")) {
                    null -> -1
                    else -> integer(json, "authorizationGeneration")
                },
                deviceId = deviceId
            )
        }

        internal fun decodeStored(value: String): BleManagedKey {
            require(value.length <= 8_192) { "Bluetooth binding is too large" }
            val tokens = JSONTokener(value)
            val json = tokens.nextValue() as? JSONObject ?: invalid()
            require(tokens.nextClean() == '\u0000') { "Invalid Bluetooth binding data" }
            return fromJson(
                json, string(json, "accountId"), string(json, "vin"),
                if (integer(json, "schemaVersion") == 1L) "" else string(json, "deviceId")
            )
        }

        private fun configurationJson(configuration: BlePassiveConfiguration): JSONObject = JSONObject().apply {
            put("enabled", configuration.enabled)
            put("autoUnlock", configuration.autoUnlock)
            put("autoLock", configuration.autoLock)
            put("buttonEnabled", configuration.buttonEnabled)
            put("calibration", JSONObject().apply {
                put("distanceCalibration", configuration.calibration.distanceCalibration)
                put("coefficientHundredths", configuration.calibration.coefficientHundredths)
                put("unlockCalibration", configuration.calibration.unlockCalibration)
                put("lockCalibration", configuration.calibration.lockCalibration)
            })
        }

        private fun configuration(json: JSONObject, schema: Long): BlePassiveConfiguration = BlePassiveConfiguration(
            enabled = boolean(json, "enabled"),
            autoUnlock = boolean(json, "autoUnlock"),
            autoLock = boolean(json, "autoLock"),
            buttonEnabled = boolean(json, "buttonEnabled"),
            calibration = if (schema == 1L) {
                require(!json.has("calibration")) { "Invalid legacy Bluetooth calibration" }
                BleCalibration.DEFAULT
            } else {
                val calibration = json.opt("calibration") as? JSONObject ?: invalid()
                BleCalibration(
                    boundedInteger(calibration, "distanceCalibration", BleCalibration.BYTE_PROTOCOL_RANGE),
                    boundedInteger(calibration, "coefficientHundredths", BleCalibration.COEFFICIENT_PROTOCOL_RANGE),
                    boundedInteger(calibration, "unlockCalibration", BleCalibration.BYTE_PROTOCOL_RANGE),
                    boundedInteger(calibration, "lockCalibration", BleCalibration.BYTE_PROTOCOL_RANGE)
                )
            }
        )

        private fun string(json: JSONObject, key: String): String = json.opt(key) as? String ?: invalid()
        private fun boolean(json: JSONObject, key: String): Boolean = json.opt(key) as? Boolean ?: invalid()

        private fun integer(json: JSONObject, key: String): Long = when (val value = json.opt(key)) {
            is Int -> value.toLong()
            is Long -> value
            else -> invalid()
        }

        private fun boundedInteger(json: JSONObject, key: String, range: IntRange): Int {
            val value = integer(json, key)
            require(value >= range.first && value <= range.last) { "Invalid Bluetooth binding metadata" }
            return value.toInt()
        }

        private fun invalid(): Nothing = throw IllegalArgumentException("Invalid Bluetooth binding data")
    }
}
