package com.leapauto.app.bluetooth

import java.nio.charset.CharacterCodingException
import javax.crypto.BadPaddingException
import javax.crypto.IllegalBlockSizeException

enum class BleProtocolFailure(val code: Int, val label: String) {
    GENERAL(1000, "蓝牙协议处理失败"),
    CERTIFICATE_FORMAT(1001, "蓝牙证书格式无效"),
    CERTIFICATE_CURVE(1002, "蓝牙证书曲线不匹配"),
    CERTIFICATE_POINT(1003, "蓝牙证书公钥点无效"),
    P256_PUBLIC_KEY(1004, "P-256 公钥无效"),
    KEY_AGREEMENT(1005, "蓝牙会话密钥派生失败"),
    CIPHERTEXT(1006, "蓝牙回包解密失败"),
    FRAME(1007, "蓝牙数据帧无效"),
    AUTHENTICATION(1008, "蓝牙认证参数无效"),
    SESSION(1009, "蓝牙会话不可用"),
    CERTIFICATE_BINDING(1010, "蓝牙钥匙与当前车辆或会话不匹配");

    companion object {
        // Only exact local constants are recognized; provider/server text and causes are never retained.
        fun classify(error: Throwable): BleProtocolFailure {
            if (error is CharacterCodingException) return FRAME
            if (error is BadPaddingException || error is IllegalBlockSizeException) return CIPHERTEXT
            return when (error.message) {
                "SM2 certificate is too large", "Incomplete SM2 certificate PEM",
                "Invalid SM2 certificate Base64", "Invalid SM2 X.509 certificate",
                "Invalid BLE password card" -> CERTIFICATE_FORMAT
                "SM2 certificate must declare the sm2p256v1 curve" -> CERTIFICATE_CURVE
                "Invalid SM2 public key bit string", "Invalid SM2 public key encoding",
                "SM2 public key is not on the curve" -> CERTIFICATE_POINT
                "Invalid BLE P-256 public key", "Invalid BLE P-256 coordinates",
                "BLE P-256 point is not on the curve" -> P256_PUBLIC_KEY
                "Invalid SM2 private scalar", "Invalid SM2 agreement point",
                "Invalid P-256 shared secret" -> KEY_AGREEMENT
                "Invalid SM4 input or key/IV", "Invalid SM4 ciphertext length",
                "SM4 BLE encryption or decryption failed", "Invalid encrypted BLE frame",
                "Empty encrypted BLE frame" -> CIPHERTEXT
                "Only an encrypted BLE frame can be decoded", "BLE notification exceeds the frame limit",
                "BLE frame buffer limit exceeded" -> FRAME
                "Invalid BLE authentication parameters", "Invalid BLE account or device identifier",
                "Incomplete BLE certificate text", "Invalid BLE certificate signature",
                "BLE authentication payload is too large", "Invalid BLE command timestamp" -> AUTHENTICATION
                "BLE session is closed", "Invalid BLE session identifier",
                "P-256 session requires a P-256 certificate", "SM2 session requires an SM2 certificate",
                "Unsupported Bluetooth key certificate type" -> SESSION
                "BLE certificate belongs to a different vehicle",
                "BLE authentication identity changed during the connection",
                "BLE authentication certificate changed during the connection" -> CERTIFICATE_BINDING
                else -> GENERAL
            }
        }
    }
}
