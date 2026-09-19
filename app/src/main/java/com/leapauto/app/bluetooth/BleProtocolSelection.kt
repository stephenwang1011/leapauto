package com.leapauto.app.bluetooth

internal object BleProtocolSelection {
    fun forManualConnection(
        selected: BleNearbyDevice,
        scannedDevices: List<BleNearbyDevice>,
        binding: BleManagedKey?,
        identity: BleSessionIdentity,
        certificate: BleKeyCertificate
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
        return observed.copy(
            protocolMinor = saved?.device?.protocolMinor ?: BleKeyProtocol.DEFAULT_PROTOCOL_MINOR,
            protocolMinorSource = if (saved != null) BleProtocolMinorSource.SAVED else BleProtocolMinorSource.DEFAULT
        )
    }
}
