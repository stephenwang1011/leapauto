package com.leapauto.app.bluetooth

enum class BleAuthenticationTextProfile {
    LEGACY_VERSIONED,
    ONE_PAO_V010
}

data class BlePassiveConfiguration(
    val enabled: Boolean = false,
    val autoUnlock: Boolean = false,
    val autoLock: Boolean = false,
    val buttonEnabled: Boolean = false,
    val calibration: BleCalibration = BleCalibration.DEFAULT
) {
    val needsBackground: Boolean get() = enabled

    internal fun authenticationFields(
        protocolMinor: Int,
        profile: BleAuthenticationTextProfile = BleAuthenticationTextProfile.LEGACY_VERSIONED
    ): String = "${calibration.toProtocolText()};${authenticationFlags(protocolMinor, profile)
        .joinToString(";")};"

    internal fun encoded(protocolMinor: Int): ByteArray =
        calibration.encoded() + flags(protocolMinor)

    internal fun reconnectFields(supportsButton: Boolean = false): ByteArray = calibration.encoded() + byteArrayOf(
        enabled.byte(),
        (enabled && autoLock).byte(),
        (enabled && autoUnlock).byte(),
        supportsButton.byte()
    )

    private fun authenticationFlags(protocolMinor: Int, profile: BleAuthenticationTextProfile): ByteArray =
        when (profile) {
            BleAuthenticationTextProfile.LEGACY_VERSIONED -> flags(protocolMinor)
            BleAuthenticationTextProfile.ONE_PAO_V010 -> {
                require(protocolMinor in 0..255) { "Invalid BLE configuration protocol minor" }
                byteArrayOf(
                    enabled.byte(),
                    (enabled && autoLock).byte(),
                    (enabled && autoUnlock).byte(),
                    false.byte()
                )
            }
        }

    private fun flags(protocolMinor: Int): ByteArray {
        require(protocolMinor in 0..255) { "Invalid BLE configuration protocol minor" }
        require(!enabled || !buttonEnabled || protocolMinor >= 9) {
            "BLE button configuration requires protocol minor 9 or later"
        }
        // Original g91: older vehicles combine the master and passive-unlock flags.
        val flags = byteArrayOf(
            (enabled && (protocolMinor >= 9 || autoUnlock)).byte(),
            (enabled && autoLock).byte()
        )
        return if (protocolMinor < 9) flags else flags + byteArrayOf(
            (enabled && autoUnlock).byte(),
            (enabled && buttonEnabled).byte()
        )
    }

    companion object {
        val MANUAL = BlePassiveConfiguration(enabled = true)

        fun defaultForModel(carType: String? = null, protocolMinor: Int? = null): BlePassiveConfiguration {
            val model = carType.orEmpty().uppercase()
            val isLeap3 = model.contains("C16") || model.contains("C10") || (protocolMinor ?: 0) >= 9
            val supportsButton = (protocolMinor == null || protocolMinor >= 9) || isLeap3
            return BlePassiveConfiguration(
                enabled = true,
                autoUnlock = false,
                autoLock = false,
                buttonEnabled = supportsButton,
                calibration = BleCalibration.defaultForModel(carType)
            )
        }
    }
}

private fun Boolean.byte(): Byte = if (this) 1 else 0
