package com.leapauto.app.bluetooth

import android.content.Context
import android.content.SharedPreferences
import com.leapauto.app.SecureValueStore
import java.security.MessageDigest

class BleManagedKeyStore internal constructor(private val storage: BleManagedKeyStorage) {
    constructor(context: Context) : this(EncryptedManagedKeyStorage(context.applicationContext))

    fun load(
        accountId: String,
        vin: String,
        sessionGeneration: Long,
        deviceId: String = ""
    ): BleManagedKey? = synchronized(STORE_LOCK) {
        runCatching {
            val key = storageKey(accountId, vin, deviceId)
            val value = storage.read(key)
            val binding = if (value != null) {
                BleManagedKey.decodeStored(value).takeIf { it.deviceId == deviceId }
            } else if (key in storage.keys()) {
                null
            } else if (deviceId.isNotEmpty()) {
                storage.read(storageKey(accountId, vin))?.let(BleManagedKey::decodeStored)
                    ?.takeIf { it.deviceId.isEmpty() }
            } else null
            binding?.takeIf { it.accountId == accountId && it.vin == vin }
                ?.forSession(sessionGeneration, deviceId)
        }.getOrNull()
    }

    fun save(binding: BleManagedKey): Boolean = synchronized(STORE_LOCK) {
        runCatching {
            storage.write(mapOf(storageKey(binding.accountId, binding.vin, binding.deviceId) to binding.toJson().toString()))
        }.getOrDefault(false)
    }

    fun clear(accountId: String, vin: String, deviceId: String = ""): Boolean = synchronized(STORE_LOCK) {
        runCatching { storage.remove(storageKey(accountId, vin, deviceId)) }.getOrDefault(false)
    }

    /** Local suspension remains pending until the vehicle acknowledges the disabled configuration. */
    fun suspendAll(): Boolean = synchronized(STORE_LOCK) {
        runCatching {
            var complete = true
            val updates = linkedMapOf<String, String>()
            storage.keys().filter { it.startsWith(KEY_PREFIX) }.forEach { key ->
                val binding = runCatching decode@{
                    val value = storage.read(key) ?: return@decode null
                    BleManagedKey.decodeStored(value).takeIf { storageKey(it.accountId, it.vin, it.deviceId) == key }
                }.getOrNull()
                if (binding == null) {
                    complete = false
                } else {
                    updates[key] = binding.suspended().toJson().toString()
                }
            }
            (updates.isEmpty() || storage.write(updates)) && complete
        }.getOrDefault(false)
    }

    companion object {
        private val STORE_LOCK = Any()
        private const val KEY_PREFIX = "ble_managed_"

        internal fun storageKey(accountId: String, vin: String, deviceId: String = ""): String {
            require(BleManagedKey.validAccount(accountId) && BleManagedKey.validVin(vin) &&
                (deviceId.isEmpty() || BleManagedKey.validAccount(deviceId))) {
                "Invalid Bluetooth binding scope"
            }
            val digest = MessageDigest.getInstance("SHA-256")
                .digest(("$accountId\u0000$vin" + if (deviceId.isEmpty()) "" else "\u0000$deviceId")
                    .toByteArray(Charsets.UTF_8))
            return KEY_PREFIX + digest.joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }
        }
    }
}

internal interface BleManagedKeyStorage {
    fun keys(): Set<String>
    fun read(key: String): String?
    fun write(values: Map<String, String>): Boolean
    fun remove(key: String): Boolean
}

private class EncryptedManagedKeyStorage(context: Context) : BleManagedKeyStorage {
    private val prefs: SharedPreferences = context.getSharedPreferences("leap_ble_managed", Context.MODE_PRIVATE)
    private val secureValues = SecureValueStore(context, prefs)

    override fun keys(): Set<String> = prefs.all.keys

    override fun read(key: String): String? {
        // This store has no plaintext legacy format to migrate.
        if (prefs.getString(key, null)?.startsWith("enc:v1:") != true) return null
        return secureValues.getString(key)
    }

    override fun write(values: Map<String, String>): Boolean {
        val editor = prefs.edit()
        values.forEach { (key, value) -> secureValues.putString(editor, key, value) }
        return editor.commit()
    }

    override fun remove(key: String): Boolean = prefs.edit().remove(key).commit()
}
