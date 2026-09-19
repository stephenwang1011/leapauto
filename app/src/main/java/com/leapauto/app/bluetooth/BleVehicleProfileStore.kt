package com.leapauto.app.bluetooth

import android.content.Context
import com.leapauto.app.SecureValueStore
import java.security.MessageDigest
import org.json.JSONObject
import org.json.JSONTokener

class BleVehicleProfileStore internal constructor(private val storage: BleManagedKeyStorage) {
    constructor(context: Context) : this(EncryptedProfileStorage(context.applicationContext))

    fun load(identity: BleSessionIdentity): BleVehicleProfile = synchronized(LOCK) {
        runCatching {
            val text = storage.read(key(identity)) ?: return@runCatching BleVehicleProfile()
            require(text.length <= 8_192) { "Invalid BLE profile size" }
            val tokens = JSONTokener(text)
            val json = tokens.nextValue() as? JSONObject ?: error("Invalid BLE profile")
            require(tokens.nextClean() == '\u0000') { "Invalid BLE profile data" }
            require(json.opt("account") == identity.accountId && json.opt("vin") == identity.vin &&
                json.opt("device") == identity.deviceId) { "Invalid BLE profile scope" }
            BleVehicleProfile.fromJson(json)
        }.getOrDefault(BleVehicleProfile())
    }

    fun save(identity: BleSessionIdentity, profile: BleVehicleProfile): Boolean = synchronized(LOCK) {
        runCatching {
            val json = profile.toJson().apply {
                put("account", identity.accountId)
                put("vin", identity.vin)
                put("device", identity.deviceId)
            }
            storage.write(mapOf(key(identity) to json.toString()))
        }.getOrDefault(false)
    }

    companion object {
        private val LOCK = Any()

        internal fun key(identity: BleSessionIdentity): String {
            require(BleManagedKey.validAccount(identity.accountId) && BleManagedKey.validVin(identity.vin) &&
                identity.deviceId.isNotBlank() && identity.deviceId.length <= 256 &&
                identity.deviceId.none { it.isISOControl() }) { "Invalid BLE profile scope" }
            return "ble_profile_" + MessageDigest.getInstance("SHA-256")
                .digest("${identity.accountId}\u0000${identity.vin}\u0000${identity.deviceId}".toByteArray())
                .joinToString("") { (it.toInt() and 255).toString(16).padStart(2, '0') }
        }
    }
}

private class EncryptedProfileStorage(context: Context) : BleManagedKeyStorage {
    private val preferences = context.getSharedPreferences("leap_ble_profiles", Context.MODE_PRIVATE)
    private val secure = SecureValueStore(context, preferences)
    override fun keys(): Set<String> = preferences.all.keys
    override fun read(key: String): String? =
        if (preferences.getString(key, null)?.startsWith("enc:v1:") == true) secure.getString(key) else null
    override fun write(values: Map<String, String>): Boolean {
        val editor = preferences.edit()
        values.forEach { (key, value) -> secure.putString(editor, key, value) }
        return editor.commit()
    }
    override fun remove(key: String): Boolean = preferences.edit().remove(key).commit()
}
