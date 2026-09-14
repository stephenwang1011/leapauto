package com.leapauto.app.bluetooth

import android.content.Context
import android.content.SharedPreferences
import com.leapauto.app.SecureValueStore
import java.security.MessageDigest

class BleManagedKeyStore internal constructor(private val storage: BleManagedKeyStorage) {
    constructor(context: Context) : this(EncryptedManagedKeyStorage(context.applicationContext))

    fun load(accountId: String, vin: String, sessionGeneration: Long): BleManagedKey? = synchronized(STORE_LOCK) {
        runCatching {
            val key = storageKey(accountId, vin)
            val binding = storage.read(key)?.let(BleManagedKey::decodeStored) ?: return@runCatching null
            binding.takeIf { it.accountId == accountId && it.vin == vin }?.forSession(sessionGeneration)
        }.getOrNull()
    }

    fun save(binding: BleManagedKey): Boolean = synchronized(STORE_LOCK) {
        runCatching {
            storage.write(mapOf(storageKey(binding.accountId, binding.vin) to binding.toJson().toString()))
        }.getOrDefault(false)
    }

    fun clear(accountId: String, vin: String): Boolean = synchronized(STORE_LOCK) {
        runCatching { storage.remove(storageKey(accountId, vin)) }.getOrDefault(false)
    }

    /** Local suspension remains pending until the vehicle acknowledges the disabled configuration. */
    fun suspendAll(): Boolean = synchronized(STORE_LOCK) {
        runCatching {
            var complete = true
            val updates = linkedMapOf<String, String>()
            storage.keys().filter { it.startsWith(KEY_PREFIX) }.forEach { key ->
                val binding = runCatching decode@{
                    val value = storage.read(key) ?: return@decode null
                    BleManagedKey.decodeStored(value).takeIf { storageKey(it.accountId, it.vin) == key }
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

        internal fun storageKey(accountId: String, vin: String): String {
            require(BleManagedKey.validAccount(accountId) && BleManagedKey.validVin(vin)) {
                "Invalid Bluetooth binding scope"
            }
            val digest = MessageDigest.getInstance("SHA-256")
                .digest("$accountId\u0000$vin".toByteArray(Charsets.UTF_8))
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
