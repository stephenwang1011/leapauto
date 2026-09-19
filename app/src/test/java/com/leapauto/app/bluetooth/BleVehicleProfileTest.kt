package com.leapauto.app.bluetooth

import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class BleVehicleProfileTest {
    private val identity = BleSessionIdentity("test-account", "LTEST000000000001", 7, "test-device")
    private val now = 1_800_000_000_000L
    private val metadata = BleVehicleMetadata("02:00:00:00:00:01", "2.0", now)
    private val calibration = BleCalibration(61, 175, 12, 24)
    private val configuration = BlePassiveConfiguration(true, true, true, true)

    private fun profile() = BleVehicleProfile(
        metadata, calibration, configuration, true,
        BleCloudSyncState(BleCloudSaveStatus.SAVED, BleCloudSaveStatus.SAVED)
    )

    @Test
    fun `new profiles contain no uploaded preferences or permission to enable vehicle functions`() {
        val profile = BleVehicleProfile()
        assertNull(profile.metadata)
        assertNull(profile.calibration)
        assertNull(profile.uploadedConfiguration)
        assertFalse(profile.calibrationRequested)
        assertEquals(BleCalibration.DEFAULT, profile.effectiveCalibration)
        assertEquals(BleCloudSyncState(), profile.cloudState)
        assertEquals(profile, BleVehicleProfile.fromJson(profile.toJson()))
    }

    @Test
    fun `cloud operation status requires its corresponding explicit request`() {
        BleCloudSaveStatus.entries.filter { it != BleCloudSaveStatus.UNSAVED }.forEach { status ->
            assertThrows(IllegalArgumentException::class.java) {
                BleVehicleProfile(cloudState = BleCloudSyncState(configuration = status))
            }
            assertThrows(IllegalArgumentException::class.java) {
                BleVehicleProfile(cloudState = BleCloudSyncState(calibration = status))
            }
        }
        val pendingDeletion = BleVehicleProfile(
            calibration = null, calibrationRequested = true,
            cloudState = BleCloudSyncState(calibration = BleCloudSaveStatus.PENDING)
        )
        assertEquals(BleCalibration.DEFAULT, pendingDeletion.effectiveCalibration)
        assertEquals(pendingDeletion, BleVehicleProfile.fromJson(pendingDeletion.toJson()))
    }

    @Test
    fun `interrupted saves recover as failed and preserve all other cloud states and data`() {
        for (configurationStatus in BleCloudSaveStatus.entries) {
            for (calibrationStatus in BleCloudSaveStatus.entries) {
                val original = profile().copy(cloudState = BleCloudSyncState(configurationStatus, calibrationStatus))
                val expected = original.copy(cloudState = BleCloudSyncState(
                    if (configurationStatus == BleCloudSaveStatus.SAVING) BleCloudSaveStatus.FAILED else configurationStatus,
                    if (calibrationStatus == BleCloudSaveStatus.SAVING) BleCloudSaveStatus.FAILED else calibrationStatus
                ))
                assertEquals(expected, original.interrupted())
                assertEquals(expected, BleVehicleProfile.fromJson(original.toJson()))
                assertEquals(calibration, expected.effectiveCalibration)
                assertEquals(configuration, expected.uploadedConfiguration)
                assertEquals(metadata, expected.metadata)
            }
        }
    }

    @Test
    fun `metadata freshness has an inclusive twenty four hour boundary and rejects future clocks`() {
        assertNull(BleVehicleProfile.freshMetadata(null, now))
        assertEquals(metadata, BleVehicleProfile.freshMetadata(metadata, now))
        val deadline = now + BleVehicleProfile.METADATA_MAX_AGE_MILLIS
        assertEquals(metadata, BleVehicleProfile.freshMetadata(metadata, deadline))
        assertNull(BleVehicleProfile.freshMetadata(metadata, deadline + 1))
        assertNull(BleVehicleProfile.freshMetadata(metadata, now - 1))
        assertNull(BleVehicleProfile.freshMetadata(metadata, -1))
        assertNull(BleVehicleProfile.freshMetadata(metadata, Long.MIN_VALUE))
        assertNull(BleVehicleProfile.freshMetadata(metadata, Long.MAX_VALUE))
        assertEquals(metadata, profile().metadata)
    }

    @Test
    fun `strict decoding rejects missing wrong typed and contradictory fields`() {
        val mutations: List<(JSONObject) -> Unit> = listOf(
            { it.put("schema", 2) },
            { it.put("schema", "1") },
            { it.put("schema", 1.0) },
            { it.remove("metadata") },
            { it.put("metadata", "not-an-object") },
            { it.getJSONObject("metadata").put("address", 1) },
            { it.getJSONObject("metadata").put("address", "00:00:00:00:00:00") },
            { it.getJSONObject("metadata").put("version", JSONObject.NULL) },
            { it.getJSONObject("metadata").put("fetchedAt", "1800000000000") },
            { it.getJSONObject("metadata").put("fetchedAt", now.toDouble()) },
            { it.getJSONObject("metadata").put("fetchedAt", -1) },
            { it.getJSONObject("metadata").remove("fetchedAt") },
            { it.remove("calibration") },
            { it.put("calibration", JSONObject()) },
            { it.put("calibration", "256;2.00;08;16") },
            { it.put("calibration", "56;2.00;08;16;1;1") },
            { it.remove("configuration") },
            { it.put("configuration", "not-an-object") },
            { it.put("configuration", JSONObject.NULL) },
            { it.getJSONObject("configuration").put("enabled", "true") },
            { it.getJSONObject("configuration").put("autoUnlock", 1) },
            { it.getJSONObject("configuration").remove("autoLock") },
            { it.put("calibrationRequested", "true") },
            { it.put("calibrationRequested", false) },
            { it.put("configurationStatus", "saved") },
            { it.put("configurationStatus", 3) },
            { it.remove("calibrationStatus") }
        )
        mutations.forEach { mutate ->
            assertThrows(IllegalArgumentException::class.java) {
                BleVehicleProfile.fromJson(profile().toJson().also(mutate))
            }
        }
    }

    @Test
    fun `encrypted storage fake round trips explicit profiles without identity in keys or ciphertext`() {
        val storage = EncryptedMemoryStorage()
        val store = BleVehicleProfileStore(storage)
        val profile = profile()
        assertTrue(store.save(identity, profile))
        assertEquals(profile, store.load(identity))
        assertEquals(1, storage.raw.size)
        assertTrue(storage.raw.values.single().startsWith("enc:v1:"))
        val exposed = listOf(storage.raw.keys.single(), storage.raw.values.single(), profile.toString())
        for (value in exposed) {
            listOf(identity.accountId, identity.vin, identity.deviceId, metadata.address)
                .forEach { assertFalse(value.contains(it)) }
        }
    }

    @Test
    fun `account vehicle and device scopes stay isolated while new sessions retain preferences`() {
        val storage = EncryptedMemoryStorage()
        val store = BleVehicleProfileStore(storage)
        val scopes = listOf(
            identity,
            identity.copy(accountId = "other-account"),
            identity.copy(vin = "LTEST000000000002"),
            identity.copy(deviceId = "other-device")
        )
        scopes.forEachIndexed { index, scope ->
            val expected = profile().copy(calibration = calibration.copy(unlockCalibration = 10 + index))
            assertTrue(store.save(scope, expected))
        }
        assertEquals(scopes.size, storage.raw.size)
        scopes.forEachIndexed { index, scope ->
            assertEquals(10 + index, store.load(scope).calibration?.unlockCalibration)
        }
        assertEquals(store.load(identity), store.load(identity.copy(generation = 8)))
        assertEquals(BleVehicleProfileStore.key(identity), BleVehicleProfileStore.key(identity.copy(generation = 8)))
        assertEquals(BleVehicleProfile(), store.load(identity.copy(deviceId = "missing-device")))
        assertNotEquals(BleVehicleProfileStore.key(identity), BleVehicleProfileStore.key(scopes.last()))
    }

    @Test
    fun `swapped encrypted records cannot cross any persisted identity boundary`() {
        val storage = EncryptedMemoryStorage()
        val store = BleVehicleProfileStore(storage)
        assertTrue(store.save(identity, profile()))
        val ciphertext = storage.raw.getValue(BleVehicleProfileStore.key(identity))
        for (other in listOf(identity.copy(accountId = "other-account"),
            identity.copy(vin = "LTEST000000000002"), identity.copy(deviceId = "other-device"))) {
            storage.raw[BleVehicleProfileStore.key(other)] = ciphertext
            assertEquals(BleVehicleProfile(), store.load(other))
        }
        assertEquals(profile(), store.load(identity))
    }

    @Test
    fun `corruption decryption errors and oversized or trailing documents recover to an unsaved profile`() {
        val storage = EncryptedMemoryStorage()
        val store = BleVehicleProfileStore(storage)
        val key = BleVehicleProfileStore.key(identity)
        listOf("not-json", "{}", "[]", " ".repeat(8_193), "{} trailing").forEach { text ->
            storage.write(mapOf(key to text))
            assertEquals(BleVehicleProfile(), store.load(identity))
        }
        val malformed = profile().toJson().apply {
            put("account", identity.accountId)
            put("vin", identity.vin)
            put("device", identity.deviceId)
            put("metadata", true)
        }
        storage.write(mapOf(key to malformed.toString()))
        assertEquals(BleVehicleProfile(), store.load(identity))
        listOf("plaintext-is-not-encrypted", "enc:v1:invalid-base64").forEach { value ->
            storage.raw[key] = value
            assertEquals(BleVehicleProfile(), store.load(identity))
        }
        assertTrue(store.save(identity, profile()))
        val ciphertext = storage.raw.getValue(key)
        val corrupted = Base64.getDecoder().decode(ciphertext.removePrefix("enc:v1:"))
        corrupted[corrupted.lastIndex] = (corrupted.last().toInt() xor 1).toByte()
        storage.raw[key] = "enc:v1:" + Base64.getEncoder().encodeToString(corrupted)
        assertEquals(BleVehicleProfile(), store.load(identity))
        storage.failReads = true
        assertEquals(BleVehicleProfile(), store.load(identity))
    }

    @Test
    fun `failed saves do not replace the previous profile and invalid scopes cannot access storage`() {
        val storage = EncryptedMemoryStorage()
        val store = BleVehicleProfileStore(storage)
        assertTrue(store.save(identity, profile()))
        storage.rejectWrites = true
        assertFalse(store.save(identity, BleVehicleProfile()))
        assertEquals(profile(), store.load(identity))
        storage.rejectWrites = false
        listOf(
            identity.copy(accountId = ""), identity.copy(accountId = "other\u0000account"),
            identity.copy(vin = "short"), identity.copy(deviceId = ""),
            identity.copy(deviceId = "x".repeat(257)), identity.copy(deviceId = "test\nother")
        ).forEach { invalid ->
            assertFalse(store.save(invalid, profile()))
            assertEquals(BleVehicleProfile(), store.load(invalid))
        }
        assertEquals(1, storage.raw.size)
    }

    @Test
    fun `cloud saved preferences neither authorize nor acknowledge an independent managed vehicle binding`() {
        val storage = EncryptedMemoryStorage()
        val managed = BleManagedKeyStore(storage)
        val profiles = BleVehicleProfileStore(storage)
        val binding = BleManagedKey(
            identity.accountId, identity.vin,
            BleNearbyDevice(metadata.address, "Synthetic vehicle", -55, 9), "ab".repeat(32),
            deviceId = identity.deviceId
        )
        assertTrue(managed.save(binding))
        assertTrue(profiles.save(identity, profile()))
        val restored = requireNotNull(managed.load(identity.accountId, identity.vin, identity.generation, identity.deviceId))
        assertEquals(binding, restored)
        assertFalse(restored.requested)
        assertFalse(restored.desired.enabled)
        assertFalse(restored.needsBackground)
        assertNull(restored.applied)
        assertNull(restored.confirmedRevision)
        assertEquals(BleCalibration.DEFAULT, restored.desired.calibration)
        assertEquals(BleCloudSaveStatus.SAVED, profiles.load(identity).cloudState.configuration)
        assertEquals(calibration, profiles.load(identity).effectiveCalibration)
    }

    // Exercises the storage contract with synthetic encrypted data, not Android Keystore implementation.
    private class EncryptedMemoryStorage : BleManagedKeyStorage {
        val raw = linkedMapOf<String, String>()
        var rejectWrites = false
        var failReads = false
        private val key = SecretKeySpec(ByteArray(16) { (it + 1).toByte() }, "AES")
        private val random = SecureRandom()

        override fun keys(): Set<String> = raw.keys.toSet()

        override fun read(key: String): String? {
            check(!failReads) { "Synthetic storage failure" }
            val stored = raw[key] ?: return null
            if (!stored.startsWith("enc:v1:")) return null
            return runCatching {
                val bytes = Base64.getDecoder().decode(stored.removePrefix("enc:v1:"))
                require(bytes.size >= 28)
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.DECRYPT_MODE, this.key, GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
                String(cipher.doFinal(bytes.copyOfRange(12, bytes.size)), Charsets.UTF_8)
            }.getOrNull()
        }

        override fun write(values: Map<String, String>): Boolean {
            if (rejectWrites) return false
            val encoded = values.mapValues { (_, value) ->
                val iv = ByteArray(12).also(random::nextBytes)
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
                "enc:v1:" + Base64.getEncoder().encodeToString(iv + cipher.doFinal(value.toByteArray(Charsets.UTF_8)))
            }
            raw.putAll(encoded)
            return true
        }

        override fun remove(key: String): Boolean {
            raw.remove(key)
            return true
        }
    }
}
