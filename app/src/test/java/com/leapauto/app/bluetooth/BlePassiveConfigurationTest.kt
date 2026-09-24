package com.leapauto.app.bluetooth

import java.util.Base64
import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class BlePassiveConfigurationTest {
    private val timestamp = 1_800_000_000L
    private val vectors = JSONObject(requireNotNull(javaClass.getResourceAsStream("/bluetooth/passive-vectors.json"))
        .bufferedReader().use { it.readText() })

    @Test
    fun customCalibrationIsSharedByAuthenticationAndConfigurationWithoutChangingFlags() {
        val calibration = BleCalibration(61, 175, 12, 24)
        val configuration = BlePassiveConfiguration(
            enabled = true, autoUnlock = true, autoLock = true, calibration = calibration
        )
        for (minor in listOf(0, 8, 9, 10, 255)) {
            val flags = if (minor < 9) byteArrayOf(1, 1) else byteArrayOf(1, 1, 1, 0)
            assertArrayEquals(byteArrayOf(61, 175.toByte(), 0, 12, 24) + flags, configuration.encoded(minor))
            assertEquals("61;1.75;12;24;${flags.joinToString(";")};", configuration.authenticationFields(minor))
            val disabled = configuration.copy(enabled = false)
            assertArrayEquals(calibration.encoded() + ByteArray(flags.size), disabled.encoded(minor))
            assertEquals(calibration, disabled.calibration)
            assertFalse(disabled.needsBackground)
        }
    }

    @Test
    fun newSettingsAreOffAndBackgroundFollowsOnlyTheMasterSwitch() {
        assertEquals(BlePassiveConfiguration(false, false, false, false), BlePassiveConfiguration())
        assertFalse(BlePassiveConfiguration().needsBackground)
        assertEquals(BlePassiveConfiguration(true, false, false, false), BlePassiveConfiguration.MANUAL)
        for (mask in 0..15) assertEquals(mask and 1 != 0, configuration(mask).needsBackground)
    }

    @Test
    fun minorNineKeepsUnlockLockAndButtonIndependentUnderMasterGate() {
        val flags = listOf("0000", "1000", "0000", "1010", "0000", "1100", "0000", "1110",
            "0000", "1001", "0000", "1011", "0000", "1101", "0000", "1111")
        for (mask in 0..15) {
            val expected = flags[mask]
            val configuration = configuration(mask)
            for (minor in listOf(9, 10, 255)) {
                assertArrayEquals(hex("38c8000810") + expected.map { it.digitToInt().toByte() }.toByteArray(),
                    configuration.encoded(minor))
                assertEquals("56;2.00;08;16;${expected.toList().joinToString(";")};",
                    configuration.authenticationFields(minor))
            }
        }
    }

    @Test
    fun legacyMinorCombinesMasterAndUnlockWhileKeepingAutomaticLockIndependent() {
        val flags = listOf("00", "00", "00", "10", "00", "01", "00", "11")
        for (mask in 0..7) {
            for (minor in listOf(0, 7, 8)) {
                assertArrayEquals(hex("38c8000810") + flags[mask].map { it.digitToInt().toByte() }.toByteArray(),
                    configuration(mask).encoded(minor))
                assertEquals("56;2.00;08;16;${flags[mask].toList().joinToString(";")};",
                    configuration(mask).authenticationFields(minor))
            }
        }
    }

    @Test
    fun onePaoAuthenticationAlwaysUsesFourFlagsAndSeparatesButtonCapability() {
        val calibration = BleCalibration(61, 175, 12, 24)
        val disabledButCapable = BlePassiveConfiguration(
            enabled = false, buttonEnabled = true, calibration = calibration
        )
        val enabled = BlePassiveConfiguration(
            enabled = true, autoUnlock = true, autoLock = true, buttonEnabled = true,
            calibration = calibration
        )
        for (minor in listOf(0, 8, 9, 255)) {
            assertEquals("61;1.75;12;24;0;0;0;0;", disabledButCapable.authenticationFields(
                minor, BleAuthenticationTextProfile.ONE_PAO_V010))
            assertEquals("61;1.75;12;24;1;1;1;0;", enabled.authenticationFields(
                minor, BleAuthenticationTextProfile.ONE_PAO_V010))
        }
        assertArrayEquals(byteArrayOf(61, 175.toByte(), 0, 12, 24, 0, 0, 0, 0),
            disabledButCapable.reconnectFields())
        assertArrayEquals(byteArrayOf(61, 175.toByte(), 0, 12, 24, 0, 0, 0, 1),
            disabledButCapable.reconnectFields(supportsButton = true))
    }

    @Test
    fun legacyVehiclesRejectEnabledButtonButCanAlwaysReceiveMasterOff() {
        for (keyType in listOf(0, 1)) {
            session(keyType).use { session ->
                for (mask in listOf(9, 11, 13, 15)) {
                    assertThrows(IllegalArgumentException::class.java) {
                        session.buildAuthentication(certificate(keyType), "account", "device", 8, timestamp, configuration(mask))
                    }
                    assertThrows(IllegalArgumentException::class.java) {
                        session.buildConfiguration(configuration(mask), 8, timestamp)
                    }
                    assertArrayEquals(session.buildConfiguration(BlePassiveConfiguration(), 8, timestamp),
                        session.buildConfiguration(configuration(mask).copy(enabled = false), 8, timestamp))
                }
            }
        }
    }

    @Test
    fun aesAndSm4AuthenticationAndCommandThreeMatchIndependentOpenSslVectors() {
        forEachAlgorithm { keyType, algorithm ->
            session(keyType).use { session ->
                val configurations = algorithm.getJSONArray("configurations")
                for (index in 0 until configurations.length()) {
                    val vector = configurations.getJSONObject(index)
                    val name = vector.getString("name")
                    val minor = name.last().digitToInt()
                    val configuration = namedConfiguration(name)
                    assertArrayEquals(name, hex(vector.getString("command")),
                        session.buildConfiguration(configuration, minor, timestamp))
                    assertArrayEquals(name, Base64.getDecoder().decode(vector.getString("authentication")),
                        session.buildAuthentication(certificate(keyType), "test-account", "test-device", minor,
                            timestamp, configuration))
                }
            }
        }
    }

    @Test
    fun onlyExactCommandThreeSuccessRepliesConfirmConfiguration() {
        forEachAlgorithm { keyType, algorithm ->
            session(keyType).use { session ->
                val replies = algorithm.getJSONArray("replies")
                for (index in 0 until replies.length()) {
                    val vector = replies.getJSONObject(index)
                    val frame = BleFrameDecoder().append(hex(vector.getString("frame"))).single()
                    val response = session.decodeResponse(frame) as BleResponse.CommandResult
                    assertEquals(vector.getString("text"), index < 2, response.confirmsConfiguration())
                }
            }
        }
        for (identifier in listOf("", "03", "3 ", " 3", "1", "2")) {
            assertFalse(BleResponse.CommandResult(identifier, "0").confirmsConfiguration())
        }
        for (result in listOf(null, "", "000", "0 ", " 00", "1", "-0", "+0", "0.0")) {
            assertFalse(BleResponse.CommandResult("3", result).confirmsConfiguration())
        }
        assertTrue(BleResponse.CommandResult("3", "0").confirmsConfiguration())
        assertTrue(BleResponse.CommandResult("3", "00").confirmsConfiguration())
    }

    @Test
    fun invalidParametersAndClosedSessionsCannotBuildConfiguration() {
        for (keyType in listOf(0, 1)) {
            val session = session(keyType)
            for (minor in listOf(-1, 256)) {
                assertThrows(IllegalArgumentException::class.java) {
                    session.buildConfiguration(BlePassiveConfiguration(), minor, timestamp)
                }
            }
            assertThrows(IllegalArgumentException::class.java) {
                session.buildConfiguration(BlePassiveConfiguration(), 9, -1)
            }
            session.close()
            assertThrows(IllegalStateException::class.java) {
                session.buildConfiguration(BlePassiveConfiguration(), 9, timestamp)
            }
        }
    }

    @Test
    fun certificateFingerprintIsStableLengthPrefixedAndCoversAllCredentialFields() {
        val certificate = certificate(0)
        val expected = vectors.getString("fingerprint")
        assertEquals(expected, BleKeyProtocol.certificateFingerprint(certificate))
        assertEquals(expected, BleKeyProtocol.certificateFingerprint(certificate.copy()))
        for (changed in listOf(certificate.copy(ecdhPublicKey = "another-key"), certificate.copy(keyType = 1),
            certificate.copy(passwordCard = "f".repeat(80)), certificate.copy(plainText = "other;text"),
            certificate.copy(signResult = "YWJj"), certificate.copy(vin = "TESTVIN0000000002"),
            certificate.copy(passwordCard = certificate.passwordCard + "v", plainText = certificate.plainText.drop(1)))) {
            assertNotEquals(expected, BleKeyProtocol.certificateFingerprint(changed))
        }
    }

    private fun configuration(mask: Int): BlePassiveConfiguration = BlePassiveConfiguration(
        enabled = mask and 1 != 0, autoUnlock = mask and 2 != 0,
        autoLock = mask and 4 != 0, buttonEnabled = mask and 8 != 0
    )

    private fun namedConfiguration(name: String): BlePassiveConfiguration = when (name) {
        "off8", "off9" -> BlePassiveConfiguration()
        "auto8" -> BlePassiveConfiguration(enabled = true, autoUnlock = true, autoLock = true)
        "manual9" -> BlePassiveConfiguration.MANUAL
        "button9" -> BlePassiveConfiguration(enabled = true, buttonEnabled = true)
        "all9" -> BlePassiveConfiguration(true, true, true, true)
        else -> error("Unknown offline vector")
    }

    private fun forEachAlgorithm(block: (Int, JSONObject) -> Unit) {
        val algorithms = vectors.getJSONArray("algorithms")
        for (index in 0 until algorithms.length()) block(index, algorithms.getJSONObject(index))
    }

    private fun certificate(keyType: Int): BleKeyCertificate = BleKeyCertificate(
        ecdhPublicKey = "synthetic-public-key", keyType = keyType,
        passwordCard = "0123456789abcdef".repeat(5),
        plainText = "v1;old-account;old-device;TESTVIN0000000001;1700000000;1900000000",
        signResult = Base64.getEncoder().encodeToString(ByteArray(16) { (it + 1).toByte() }),
        vin = "TESTVIN0000000001"
    )

    // Fixed framing inputs bypass key agreement, which is covered separately by P-256 and SM2 vectors.
    private fun session(keyType: Int): BleKeySession = BleKeySession(
        0x78563412,
        hex("046b17d1f2e12c4247f8bce6e563a440f277037d812deb33a0f4a13945d898c296" +
            "4fe342e2fe1a7f9b8ee7eb4a7c0f9e162bce33576b315ececbb6406837bf51f5"),
        "0123456789abcdef".toByteArray(), "fedcba9876543210".toByteArray(),
        hex(BleKeyProtocol.certificateFingerprint(certificate(keyType))), keyType
    )

    private fun hex(value: String): ByteArray = value.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
}
