package com.leapauto.app.bluetooth

import android.content.Context
import android.content.SharedPreferences
import com.leapauto.app.SecureValueStore
import java.security.MessageDigest
import org.json.JSONObject
import org.json.JSONTokener

internal data class BleReconnectCredentialScope(
    val accountId: String,
    val vin: String,
    val deviceId: String,
    val certificateFingerprint: String,
    val compatibilityProfile: BleCompatibilityProfile
) {
    init {
        require(BleManagedKey.validAccount(accountId) && BleManagedKey.validVin(vin) &&
            BleManagedKey.validAccount(deviceId) && FINGERPRINT.matches(certificateFingerprint)) {
            "Invalid BLE reconnect credential scope"
        }
    }

    override fun toString(): String =
        "BleReconnectCredentialScope(profile=${compatibilityProfile.name}, identity=redacted)"

    internal fun digest(): String {
        val material = listOf(
            SCOPE_VERSION,
            accountId,
            vin,
            deviceId,
            certificateFingerprint,
            compatibilityProfile.name
        ).joinToString("\u0000")
        return MessageDigest.getInstance("SHA-256").digest(material.toByteArray(Charsets.UTF_8)).toHex()
    }

    private companion object {
        const val SCOPE_VERSION = "ble-reconnect-v1"
        val FINGERPRINT = Regex("[0-9a-f]{64}")
    }
}

internal class BleReconnectCredentialStore internal constructor(
    private val storage: BleReconnectCredentialStorage
) {
    constructor(context: Context) : this(EncryptedReconnectCredentialStorage(context.applicationContext))

    fun load(scope: BleReconnectCredentialScope): BleReconnectCredential? = synchronized(STORE_LOCK) {
        runCatching {
            storage.read(storageKey(scope))?.let { decode(it, scope) }
        }.getOrNull()
    }

    fun save(scope: BleReconnectCredentialScope, credential: BleReconnectCredential): Boolean =
        synchronized(STORE_LOCK) {
            runCatching {
                storage.write(storageKey(scope), encode(scope, credential))
            }.getOrDefault(false)
        }

    fun clear(scope: BleReconnectCredentialScope): Boolean = synchronized(STORE_LOCK) {
        runCatching { storage.remove(storageKey(scope)) }.getOrDefault(false)
    }

    fun clearAll(): Boolean = synchronized(STORE_LOCK) {
        runCatching { storage.clear() }.getOrDefault(false)
    }

    private fun encode(scope: BleReconnectCredentialScope, credential: BleReconnectCredential): String =
        JSONObject().apply {
            put("schemaVersion", SCHEMA_VERSION)
            put("scopeHash", scope.digest())
            put("credential", credential.hex)
        }.toString()

    private fun decode(value: String, scope: BleReconnectCredentialScope): BleReconnectCredential {
        require(value.length <= MAX_STORED_LENGTH) { "BLE reconnect credential record is too large" }
        val tokens = JSONTokener(value)
        val json = tokens.nextValue() as? JSONObject ?: invalidRecord()
        require(tokens.nextClean() == '\u0000' && json.length() == 3 &&
            json.opt("schemaVersion") == SCHEMA_VERSION && json.opt("scopeHash") == scope.digest()) {
            "Invalid BLE reconnect credential record"
        }
        return BleReconnectCredential.parse(json.opt("credential") as? String ?: invalidRecord())
    }

    companion object {
        private val STORE_LOCK = Any()
        private const val KEY_PREFIX = "ble_reconnect_"
        private const val SCHEMA_VERSION = 1
        private const val MAX_STORED_LENGTH = 2_048

        internal fun storageKey(scope: BleReconnectCredentialScope): String = KEY_PREFIX + scope.digest()

        private fun invalidRecord(): Nothing = throw IllegalArgumentException("Invalid BLE reconnect credential record")
    }
}

internal interface BleReconnectCredentialStorage {
    fun read(key: String): String?
    fun write(key: String, value: String): Boolean
    fun remove(key: String): Boolean
    fun clear(): Boolean
}

private class EncryptedReconnectCredentialStorage(context: Context) : BleReconnectCredentialStorage {
    private val preferences: SharedPreferences =
        context.getSharedPreferences("leap_ble_reconnect", Context.MODE_PRIVATE)
    private val secureValues = SecureValueStore(context, preferences)

    override fun read(key: String): String? {
        // Reconnect credentials have never had a supported plaintext persistence format.
        if (preferences.getString(key, null)?.startsWith("enc:v1:") != true) return null
        return secureValues.getString(key)
    }

    override fun write(key: String, value: String): Boolean {
        val editor = preferences.edit()
        secureValues.putString(editor, key, value)
        return editor.commit()
    }

    override fun remove(key: String): Boolean = preferences.edit().remove(key).commit()

    override fun clear(): Boolean = preferences.edit().clear().commit()
}

private fun ByteArray.toHex(): String = joinToString("") {
    (it.toInt() and 0xFF).toString(16).padStart(2, '0')
}
