package com.leapauto.app.bluetooth

import java.util.Locale
import java.util.UUID

enum class BleProtocolMinorSource(val diagnosticCode: Int) {
    UNKNOWN(0), ADVERTISED(1), DEFAULT(2), SAVED(3)
}

/** Keeps split advertisements together for one bounded scan session. */
class BleScanDeviceCache(
    private val maxTrackedDevices: Int = 128,
    private val maxDisplayedDevices: Int = 30
) {
    private data class Entry(
        val address: String,
        val name: String,
        val rssi: Int,
        val connectable: Boolean,
        val hasKeyService: Boolean,
        val advertisedMinor: Int?
    ) {
        fun nearbyDevice(): BleNearbyDevice? = if (!connectable || !hasKeyService) null else BleNearbyDevice(
            address, name, rssi, advertisedMinor ?: BleKeyProtocol.DEFAULT_PROTOCOL_MINOR,
            if (advertisedMinor == null) BleProtocolMinorSource.DEFAULT else BleProtocolMinorSource.ADVERTISED
        )

        override fun toString(): String = "BleScanEntry(redacted)"
    }

    private val entries = LinkedHashMap<String, Entry>()

    init {
        require(maxTrackedDevices > 0 && maxDisplayedDevices in 1..maxTrackedDevices)
    }

    fun clear() = entries.clear()

    fun update(
        address: String,
        name: String?,
        rssi: Int,
        connectable: Boolean,
        serviceUuids: List<UUID>
    ): BleNearbyDevice? {
        val normalizedAddress = address.uppercase(Locale.ROOT)
        if (!MAC_ADDRESS.matches(normalizedAddress)) return null
        val previous = entries.remove(normalizedAddress)
        val entry = Entry(
            normalizedAddress,
            name?.take(64)?.takeIf { it.isNotBlank() } ?: previous?.name ?: "蓝牙车辆",
            rssi,
            connectable,
            previous?.hasKeyService == true || BleKeyProtocol.SERVICE_UUID in serviceUuids,
            BleKeyProtocol.advertisedProtocolMinor(serviceUuids) ?: previous?.advertisedMinor
        )
        entries[normalizedAddress] = entry
        if (entries.size > maxTrackedDevices) entries.remove(entries.keys.first())
        return entry.nearbyDevice()
    }

    fun devices(preferredAddress: String? = null): List<BleNearbyDevice> = entries.values.mapNotNull(Entry::nearbyDevice)
        .sortedWith(compareByDescending<BleNearbyDevice> { it.address.equals(preferredAddress, ignoreCase = true) }
            .thenByDescending { it.rssi }).take(maxDisplayedDevices)

    companion object {
        private val MAC_ADDRESS = Regex("(?:[0-9A-F]{2}:){5}[0-9A-F]{2}")
    }
}
