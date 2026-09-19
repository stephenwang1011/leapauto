package com.leapauto.app

import com.leapauto.app.bluetooth.BleKeyCertificate
import com.leapauto.app.bluetooth.BleCalibration
import com.leapauto.app.bluetooth.BleKeyProtocol
import com.leapauto.app.bluetooth.BleManagedKey
import com.leapauto.app.bluetooth.BleManagedKeyStorage
import com.leapauto.app.bluetooth.BleManagedKeyStore
import com.leapauto.app.bluetooth.BleNearbyDevice
import com.leapauto.app.bluetooth.BlePassiveConfiguration
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class BleManagedKeyTest {
    private val vin = "LTEST000000000001"
    private val enabled = BlePassiveConfiguration(enabled = true, autoUnlock = true, autoLock = true)

    private fun binding(accountId: String = "test-account", vehicle: String = vin): BleManagedKey = BleManagedKey(
        accountId = accountId,
        vin = vehicle,
        device = BleNearbyDevice("02:00:00:00:00:01", "Synthetic vehicle", -55, 9),
        certificateFingerprint = "ab".repeat(32)
    )

    @Test
    fun `new authenticated binding never enables passive behavior or background work`() {
        val binding = binding()
        assertEquals(BlePassiveConfiguration(), binding.desired)
        assertFalse(binding.desired.enabled)
        assertFalse(binding.desired.autoUnlock)
        assertFalse(binding.desired.autoLock)
        assertFalse(binding.desired.buttonEnabled)
        assertNull(binding.applied)
        assertNull(binding.confirmedRevision)
        assertFalse(binding.requested)
        assertFalse(binding.pending)
        assertFalse(binding.needsBackground)
    }

    @Test
    fun `only the matching configuration revision acknowledges the vehicle state`() {
        val requested = binding().request(enabled, 0)
        assertTrue(requested.pending)
        assertTrue(requested.needsBackground)
        assertEquals(1L, requested.revision)
        assertSame(requested, requested.confirmed(0, enabled))
        assertSame(requested, requested.confirmed(2, enabled))
        assertSame(requested, requested.confirmed(1, BlePassiveConfiguration()))
        val confirmed = requested.confirmed(1, enabled)
        assertEquals(enabled, confirmed.applied)
        assertEquals(confirmed.revision, confirmed.confirmedRevision)
        assertFalse(confirmed.pending)
        assertTrue(confirmed.needsBackground)
    }

    @Test
    fun `changed settings preserve applied state and reject a delayed prior acknowledgement`() {
        val initial = binding().request(enabled, 0).confirmed(1, enabled)
        val disable = initial.suspended()
        val enableAgain = disable.request(enabled, 0)
        assertEquals(enabled, disable.applied)
        assertTrue(disable.pending)
        assertTrue(disable.needsBackground)
        assertSame(enableAgain, enableAgain.confirmed(disable.revision, disable.desired))
        val confirmedDisable = disable.confirmed(disable.revision, disable.desired)
        assertFalse(confirmedDisable.pending)
        assertFalse(confirmedDisable.needsBackground)
        assertEquals(BlePassiveConfiguration(), confirmedDisable.applied)
    }

    @Test
    fun `disabling an unconfirmed binding remains pending and cannot fabricate a vehicle acknowledgement`() {
        val disabled = binding().suspended()
        assertNull(disabled.applied)
        assertTrue(disabled.requested)
        assertTrue(disabled.pending)
        assertTrue(disabled.needsBackground)
    }

    @Test
    fun `unrequested configurations cannot be acknowledged`() {
        val original = binding()
        assertSame(original, original.confirmed(0, original.desired))
    }

    @Test
    fun `failed enabling followed by disabling still requires a fresh vehicle acknowledgement`() {
        val disabled = binding().suspended().let { it.confirmed(it.revision, it.desired) }
        val uncertainEnable = disabled.request(enabled, 0)
        val disableAgain = uncertainEnable.suspended()

        assertEquals(disabled.applied, disableAgain.desired)
        assertEquals(disabled.confirmedRevision, disableAgain.confirmedRevision)
        assertTrue(disableAgain.pending)
        assertTrue(disableAgain.needsBackground)
        assertSame(disableAgain, disableAgain.confirmed(uncertainEnable.revision, enabled))
        val confirmed = disableAgain.confirmed(disableAgain.revision, disableAgain.desired)
        assertFalse(confirmed.pending)
        assertFalse(confirmed.needsBackground)
    }

    @Test
    fun `repeating an already applied configuration requires its own acknowledgement`() {
        val first = binding().request(enabled, 0).confirmed(1, enabled)
        val repeated = first.request(enabled, 0)

        assertEquals(repeated.desired, repeated.applied)
        assertTrue(repeated.pending)
        assertSame(repeated, repeated.confirmed(first.revision, enabled))
        assertFalse(repeated.confirmed(repeated.revision, enabled).pending)
    }

    @Test
    fun `legacy records without a confirmed revision cannot claim synchronization`() {
        val original = binding().request(enabled, 0).confirmed(1, enabled)
        val json = original.toJson().apply { remove("confirmedRevision") }
        val restored = BleManagedKey.fromJson(json, original.accountId, original.vin)

        assertEquals(original.applied, restored.applied)
        assertNull(restored.confirmedRevision)
        assertTrue(restored.pending)
    }

    @Test
    fun `enabled authorization from a prior session is suspended in memory without pretending vehicle state changed`() {
        val original = binding().request(enabled, 7).confirmed(1, enabled)
        val storage = MemoryStorage()
        val store = BleManagedKeyStore(storage)
        assertTrue(store.save(original))

        val restored = requireNotNull(store.load(original.accountId, original.vin, 8))
        assertEquals(BlePassiveConfiguration(), restored.desired)
        assertEquals(enabled, restored.applied)
        assertEquals(original.confirmedRevision, restored.confirmedRevision)
        assertTrue(restored.pending)
        assertTrue(restored.needsBackground)
        assertEquals(original, requireNotNull(store.load(original.accountId, original.vin, 7)))
    }

    @Test
    fun `suspended authorization cannot be revived by a new session without an explicit request`() {
        val original = binding().request(enabled, 7).confirmed(1, enabled)
        val memory = original.forSession(8)

        assertFalse(memory.desired.enabled)
        assertEquals(enabled, memory.applied)
        assertTrue(memory.pending)
        assertEquals(-1L, memory.authorizationGeneration)
        assertFalse(memory.forSession(7).desired.enabled)
    }

    @Test
    fun `legacy stored enabled configuration without session authorization restores only a pending disable`() {
        val original = binding().request(enabled, 0).confirmed(1, enabled)
        val storage = MemoryStorage()
        val json = original.toJson().apply {
            remove("authorizationGeneration")
            remove("confirmedRevision")
        }
        storage.values[BleManagedKeyStore.storageKey(original.accountId, original.vin)] = json.toString()
        val restored = requireNotNull(BleManagedKeyStore(storage).load(original.accountId, original.vin, 0))

        assertFalse(restored.desired.enabled)
        assertEquals(enabled, restored.applied)
        assertNull(restored.confirmedRevision)
        assertEquals(-1L, restored.authorizationGeneration)
        assertTrue(restored.pending)
    }

    @Test
    fun `failed logout persistence cannot restore enabled authorization in a later session`() {
        val original = binding().request(enabled, 7).confirmed(1, enabled)
        val storage = MemoryStorage()
        val store = BleManagedKeyStore(storage)
        assertTrue(store.save(original))
        storage.rejectWrites = true
        assertFalse(store.suspendAll())

        val restartedStore = BleManagedKeyStore(storage)
        val restored = requireNotNull(restartedStore.load(original.accountId, original.vin, 8))
        assertFalse(restored.desired.enabled)
        assertEquals(enabled, restored.applied)
        assertTrue(restored.pending)
        assertEquals(2L, restored.revision)
        assertEquals(original, restartedStore.load(original.accountId, original.vin, 7))
    }

    @Test
    fun `all lifecycle states round trip without losing pending disable`() {
        val original = binding()
        val states = listOf(original, original.request(enabled, 0), original.request(enabled, 0).confirmed(1, enabled).suspended())
        states.forEach {
            assertEquals(it, BleManagedKey.fromJson(it.toJson(), it.accountId, it.vin))
            assertEquals(it, BleManagedKey.decodeStored(it.toJson().toString()))
        }
    }

    @Test
    fun `malformed JSON fields cannot default to enabled or a matching identity`() {
        assertEquals(binding(), BleManagedKey.fromJson(binding().toJson(), "test-account", vin))
        val mutations: List<(JSONObject) -> Unit> = listOf(
            { it.put("schemaVersion", 3) },
            { it.put("schemaVersion", "1") },
            { it.put("accountId", "other-account") },
            { it.put("vin", "LTEST000000000002") },
            { it.put("requested", "true") },
            { it.put("revision", 1.0) },
            { it.put("revision", -1) },
            { it.put("revision", Long.MAX_VALUE) },
            { it.put("confirmedRevision", 1) },
            { it.put("confirmedRevision", "0") },
            { it.put("authorizationGeneration", "0") },
            { it.put("authorizationGeneration", -2) },
            { it.remove("applied") },
            { it.put("applied", true) },
            { it.put("certificateFingerprint", "not-a-fingerprint") },
            { it.getJSONObject("device").put("address", "000000000001") },
            { it.getJSONObject("device").put("protocolMinor", JSONObject.NULL) },
            { it.getJSONObject("device").put("protocolMinor", 256) },
            { it.getJSONObject("device").put("rssi", -129) },
            { it.getJSONObject("device").put("name", "bad\nname") },
            { it.getJSONObject("desired").remove("autoLock") },
            { it.getJSONObject("desired").remove("calibration") },
            { it.getJSONObject("desired").getJSONObject("calibration").put("distanceCalibration", 256) },
            { it.getJSONObject("desired").getJSONObject("calibration").put("coefficientHundredths", 65_536) },
            { it.getJSONObject("desired").getJSONObject("calibration").put("unlockCalibration", -1) },
            { it.getJSONObject("desired").getJSONObject("calibration").put("lockCalibration", "16") },
            { it.getJSONObject("desired").getJSONObject("calibration").put("lockCalibration", 16.0) },
            { it.put("deviceId", "other-device") },
            { it.remove("deviceId") },
            { it.getJSONObject("desired").put("enabled", 1) }
        )
        mutations.forEach { mutate ->
            assertThrows(IllegalArgumentException::class.java) {
                BleManagedKey.fromJson(binding().toJson().also(mutate), "test-account", vin)
            }
        }
        assertThrows(IllegalArgumentException::class.java) {
            BleManagedKey.decodeStored(binding().toJson().toString() + " trailing-data")
        }
        assertThrows(IllegalArgumentException::class.java) { BleManagedKey.decodeStored(" ".repeat(8_193)) }
    }

    @Test
    fun `constructor rejects invalid identity metadata and revision overflow`() {
        listOf("", " test-account", "test-account\u0000other", "test;account").forEach {
            assertThrows(IllegalArgumentException::class.java) { binding(accountId = it) }
        }
        listOf("", "SHORT", "ltest0000000000010", "LTEST00000000000;0").forEach {
            assertThrows(IllegalArgumentException::class.java) { binding(vehicle = it) }
        }
        assertThrows(IllegalArgumentException::class.java) {
            binding().copy(device = binding().device.copy(protocolMinor = null))
        }
        assertThrows(IllegalArgumentException::class.java) { binding().copy(revision = Long.MAX_VALUE - 1).suspended() }
        assertThrows(IllegalArgumentException::class.java) { binding().request(enabled, -1) }
        assertThrows(IllegalArgumentException::class.java) { binding().forSession(-1) }
        assertThrows(IllegalArgumentException::class.java) {
            binding().copy(desired = enabled, applied = BlePassiveConfiguration(), confirmedRevision = 0)
        }
    }

    @Test
    fun `certificate changes require a new authenticated binding`() {
        val certificate = BleKeyCertificate("synthetic-public-key", 0, "a".repeat(80), "proof", "signature", vin)
        val trusted = binding().copy(certificateFingerprint = BleKeyProtocol.certificateFingerprint(certificate))
        assertTrue(trusted.matchesCertificate(certificate))
        assertFalse(trusted.matchesCertificate(certificate.copy(signResult = "new-signature")))
        assertFalse(trusted.matchesCertificate(certificate.copy(vin = "LTEST000000000002")))
    }

    @Test
    fun `string rendering and storage keys do not expose account or vehicle material`() {
        val binding = binding()
        val key = BleManagedKeyStore.storageKey(binding.accountId, binding.vin)
        listOf(binding.toString(), key).forEach { text ->
            listOf(binding.accountId, binding.vin, binding.device.address, binding.device.name, binding.certificateFingerprint)
                .forEach { assertFalse(text.contains(it)) }
        }
        assertFalse(key == BleManagedKeyStore.storageKey("other-account", vin))
        assertFalse(key == BleManagedKeyStore.storageKey(binding.accountId, "LTEST000000000002"))
    }

    @Test
    fun `store isolates multiple accounts and vehicles and clears only the requested binding`() {
        val storage = MemoryStorage()
        val store = BleManagedKeyStore(storage)
        val bindings = listOf(binding(), binding("other-account"), binding(vehicle = "LTEST000000000002"))
        bindings.forEach { assertTrue(store.save(it)) }
        bindings.forEach { assertEquals(it, store.load(it.accountId, it.vin, 0)) }
        assertEquals(3, storage.values.size)
        assertTrue(store.clear(bindings[0].accountId, bindings[0].vin))
        assertNull(store.load(bindings[0].accountId, bindings[0].vin, 0))
        assertNotNull(store.load(bindings[1].accountId, bindings[1].vin, 0))
        assertNotNull(store.load(bindings[2].accountId, bindings[2].vin, 0))
    }

    @Test
    fun `logout suspension preserves previously applied settings until vehicle confirmation`() {
        val storage = MemoryStorage()
        val store = BleManagedKeyStore(storage)
        val bindings = listOf(binding(), binding("other-account")).map { it.request(enabled, 0).confirmed(1, enabled) }
        bindings.forEach { assertTrue(store.save(it)) }
        assertTrue(store.suspendAll())
        bindings.forEach {
            val loaded = requireNotNull(store.load(it.accountId, it.vin, 0))
            assertEquals(enabled, loaded.applied)
            assertEquals(BlePassiveConfiguration(), loaded.desired)
            assertEquals(it.revision + 1, loaded.revision)
            assertTrue(loaded.requested)
            assertTrue(loaded.pending)
        }
    }

    @Test
    fun `corrupt or swapped persisted bindings never restore background access`() {
        val storage = MemoryStorage()
        val store = BleManagedKeyStore(storage)
        val key = BleManagedKeyStore.storageKey("test-account", vin)
        listOf("not-json", "{}", binding("other-account").toJson().toString()).forEach { value ->
            storage.values[key] = value
            assertNull(store.load("test-account", vin, 0))
            assertFalse(store.suspendAll())
        }
        storage.values[key] = "not-json"
        val good = binding("other-account").request(enabled, 0).confirmed(1, enabled)
        assertTrue(store.save(good))
        assertFalse(store.suspendAll())
        assertEquals(BlePassiveConfiguration(), store.load(good.accountId, good.vin, 0)?.desired)
    }

    @Test
    fun `storage failures are explicit without claiming the vehicle has been disabled`() {
        val storage = MemoryStorage()
        val store = BleManagedKeyStore(storage)
        val original = binding().request(enabled, 0).confirmed(1, enabled)
        assertTrue(store.save(original))
        storage.rejectWrites = true
        assertFalse(store.save(original.suspended()))
        assertFalse(store.suspendAll())
        assertEquals(original, store.load(original.accountId, original.vin, 0))
        storage.failReads = true
        assertNull(store.load(original.accountId, original.vin, 0))
        assertFalse(store.suspendAll())
    }

    @Test
    fun `schema one migrates only original default calibration without inventing authorization`() {
        val original = binding().request(enabled, 7).confirmed(1, enabled)
        val legacy = legacyJson(original)
        val restored = BleManagedKey.fromJson(legacy, original.accountId, vin)
        assertEquals(original, restored)
        assertEquals(BleCalibration.DEFAULT, restored.desired.calibration)
        assertEquals(BleCalibration.DEFAULT, restored.applied?.calibration)
        assertEquals(2, restored.toJson().getInt("schemaVersion"))
        assertThrows(IllegalArgumentException::class.java) {
            BleManagedKey.fromJson(legacyJson(original).apply {
                getJSONObject("desired").put("calibration", JSONObject())
            }, original.accountId, vin)
        }
    }

    @Test
    fun `custom calibration requires device identity and survives suspension with every switch off`() {
        val calibration = BleCalibration(61, 175, 12, 24)
        val configuration = enabled.copy(buttonEnabled = true, calibration = calibration)
        assertThrows(IllegalArgumentException::class.java) { binding().request(configuration, 7) }
        val original = binding().copy(deviceId = "test-device").request(configuration, 7)
            .confirmed(1, configuration)
        val suspended = original.forSession(8)
        assertEquals(BlePassiveConfiguration(calibration = calibration), suspended.desired)
        assertEquals(configuration, suspended.applied)
        assertTrue(suspended.pending)
        assertEquals(-1L, suspended.authorizationGeneration)
        assertFalse(suspended.forSession(7).desired.enabled)
        assertEquals(original, BleManagedKey.decodeStored(original.toJson().toString()))
        assertEquals(original, BleManagedKey.fromJson(original.toJson(), original.accountId, vin, original.deviceId))
        assertThrows(IllegalArgumentException::class.java) { original.forSession(7, "another-device") }
        assertThrows(IllegalArgumentException::class.java) {
            BleManagedKey.fromJson(original.toJson(), original.accountId, vin, "another-device")
        }
    }

    @Test
    fun `legacy default bindings migrate to an explicit device only with enabled behavior suspended`() {
        val original = binding().request(enabled, 7).confirmed(1, enabled)
        val storage = MemoryStorage()
        storage.values[BleManagedKeyStore.storageKey(original.accountId, vin)] = legacyJson(original).toString()
        val store = BleManagedKeyStore(storage)
        val migrated = requireNotNull(store.load(original.accountId, vin, 7, "test-device"))
        assertEquals("test-device", migrated.deviceId)
        assertEquals(BlePassiveConfiguration(), migrated.desired)
        assertEquals(enabled, migrated.applied)
        assertTrue(migrated.pending)
        assertEquals(-1L, migrated.authorizationGeneration)
        assertEquals(original, store.load(original.accountId, vin, 7))

        val unrequested = binding().forSession(7, "test-device")
        assertFalse(unrequested.requested)
        assertFalse(unrequested.needsBackground)
    }

    @Test
    fun `device scoped calibration cannot leak through swapped data or legacy fallback`() {
        val storage = MemoryStorage()
        val store = BleManagedKeyStore(storage)
        val first = binding().copy(deviceId = "first-device")
            .request(enabled.copy(calibration = BleCalibration(61, 175, 12, 24)), 7)
        val second = binding().copy(deviceId = "second-device")
            .request(enabled.copy(calibration = BleCalibration(63, 150, 10, 22)), 7)
        assertTrue(store.save(first))
        assertTrue(store.save(second))
        assertEquals(first, store.load(first.accountId, vin, 7, first.deviceId))
        assertEquals(second, store.load(second.accountId, vin, 7, second.deviceId))
        assertNull(store.load(first.accountId, vin, 7, "third-device"))
        assertTrue(store.save(binding()))
        val firstKey = BleManagedKeyStore.storageKey(first.accountId, vin, first.deviceId)
        storage.values[firstKey] = second.toJson().toString()
        assertNull(store.load(first.accountId, vin, 7, first.deviceId))
        storage.values[firstKey] = "not-json"
        assertNull(store.load(first.accountId, vin, 7, first.deviceId))
        storage.values[firstKey] = first.toJson().toString()
        storage.unreadableKeys += firstKey
        assertNull(store.load(first.accountId, vin, 7, first.deviceId))
        storage.unreadableKeys.clear()
        assertTrue(store.clear(first.accountId, vin, first.deviceId))
        assertEquals(second, store.load(second.accountId, vin, 7, second.deviceId))
        assertEquals(binding(), store.load(first.accountId, vin, 7))
    }

    @Test
    fun `logout keeps each device calibration but only retains a pending disable`() {
        val storage = MemoryStorage()
        val store = BleManagedKeyStore(storage)
        val configuration = enabled.copy(calibration = BleCalibration(61, 175, 12, 24))
        val original = binding().copy(deviceId = "test-device").request(configuration, 7)
            .confirmed(1, configuration)
        assertTrue(store.save(original))
        assertTrue(store.suspendAll())
        val restored = requireNotNull(store.load(original.accountId, vin, 7, original.deviceId))
        assertEquals(BlePassiveConfiguration(calibration = configuration.calibration), restored.desired)
        assertEquals(configuration, restored.applied)
        assertTrue(restored.pending)
        assertEquals(original.deviceId, restored.deviceId)
        assertFalse(BleManagedKeyStore.storageKey(original.accountId, vin, original.deviceId)
            .contains(original.deviceId))
    }

    private fun legacyJson(binding: BleManagedKey): JSONObject = binding.toJson().apply {
        put("schemaVersion", 1)
        remove("deviceId")
        getJSONObject("desired").remove("calibration")
        optJSONObject("applied")?.remove("calibration")
    }

    private class MemoryStorage : BleManagedKeyStorage {
        val values = linkedMapOf<String, String>()
        var rejectWrites = false
        var failReads = false
        val unreadableKeys = mutableSetOf<String>()

        override fun keys(): Set<String> = values.keys.toSet()
        override fun read(key: String): String? {
            check(!failReads) { "Synthetic storage failure" }
            if (key in unreadableKeys) return null
            return values[key]
        }

        override fun write(values: Map<String, String>): Boolean {
            if (rejectWrites) return false
            this.values.putAll(values)
            return true
        }

        override fun remove(key: String): Boolean {
            values.remove(key)
            return true
        }
    }
}
