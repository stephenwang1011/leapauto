package com.leapauto.app.bluetooth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class BleScanDeviceCacheTest {
    private val address = "00:11:22:33:AA:BB"
    private val otherAddress = "00:11:22:33:AA:CC"
    private val keyService = BleKeyProtocol.SERVICE_UUID
    private val minor8 = UUID.fromString("00000108-0000-1000-8000-00805f9b34fb")
    private val minor9 = UUID.fromString("00000109-0000-1000-8000-00805f9b34fb")

    @Test
    fun metadataTargetSurvivesDisplayLimitWithoutChangingProtocolOrFilteringOtherDevices() {
        val cache = BleScanDeviceCache(maxTrackedDevices = 8, maxDisplayedDevices = 1)
        cache.update(address, "same name", -95, true, listOf(keyService, minor9))
        cache.update(otherAddress, "same name", -30, true, listOf(keyService, minor8))
        assertEquals(otherAddress, cache.devices().single().address)
        val preferred = cache.devices(address.lowercase()).single()
        assertEquals(address, preferred.address)
        assertEquals(9, preferred.protocolMinor)
        assertEquals(otherAddress, cache.devices("00:22:33:44:55:66").single().address)
    }

    @Test
    fun metadataCannotInventAConnectableDeviceOrItsService() {
        val cache = BleScanDeviceCache()
        cache.update(address, "same name", -20, true, listOf(minor9))
        cache.update(otherAddress, "same name", -60, true, listOf(keyService))
        assertEquals(listOf(otherAddress), cache.devices(address).map { it.address })
    }

    @Test
    fun minorArrivingBeforeKeyServiceSurvivesSplitAdvertisements() {
        val cache = BleScanDeviceCache()
        assertNull(cache.update(address, "vehicle", -80, true, listOf(minor9)))
        assertTrue(cache.devices().isEmpty())
        val vehicle = requireNotNull(cache.update(address, null, -72, true, listOf(keyService)))
        assertEquals(9, vehicle.protocolMinor)
        assertEquals(BleProtocolMinorSource.ADVERTISED, vehicle.protocolMinorSource)
        assertEquals("vehicle", vehicle.name)
        assertEquals(-72, vehicle.rssi)
    }

    @Test
    fun keyServiceBeforeMinorUsesFallbackUntilExplicitVersionArrives() {
        val cache = BleScanDeviceCache()
        val fallback = requireNotNull(cache.update(address, null, -75, true, listOf(keyService)))
        assertEquals(8, fallback.protocolMinor)
        assertEquals(BleProtocolMinorSource.DEFAULT, fallback.protocolMinorSource)
        val advertised = requireNotNull(cache.update(address, null, -70, true, listOf(minor9)))
        assertEquals(9, advertised.protocolMinor)
        assertEquals(BleProtocolMinorSource.ADVERTISED, advertised.protocolMinorSource)
        assertEquals(listOf(advertised), cache.devices())
    }

    @Test
    fun emptyOrUnrelatedAdvertisementsKeepMinorAndService() {
        val cache = BleScanDeviceCache()
        cache.update(address, "vehicle", -70, true, listOf(keyService, minor9))
        val vehicle = requireNotNull(cache.update(address, " ", -65, true, emptyList()))
        assertEquals(9, vehicle.protocolMinor)
        assertEquals("vehicle", vehicle.name)
        assertEquals(-65, vehicle.rssi)
        val unrelated = UUID.fromString("00000109-0000-1000-8000-00805f9b34fc")
        assertEquals(9, requireNotNull(cache.update(address, null, -60, true, listOf(unrelated))).protocolMinor)
    }

    @Test
    fun latestExplicitMinorReplacesEarlierVersionIncludingLegacyVersion() {
        val cache = BleScanDeviceCache()
        cache.update(address, null, -70, true, listOf(keyService, minor9))
        val vehicle = requireNotNull(cache.update(address, null, -70, true, listOf(minor8)))
        assertEquals(8, vehicle.protocolMinor)
        assertEquals(BleProtocolMinorSource.ADVERTISED, vehicle.protocolMinorSource)
        assertEquals(8, requireNotNull(cache.update(address, null, -70, true, emptyList())).protocolMinor)
    }

    @Test
    fun metadataDoesNotCrossDeviceAddresses() {
        val cache = BleScanDeviceCache()
        cache.update(address, null, -70, true, listOf(minor9))
        val other = requireNotNull(cache.update(otherAddress, null, -70, true, listOf(keyService)))
        assertEquals(8, other.protocolMinor)
        assertEquals(BleProtocolMinorSource.DEFAULT, other.protocolMinorSource)
        assertEquals(listOf(other), cache.devices())
    }

    @Test
    fun onlyCurrentlyConnectableVehiclesWithObservedKeyServiceAreVisible() {
        val cache = BleScanDeviceCache()
        assertNull(cache.update(address, null, -70, false, listOf(keyService, minor9)))
        assertTrue(cache.devices().isEmpty())
        assertEquals(9, requireNotNull(cache.update(address, null, -70, true, emptyList())).protocolMinor)
        assertNull(cache.update(address, null, -70, false, emptyList()))
        assertTrue(cache.devices().isEmpty())
        assertEquals(9, requireNotNull(cache.update(address, null, -70, true, emptyList())).protocolMinor)
    }

    @Test
    fun addressCaseDoesNotSplitMetadataAndMalformedAddressesAreIgnored() {
        val cache = BleScanDeviceCache()
        cache.update(address.lowercase(), null, -70, true, listOf(minor9))
        assertEquals(9, requireNotNull(cache.update(address, null, -70, true, listOf(keyService))).protocolMinor)
        assertEquals(address, cache.devices().single().address)
        assertNull(cache.update("invalid", null, -70, true, listOf(keyService)))
        assertNull(cache.update("", null, -70, true, listOf(keyService)))
        assertEquals(1, cache.devices().size)
    }

    @Test
    fun cacheEvictsLeastRecentlyObservedAddressAtCapacity() {
        val cache = BleScanDeviceCache(maxTrackedDevices = 2, maxDisplayedDevices = 2)
        cache.update(address, null, -70, true, listOf(minor9))
        cache.update(otherAddress, null, -70, true, listOf(minor9))
        cache.update(address, null, -70, true, emptyList())
        cache.update("00:11:22:33:AA:DD", null, -70, true, emptyList())
        assertEquals(9, requireNotNull(cache.update(address, null, -70, true, listOf(keyService))).protocolMinor)
        val evicted = requireNotNull(cache.update(otherAddress, null, -70, true, listOf(keyService)))
        assertEquals(8, evicted.protocolMinor)
        assertEquals(BleProtocolMinorSource.DEFAULT, evicted.protocolMinorSource)
    }

    @Test
    fun newScanDoesNotReusePriorVersionOrServiceMetadata() {
        val cache = BleScanDeviceCache()
        cache.update(address, null, -70, true, listOf(keyService, minor9))
        cache.clear()
        assertTrue(cache.devices().isEmpty())
        assertNull(cache.update(address, null, -70, true, emptyList()))
        val vehicle = requireNotNull(cache.update(address, null, -70, true, listOf(keyService)))
        assertEquals(8, vehicle.protocolMinor)
        assertEquals(BleProtocolMinorSource.DEFAULT, vehicle.protocolMinorSource)
    }

    @Test
    fun displayLimitKeepsStrongestSignalsAndRetainsHiddenMetadata() {
        val cache = BleScanDeviceCache(maxTrackedDevices = 2, maxDisplayedDevices = 1)
        cache.update(address, null, -80, true, listOf(keyService, minor9))
        cache.update(otherAddress, null, -60, true, listOf(keyService))
        assertEquals(otherAddress, cache.devices().single().address)
        cache.update(address, "x".repeat(80), -50, true, emptyList())
        assertEquals(address, cache.devices().single().address)
        assertEquals(9, cache.devices().single().protocolMinor)
        assertEquals(64, cache.devices().single().name.length)
        assertEquals("BleNearbyDevice(redacted)", cache.devices().single().toString())
    }

    @Test(expected = IllegalArgumentException::class)
    fun cacheRejectsNonPositiveCapacity() {
        BleScanDeviceCache(maxTrackedDevices = 0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun cacheRejectsDisplayLimitBeyondCapacity() {
        BleScanDeviceCache(maxTrackedDevices = 1, maxDisplayedDevices = 2)
    }
}
