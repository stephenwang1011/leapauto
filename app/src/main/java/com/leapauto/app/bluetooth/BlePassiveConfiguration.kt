package com.leapauto.app.bluetooth

data class BlePassiveConfiguration(
    val enabled: Boolean = false,
    val autoUnlock: Boolean = false,
    val autoLock: Boolean = false,
    val buttonEnabled: Boolean = false,
    val calibration: BleCalibration = BleCalibration.DEFAULT
) {
    val needsBackground: Boolean get() = enabled

    internal fun authenticationFields(protocolMinor: Int): String =
        "${calibration.toProtocolText()};${flags(protocolMinor).joinToString(";")};"

    internal fun encoded(protocolMinor: Int): ByteArray =
        calibration.encoded() + flags(protocolMinor)

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
    }
}

private fun Boolean.byte(): Byte = if (this) 1 else 0
