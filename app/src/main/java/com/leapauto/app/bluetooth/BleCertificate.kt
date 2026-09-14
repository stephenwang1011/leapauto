package com.leapauto.app.bluetooth

import org.json.JSONObject

data class BleKeyCertificate(
    val ecdhPublicKey: String,
    val keyType: Int,
    val passwordCard: String,
    val plainText: String,
    val signResult: String,
    val vin: String
) {
    init {
        require(keyType == 0 || keyType == 1) { "不支持的蓝牙钥匙证书类型" }
        require(passwordCard.length >= 80 && passwordCard.all { it.code <= 127 }) {
            "蓝牙钥匙 passwordCard 必须为至少 80 字节的 ASCII 字符串"
        }
        require(ecdhPublicKey.isNotBlank() && plainText.isNotBlank() && signResult.isNotBlank()) {
            "蓝牙钥匙证书缺少认证字段"
        }
        require(vin.isNotBlank()) { "蓝牙钥匙证书未绑定车辆" }
    }

    override fun toString(): String = "BleKeyCertificate(keyType=$keyType, credentials=redacted)"

    // The serialized value must only be passed to encrypted, account-scoped storage.
    fun toJson(): JSONObject = JSONObject().apply {
        put("ecdhPublicKey", ecdhPublicKey)
        put("keyType", keyType)
        put("passwordCard", passwordCard)
        put("plainText", plainText)
        put("signResult", signResult)
        put("vin", vin)
    }

    companion object {
        fun fromResponse(response: JSONObject, expectedVin: String): BleKeyCertificate {
            require(response.opt("success") != false) { "蓝牙钥匙证书同步未成功" }
            val status = listOf("code", "result", "status").firstNotNullOfOrNull { key ->
                response.opt(key)?.takeUnless { it == JSONObject.NULL }?.toString()
            }
            require(status == null || status == "0" || status == "200") {
                "蓝牙钥匙证书同步未成功，请检查登录状态或稍后重试"
            }
            val node = response.optJSONObject("data")?.optJSONObject("bluetoothKey")
                ?: response.optJSONObject("bluetoothKey")
                ?: response.takeIf { it.has("ecdhPublicKey") && it.has("passwordCard") }
                ?: throw IllegalArgumentException("蓝牙钥匙响应缺少 bluetoothKey")
            return fromJson(node, expectedVin)
        }

        fun fromJson(json: JSONObject, expectedVin: String): BleKeyCertificate {
            require(expectedVin.isNotBlank()) { "请先选择车辆" }
            val responseVin = json.opt("vin")
            require(responseVin == null || responseVin == JSONObject.NULL || responseVin is String) {
                "蓝牙钥匙证书 VIN 格式无效"
            }
            val vin = (responseVin as? String).orEmpty().ifBlank { expectedVin }
            require(vin == expectedVin) { "蓝牙钥匙证书与当前车辆不匹配" }
            val type = when (val value = json.opt("keyType")) {
                null, JSONObject.NULL -> 0
                is Number -> value.toString().toIntOrNull()
                is String -> value.toIntOrNull()
                else -> null
            } ?: throw IllegalArgumentException("蓝牙钥匙证书类型格式无效")
            return BleKeyCertificate(
                ecdhPublicKey = requiredString(json, "ecdhPublicKey"),
                keyType = type,
                passwordCard = requiredString(json, "passwordCard"),
                plainText = requiredString(json, "plainText"),
                signResult = requiredString(json, "signResult"),
                vin = vin
            )
        }

        private fun requiredString(json: JSONObject, key: String): String =
            (json.opt(key) as? String)?.takeIf { it.isNotBlank() }
                ?: throw IllegalArgumentException("蓝牙钥匙证书缺少 $key")
    }
}
