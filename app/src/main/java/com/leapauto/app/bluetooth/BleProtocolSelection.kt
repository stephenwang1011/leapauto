package com.leapauto.app.bluetooth

internal object BleProtocolSelection {
    fun forManualConnection(
        selected: BleNearbyDevice,
        scannedDevices: List<BleNearbyDevice>,
        binding: BleManagedKey?,
        identity: BleSessionIdentity,
        certificate: BleKeyCertificate,
        carType: String? = null
    ): BleNearbyDevice {
        // A current scan record supersedes a UI snapshot from an earlier scan.
        val observed = scannedDevices.firstOrNull { it.address.equals(selected.address, ignoreCase = true) }
            ?: selected
        if (observed.protocolMinorSource == BleProtocolMinorSource.ADVERTISED &&
            observed.protocolMinor?.let { it in 0..255 } == true) return observed
        val saved = binding?.takeIf {
            it.accountId == identity.accountId && it.vin == identity.vin &&
                (it.deviceId.isEmpty() || it.deviceId == identity.deviceId) &&
                it.device.address.equals(observed.address, ignoreCase = true) && it.matchesCertificate(certificate)
        }
        val defaultMinor = BleKeyProtocol.defaultProtocolMinor(carType)
        val resolvedMinor = if (saved != null) {
            val savedMinor = saved.device.protocolMinor ?: defaultMinor
            val isLeap3 = carType.orEmpty().uppercase().let { it.contains("C16") || it.contains("C10") }
            if (isLeap3 && savedMinor < BleKeyProtocol.LEAP3_PROTOCOL_MINOR) {
                BleKeyProtocol.LEAP3_PROTOCOL_MINOR
            } else {
                savedMinor
            }
        } else {
            defaultMinor
        }
        return observed.copy(
            protocolMinor = resolvedMinor,
            protocolMinorSource = if (saved != null) BleProtocolMinorSource.SAVED else BleProtocolMinorSource.DEFAULT
        )
    }

    fun forManualConnection(
        selected: BleNearbyDevice,
        scannedDevices: List<BleNearbyDevice>,
        binding: BleManagedKey?,
        identity: BleSessionIdentity,
        certificate: BleKeyCertificate
    ): BleNearbyDevice = forManualConnection(
        selected = selected,
        scannedDevices = scannedDevices,
        binding = binding,
        identity = identity,
        certificate = certificate,
        carType = null
    )
}
