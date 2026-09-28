package com.leapauto.app.bluetooth

import org.junit.Assert.assertEquals
import org.junit.Test

class BleProtocolSelectionTest {
    private val address = "00:11:22:33:AA:BB"
    private val vin = "LTEST000000000001"
    private val identity = BleSessionIdentity("synthetic-account", vin, 1, "synthetic-device")
    private val certificate = BleKeyCertificate(
        "synthetic-public-key", 1, "A".repeat(80), "synthetic-certificate", "synthetic-signature", vin
    )
    private val selected = device(8, BleProtocolMinorSource.DEFAULT)

    @Test
    fun latestAdvertisedVersionReplacesStaleClickSnapshotAndSavedVersion() {
        val latest = device(9, BleProtocolMinorSource.ADVERTISED).copy(name = "latest", rssi = -45)
        assertEquals(latest, select(scanned = listOf(latest), binding = binding(8)))
    }

    @Test
    fun latestExplicitLegacyVersionOverridesBothOlderAdvertisementAndBinding() {
        val older = device(9, BleProtocolMinorSource.ADVERTISED)
        val latest = device(8, BleProtocolMinorSource.ADVERTISED)
        assertEquals(latest, select(selected = older, scanned = listOf(latest), binding = binding(9)))
    }

    @Test
    fun absentAdvertisedVersionUsesMatchingBindingWithoutLosingCurrentScanMetadata() {
        val latest = selected.copy(name = "latest", rssi = -42)
        val resolved = select(scanned = listOf(latest), binding = binding(9))
        assertEquals(latest.copy(protocolMinor = 9, protocolMinorSource = BleProtocolMinorSource.SAVED), resolved)
    }

    @Test
    fun bindingMustMatchAccountVehicleAndAddress() {
        val trusted = binding(9)
        val mismatched = listOf(
            trusted.copy(accountId = "other-account"),
            trusted.copy(vin = "LTEST000000000002"),
            trusted.copy(device = trusted.device.copy(address = "00:11:22:33:AA:CC"))
        )
        for (binding in mismatched) assertEquals(selected, select(binding = binding))
    }

    @Test
    fun changedCertificateCannotReuseOldBindingVersion() {
        val replacement = certificate.copy(signResult = "different-signature")
        assertEquals(selected, select(binding = binding(9), certificate = replacement))
    }

    @Test
    fun bindingFromAnotherSelectedVehicleCannotSupplyVersion() {
        val anotherIdentity = identity.copy(vin = "LTEST000000000002")
        assertEquals(selected, BleProtocolSelection.forManualConnection(
            selected, listOf(selected), binding(9), anotherIdentity, certificate
        ))
    }

    @Test
    fun unknownVersionWithoutTrustedBindingRetainsDefaultEight() {
        for (source in listOf(BleProtocolMinorSource.UNKNOWN, BleProtocolMinorSource.DEFAULT, BleProtocolMinorSource.SAVED)) {
            val unverified = device(9, source)
            assertEquals(selected, select(selected = unverified, scanned = listOf(unverified)))
        }
    }

    @Test
    fun invalidAdvertisedValuesFallBackToVerifiedBindingOrDefault() {
        for (minor in listOf(null, -1, 256)) {
            val invalid = device(minor, BleProtocolMinorSource.ADVERTISED)
            assertEquals(selected, select(selected = invalid, scanned = listOf(invalid)))
            assertEquals(device(9, BleProtocolMinorSource.SAVED),
                select(selected = invalid, scanned = listOf(invalid), binding = binding(9)))
        }
    }

    @Test
    fun explicitlyAdvertisedProtocolBoundsRemainValid() {
        for (minor in listOf(0, 255)) {
            val latest = device(minor, BleProtocolMinorSource.ADVERTISED)
            assertEquals(latest, select(scanned = listOf(latest), binding = binding(9)))
        }
    }

    @Test
    fun currentScanWithoutVersionSupersedesExplicitSnapshotFromEarlierScan() {
        val older = device(9, BleProtocolMinorSource.ADVERTISED)
        assertEquals(selected, select(selected = older, scanned = listOf(selected)))
        assertEquals(device(10, BleProtocolMinorSource.SAVED),
            select(selected = older, scanned = listOf(selected), binding = binding(10)))
    }

    @Test
    fun absentCurrentRecordPreservesExplicitSnapshotWithoutTakingOtherDevicesVersion() {
        val advertised = device(9, BleProtocolMinorSource.ADVERTISED)
        val other = device(10, BleProtocolMinorSource.ADVERTISED).copy(address = "00:11:22:33:AA:CC")
        assertEquals(advertised, select(selected = advertised, scanned = listOf(other)))
        assertEquals(selected, select(scanned = listOf(other)))
    }

    @Test
    fun addressCaseDoesNotPreventUsingCurrentScanOrMatchingBinding() {
        val lowerCase = selected.copy(address = address.lowercase())
        val saved = binding(9).copy(device = binding(9).device.copy(address = address.lowercase()))
        assertEquals(device(9, BleProtocolMinorSource.SAVED),
            select(selected = lowerCase, scanned = listOf(selected), binding = saved))
    }

    @Test
    fun c16ModelAutomaticallyResolvesToMinorNineAndHealsLegacyEight() {
        // 无广播且无绑定时，C16 默认 9
        val unverified = device(null, BleProtocolMinorSource.UNKNOWN)
        val resolvedDefault = BleProtocolSelection.forManualConnection(
            unverified, listOf(unverified), null, identity, certificate, "C16"
        )
        assertEquals(9, resolvedDefault.protocolMinor)
        assertEquals(BleProtocolMinorSource.DEFAULT, resolvedDefault.protocolMinorSource)

        // 历史绑定存了 8 时，C16 自动自愈提升为 9
        val savedEight = binding(8)
        val resolvedHealed = BleProtocolSelection.forManualConnection(
            unverified, listOf(unverified), savedEight, identity, certificate, "零跑C16"
        )
        assertEquals(9, resolvedHealed.protocolMinor)
        assertEquals(BleProtocolMinorSource.SAVED, resolvedHealed.protocolMinorSource)

        // 老车型 (T03) 保持原有默认 8，不强行升级
        val resolvedT03 = BleProtocolSelection.forManualConnection(
            unverified, listOf(unverified), null, identity, certificate, "T03"
        )
        assertEquals(8, resolvedT03.protocolMinor)

        // 实车广播了明确 minor 时，依然绝对优先
        val advertised = device(8, BleProtocolMinorSource.ADVERTISED)
        val resolvedAdvertised = BleProtocolSelection.forManualConnection(
            advertised, listOf(advertised), savedEight, identity, certificate, "C16"
        )
        assertEquals(8, resolvedAdvertised.protocolMinor)
        assertEquals(BleProtocolMinorSource.ADVERTISED, resolvedAdvertised.protocolMinorSource)
    }

    private fun device(minor: Int?, source: BleProtocolMinorSource) =
        BleNearbyDevice(address, "synthetic-vehicle", -60, minor, source)

    private fun binding(minor: Int) = BleManagedKey(
        identity.accountId, vin, device(minor, BleProtocolMinorSource.UNKNOWN),
        BleKeyProtocol.certificateFingerprint(certificate)
    )

    private fun select(
        selected: BleNearbyDevice = this.selected,
        scanned: List<BleNearbyDevice> = listOf(this.selected),
        binding: BleManagedKey? = null,
        certificate: BleKeyCertificate = this.certificate
    ): BleNearbyDevice = BleProtocolSelection.forManualConnection(selected, scanned, binding, identity, certificate)
}
