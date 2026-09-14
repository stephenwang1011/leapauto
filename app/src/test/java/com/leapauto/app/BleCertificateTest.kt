package com.leapauto.app

import com.leapauto.app.bluetooth.BleKeyCertificate
import okhttp3.FormBody
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.security.MessageDigest

class BleCertificateTest {
    private val vin = "LTEST000000000001"
    private val card = "0123456789".repeat(8)

    private fun certificateJson(): JSONObject = JSONObject().apply {
        put("ecdhPublicKey", "synthetic-public-key")
        put("keyType", 0)
        put("passwordCard", card)
        put("plainText", "synthetic-plain-text")
        put("signResult", "synthetic-sign-result")
        put("vin", vin)
    }

    private fun responseJson(): JSONObject = JSONObject().apply {
        put("code", 200)
        put("data", JSONObject().put("bluetoothKey", certificateJson()))
    }

    private fun session(): Session = Session(
        deviceId = "synthetic-device",
        oldAuth = OldAuth("synthetic-account", "synthetic-token", "", "", 0),
        selectedVin = vin,
        route = RouteData("https://app-gw-global-master.leapmotor.com", "https://appuser.leapmotor.cn")
    )

    @Test
    fun `parses verified envelope and preserves every authentication field`() {
        val certificate = BleKeyCertificate.fromResponse(responseJson(), vin)

        assertEquals(card, certificate.passwordCard)
        assertEquals("synthetic-public-key", certificate.ecdhPublicKey)
        assertEquals("synthetic-plain-text", certificate.plainText)
        assertEquals("synthetic-sign-result", certificate.signResult)
        assertEquals(vin, certificate.vin)
        assertEquals(0, certificate.keyType)
        assertEquals(certificate, BleKeyCertificate.fromJson(certificate.toJson(), vin))
    }

    @Test
    fun `compatible envelopes and omitted VIN retain request vehicle scope`() {
        val node = certificateJson().apply { remove("vin"); remove("keyType") }

        assertEquals(vin, BleKeyCertificate.fromResponse(JSONObject().put("bluetoothKey", node), vin).vin)
        assertEquals(0, BleKeyCertificate.fromResponse(node, vin).keyType)
    }

    @Test
    fun `SM2 certificate is represented without pretending protocol support`() {
        val certificate = BleKeyCertificate.fromJson(certificateJson().put("keyType", 1), vin)

        assertEquals(1, certificate.keyType)
    }

    @Test
    fun `mismatched vehicle and malformed authentication material are rejected`() {
        val mutations: List<(JSONObject) -> Unit> = listOf(
            { it.put("vin", "LTEST000000000002") },
            { it.put("passwordCard", "a".repeat(79)) },
            { it.put("passwordCard", "a".repeat(79) + '\u0080') },
            { it.put("plainText", " ") },
            { it.put("signResult", JSONObject.NULL) },
            { it.put("ecdhPublicKey", 12345) },
            { it.put("keyType", 2) },
            { it.put("keyType", 0.5) },
            { it.put("vin", JSONObject()) }
        )
        mutations.forEach { mutate ->
            val json = certificateJson().also(mutate)
            assertThrows(IllegalArgumentException::class.java) { BleKeyCertificate.fromJson(json, vin) }
        }
        assertThrows(IllegalArgumentException::class.java) {
            BleKeyCertificate.fromJson(certificateJson(), "")
        }
    }

    @Test
    fun `business error takes precedence over certificate data and remains redacted`() {
        val response = responseJson().put("result", 39).apply { remove("code") }
        response.put("message", "secret-server-message")
        val error = assertThrows(IllegalArgumentException::class.java) {
            BleKeyCertificate.fromResponse(response, vin)
        }
        assertFalse(error.message.orEmpty().contains("secret-server-message"))
        assertThrows(IllegalArgumentException::class.java) {
            BleKeyCertificate.fromResponse(responseJson().put("success", false), vin)
        }
        assertThrows(IllegalArgumentException::class.java) {
            BleKeyCertificate.fromResponse(JSONObject().put("code", 200), vin)
        }
    }

