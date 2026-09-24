package com.leapauto.app.bluetooth

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class BleReconnectCredentialStoreTest {
    private val credential = BleReconnectCredential.parse("0123456789abcdef")
    private val baseScope = BleReconnectCredentialScope(
        accountId = "test-account",
        vin = "LTEST000000000001",
        deviceId = "test-device",
        certificateFingerprint = "ab".repeat(32),
        compatibilityProfile = BleCompatibilityProfile.ONE_PAO_V010
    )

    @Test
    fun `credential round trips only inside its complete scope`() {
        val storage = MemoryStorage()
        val store = BleReconnectCredentialStore(storage)
        assertTrue(store.save(baseScope, credential))
        assertEquals(credential, store.load(baseScope))

        listOf(
            baseScope.copy(accountId = "other-account"),
            baseScope.copy(vin = "LTEST000000000002"),
            baseScope.copy(deviceId = "other-device"),
            baseScope.copy(certificateFingerprint = "cd".repeat(32)),
            baseScope.copy(compatibilityProfile = BleCompatibilityProfile.LEGACY)
        ).forEach { assertNull(store.load(it)) }
    }

    @Test
    fun `storage key and scope rendering redact identity and credential material`() {
        val key = BleReconnectCredentialStore.storageKey(baseScope)
        val rendered = baseScope.toString()
        listOf(
            baseScope.accountId,
            baseScope.vin,
            baseScope.deviceId,
            baseScope.certificateFingerprint,
            credential.hex
        ).forEach { secret ->
            assertFalse(key.contains(secret))
            assertFalse(rendered.contains(secret))
        }
        assertTrue(key.startsWith("ble_reconnect_"))
        assertFalse(key == BleReconnectCredentialStore.storageKey(baseScope.copy(deviceId = "other-device")))
    }

    @Test
    fun `swapped or malformed records never cross a scope boundary`() {
        val storage = MemoryStorage()
        val store = BleReconnectCredentialStore(storage)
        val otherScope = baseScope.copy(deviceId = "other-device")
        assertTrue(store.save(baseScope, credential))
        val originalKey = BleReconnectCredentialStore.storageKey(baseScope)
        val otherKey = BleReconnectCredentialStore.storageKey(otherScope)

        storage.values[otherKey] = requireNotNull(storage.values[originalKey])
        assertNull(store.load(otherScope))

        listOf(
            "not-json",
            "{}",
            JSONObject().apply {
                put("schemaVersion", 1)
                put("scopeHash", baseScope.digest())
                put("credential", "xyz")
            }.toString(),
            requireNotNull(storage.values[originalKey]) + " trailing-data",
            " ".repeat(2_049)
        ).forEach {
            storage.values[originalKey] = it
            assertNull(store.load(baseScope))
        }
    }

    @Test
    fun `clear removes only the selected scoped credential`() {
        val storage = MemoryStorage()
        val store = BleReconnectCredentialStore(storage)
        val otherScope = baseScope.copy(deviceId = "other-device")
        assertTrue(store.save(baseScope, credential))
        assertTrue(store.save(otherScope, BleReconnectCredential.parse("aabbccdd")))

        assertTrue(store.clear(baseScope))
        assertNull(store.load(baseScope))
        assertEquals(BleReconnectCredential.parse("aabbccdd"), store.load(otherScope))
    }

    @Test
    fun `clear all removes credentials from every scope on logout`() {
        val storage = MemoryStorage()
        val store = BleReconnectCredentialStore(storage)
        val otherScope = baseScope.copy(deviceId = "other-device")
        assertTrue(store.save(baseScope, credential))
        assertTrue(store.save(otherScope, BleReconnectCredential.parse("aabbccdd")))

        assertTrue(store.clearAll())
        assertNull(store.load(baseScope))
        assertNull(store.load(otherScope))
    }

    @Test
    fun `invalid scope inputs are rejected before persistence`() {
        listOf<() -> Unit>(
            { baseScope.copy(accountId = " bad-account") },
            { baseScope.copy(vin = "SHORT") },
            { baseScope.copy(deviceId = "") },
            { baseScope.copy(certificateFingerprint = "AB".repeat(32)) }
        ).forEach { create ->
            assertThrows(IllegalArgumentException::class.java) { create() }
        }
    }

    @Test
    fun `storage failures are explicit and never fabricate a credential`() {
        val storage = MemoryStorage()
        val store = BleReconnectCredentialStore(storage)
        storage.rejectWrites = true
        assertFalse(store.save(baseScope, credential))
        assertNull(store.load(baseScope))

        storage.rejectWrites = false
        assertTrue(store.save(baseScope, credential))
        storage.failReads = true
        assertNull(store.load(baseScope))
        storage.failReads = false
        storage.rejectRemoves = true
        assertFalse(store.clear(baseScope))
        assertEquals(credential, store.load(baseScope))
    }

    private class MemoryStorage : BleReconnectCredentialStorage {
        val values = linkedMapOf<String, String>()
        var rejectWrites = false
        var rejectRemoves = false
        var rejectClear = false
        var failReads = false

        override fun read(key: String): String? {
            check(!failReads) { "Synthetic read failure" }
            return values[key]
        }

        override fun write(key: String, value: String): Boolean {
            if (rejectWrites) return false
            values[key] = value
            return true
        }

        override fun remove(key: String): Boolean {
            if (rejectRemoves) return false
            values.remove(key)
            return true
        }

        override fun clear(): Boolean {
            if (rejectClear) return false
            values.clear()
            return true
        }
    }
}