    @Test
    fun `certificate string rendering never exposes credentials or vehicle`() {
        val certificate = BleKeyCertificate.fromJson(certificateJson(), vin)
        val text = certificate.toString()

        listOf(card, vin, certificate.ecdhPublicKey, certificate.plainText, certificate.signResult)
            .forEach { assertFalse(text.contains(it)) }
        assertTrue(text.contains("keyType=0"))
    }

    @Test
    fun `request uses center route signed form and token header`() {
        val api = LeapmotorApi(session())
        val request = api.buildBluetoothCertificateRequest()
        val body = request.body as FormBody
        val values = (0 until body.size).associate { body.name(it) to body.value(it) }

        assertEquals("POST", request.method)
        assertEquals("appuser.leapmotor.cn", request.url.host)
        assertEquals(LeapmotorApi.BLUETOOTH_CERTIFICATE_PATH, request.url.encodedPath)
        assertNull(request.url.query)
        assertEquals("application/x-www-form-urlencoded", body.contentType().toString())
        assertEquals(setOf("vin", "timespan", "nonce", "deviceID", "signStr"), values.keys)
        assertEquals(vin, values["vin"])
        assertEquals("synthetic-device", values["deviceID"])
        assertTrue(values.getValue("timespan").toLong() > 1_000_000_000_000L)
        assertEquals("synthetic-token", request.header("XFX-CDN-CROSS-NODE"))
        assertEquals("APP", request.header("C-VERSIONS"))
        assertEquals("Android", request.header("APPPlatform"))
        assertEquals(session().appVersion, request.header("APPVersion"))
        assertEquals(values["deviceID"], request.header("APPImei"))
        val signed = values.filterKeys { it != "signStr" } + ("token" to "synthetic-token")
        val canonical = signed.toSortedMap().values.joinToString("")
        val digest = MessageDigest.getInstance("MD5").digest(canonical.toByteArray())
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }.substring(8, 24)
        assertEquals(digest, values["signStr"])
    }

    @Test
    fun `request refuses missing account context and untrusted route before HTTP`() {
        val invalidSessions = listOf(
            session().apply { oldAuth = null },
            session().apply { oldAuth = oldAuth?.copy(accountId = "") },
            session().apply { selectedVin = "" },
            session().apply { deviceId = "" },
            session().apply { route = null },
            session().apply { route = RouteData("", "http://appuser.leapmotor.cn") },
            session().apply { route = RouteData("", "https://appuser.leapmotor.cn.example.org") },
            session().apply { route = RouteData("", "https://appuser.leapmotor.cn/unexpected") }
        )
        invalidSessions.forEach { invalid ->
            val error = assertThrows(ApiException::class.java) {
                LeapmotorApi(invalid).fetchBluetoothKeyCertificate { throw AssertionError("must not send") }
            }
            assertEquals("ble_certificate", error.stage)
        }
    }

    @Test
    fun `certificate transport excludes diagnostics interceptors and redirects`() {
        val client = LeapmotorApi.bluetoothCertificateHttpClient

        assertTrue(client.interceptors.isEmpty())
        assertTrue(client.networkInterceptors.isEmpty())
        assertFalse(client.followRedirects)
        assertFalse(client.followSslRedirects)
        assertNull(client.cache)
    }

    @Test
    fun `certificate fetch parses success and sanitizes every failure class`() {
        val api = LeapmotorApi(session())
        assertEquals(vin, api.fetchBluetoothKeyCertificate {
            VehicleListRawResponse(200, responseJson().toString())
        }.vin)
        val responses = listOf(
            VehicleListRawResponse(403, "secret-http-response"),
            VehicleListRawResponse(200, "secret-non-json-response"),
            VehicleListRawResponse(200, "{\"code\":39,\"message\":\"secret-business-message\"}")
        )
        responses.forEach { raw ->
            val error = assertThrows(ApiException::class.java) {
                api.fetchBluetoothKeyCertificate { raw }
            }
            assertEquals("ble_certificate", error.stage)
            assertFalse(error.message.orEmpty().contains("secret-"))
            assertNull(error.cause)
        }
        val error = assertThrows(ApiException::class.java) {
            api.fetchBluetoothKeyCertificate { throw IOException("secret-transport-message") }
        }
        assertFalse(error.message.orEmpty().contains("secret-"))
        assertNull(error.cause)
    }
}
