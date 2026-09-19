package com.leapauto.app

import com.leapauto.app.bluetooth.BleCloudApiModels
import com.leapauto.app.bluetooth.BleCloudRequestScope
import com.leapauto.app.bluetooth.BlePassiveConfiguration
import com.leapauto.app.bluetooth.BleVehicleMetadata
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.security.MessageDigest
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class BleCloudApiTest {
    private val vin = "LTEST000000000001"
    private val address = "02:11:22:33:AA:BB"
    private val signingKey = "synthetic-gateway-signing-key".toByteArray()

    private fun session(generation: Long = 0L): Session = Session(
        deviceId = "synthetic-device",
        oldAuth = OldAuth("synthetic-account", "synthetic-old-token", "", "", 0),
        newAuth = NewAuth("synthetic-account", "synthetic-access-token", "",
            Base64.getEncoder().encodeToString(signingKey), 0),
        selectedVin = vin,
        route = RouteData("https://unused.leapmotor.com", "https://unused.leapmotor.cn"),
        generation = generation
    )

    private fun metadataResponse(): JSONObject = JSONObject()
        .put("code", 0).put("result", 0)
        .put("data", JSONObject().put("vin", vin).put("config", JSONObject()
            .put("4", JSONObject().put("mac", "02112233aabb").put("version", "2.0").put("updateTime", 1))))

    @Test
    fun metadataNormalizesCompactMacAndPreservesVersionWithoutProtocolConversion() {
        val metadata = requireNotNull(BleVehicleMetadata.fromResponse(metadataResponse(), vin, 100L))
        assertEquals(address, metadata.address)
        assertEquals("2.0", metadata.controllerVersion)
        assertEquals(100L, metadata.fetchedAtMillis)
        assertEquals(address, BleVehicleMetadata.normalizeAddress(" 02:11:22:33:aa:bb "))
        assertEquals("BleVehicleMetadata(redacted)", metadata.toString())
        assertFalse(metadata.toString().contains(address))
    }

    @Test
    fun metadataMissingBluetoothConfigurationIsUnavailableWithoutInventingTarget() {
        val response = metadataResponse()
        response.getJSONObject("data").getJSONObject("config").remove("4")
        assertNull(BleVehicleMetadata.fromResponse(response, vin, 100L))
        response.getJSONObject("data").remove("config")
        assertNull(BleVehicleMetadata.fromResponse(response, vin, 100L))
        response.getJSONObject("data").put("config", JSONObject.NULL)
        assertNull(BleVehicleMetadata.fromResponse(response, vin, 100L))
    }

    @Test
    fun metadataRejectsMismatchedVehicleMalformedTypesAndInvalidAddress() {
        val mutations: List<(JSONObject) -> Unit> = listOf(
            { it.put("data", JSONObject.NULL) },
            { it.getJSONObject("data").put("vin", "LTEST000000000002") },
            { it.getJSONObject("data").put("vin", JSONObject.NULL) },
            { it.getJSONObject("data").put("config", "secret-response") },
            { it.getJSONObject("data").getJSONObject("config").put("4", "secret-response") },
            { it.getJSONObject("data").getJSONObject("config").getJSONObject("4").put("mac", 123) },
            { it.getJSONObject("data").getJSONObject("config").getJSONObject("4").put("mac", "000000000000") },
            { it.getJSONObject("data").getJSONObject("config").getJSONObject("4").put("version", 2) },
            { it.getJSONObject("data").getJSONObject("config").getJSONObject("4").put("version", "") },
            { it.getJSONObject("data").getJSONObject("config").getJSONObject("4").put("version", "x\ny") },
            { it.getJSONObject("data").getJSONObject("config").getJSONObject("4").put("version", "v".repeat(65)) }
        )
        mutations.forEach { mutate ->
            val error = assertThrows(IllegalArgumentException::class.java) {
                BleVehicleMetadata.fromResponse(metadataResponse().also(mutate), vin, 100L)
            }
            assertFalse(error.message.orEmpty().contains("secret-response"))
        }
        listOf("", "FFFFFFFFFFFF", "00:00:00:00:00:00", "02-11-22-33-AA-BB", "02112233AABBGG", address + "\nsecret")
            .forEach { assertNull(BleVehicleMetadata.normalizeAddress(it)) }
        assertThrows(IllegalArgumentException::class.java) { BleVehicleMetadata(address, "2.0", -1L) }
    }

    @Test
    fun successfulEnvelopeRequiresExplicitZeroAndRejectsConflictingIndicators() {
        BleCloudApiModels.requireSuccess(JSONObject("{\"code\":0}"))
        BleCloudApiModels.requireSuccess(JSONObject("{\"code\":\"0\",\"result\":\"0\",\"success\":true}"))
        listOf("{}", "{\"result\":0}", "{\"code\":200}", "{\"code\":0.5}", "{\"code\":false}",
            "{\"code\":null}", "{\"code\":0,\"result\":9}", "{\"code\":0,\"result\":null}",
            "{\"code\":0,\"success\":false}", "{\"code\":0,\"success\":\"true\"}")
            .forEach { json ->
                assertThrows(IllegalArgumentException::class.java) { BleCloudApiModels.requireSuccess(JSONObject(json)) }
            }
    }

    @Test
    fun metadataRequestUsesObservedGatewayRouteAndOnlyThreeQueryParameters() {
        val source = session().apply { oldAuth = null }
        val request = LeapmotorApi(source).buildBluetoothVehicleMetadataRequest()
        assertEquals("GET", request.method)
        assertEquals("appgateway.leapmotor.com", request.url.host)
        assertEquals(LeapmotorApi.BLUETOOTH_VEHICLE_METADATA_PATH, request.url.encodedPath)
        assertEquals(setOf("appVersion", "osType", "vin"), request.url.queryParameterNames)
        assertEquals(source.appVersion, request.url.queryParameter("appVersion"))
        assertEquals("Android", request.url.queryParameter("osType"))
        assertEquals(vin, request.url.queryParameter("vin"))
        assertNull(request.body)
        assertNull(request.header("XFX-CDN-CROSS-NODE"))
        assertNull(request.header("APPImei"))
        assertNull(request.header("APPVersion"))
        assertEquals("synthetic-access-token", request.header("token"))
        verifyGatewaySignature(request, request.url.queryParameterNames.associateWith {
            requireNotNull(request.url.queryParameter(it))
        })
    }

    @Test
    fun cloudConfigurationPreservesFourPreferencesIndependentlyOfEffectiveVehicleFlags() {
        val preferences = BlePassiveConfiguration(enabled = false, autoUnlock = true, autoLock = false, buttonEnabled = true)
        val request = LeapmotorApi(session()).buildBluetoothConfigurationRequest(preferences)
        val fields = formFields(request)
        assertEquals("POST", request.method)
        assertEquals("app-gw-global-master.leapmotor.com", request.url.host)
        assertEquals(LeapmotorApi.BLUETOOTH_CONFIGURATION_PATH, request.url.encodedPath)
        assertEquals(setOf("vin", "conf", "timespan", "nonce", "deviceID", "signStr"), fields.keys)
        val conf = JSONObject(fields.getValue("conf"))
        assertEquals(setOf("bleKeySwitch", "bleKeyUnlock", "bleKeyLock", "bleKeyBtn"), conf.keySet())
        assertEquals(false, conf.get("bleKeySwitch"))
        assertEquals(true, conf.get("bleKeyUnlock"))
        assertEquals(false, conf.get("bleKeyLock"))
        assertEquals(true, conf.get("bleKeyBtn"))
        verifyUploadIdentityAndSignatures(request, fields)
        val disabled = JSONObject(formFields(LeapmotorApi(session()).buildBluetoothConfigurationRequest(
            BlePassiveConfiguration())).getValue("conf"))
        disabled.keySet().forEach { assertEquals(false, disabled.get(it)) }
    }

    @Test
    fun calibrationAddAndDeleteUseDistinctObservedFormsWithBothSignatures() {
        val api = LeapmotorApi(session())
        val model = "Synthetic Model + Test"
        val params = "69;1.00;04;21"
        val add = api.buildBluetoothCalibrationRequest(params, model)
        val addFields = formFields(add)
        assertEquals("app-gw-global-master.leapmotor.com", add.url.host)
        assertEquals(LeapmotorApi.BLUETOOTH_CALIBRATION_PATH, add.url.encodedPath)
        assertEquals(setOf("vin", "anchorType", "model", "operate", "params", "timespan", "nonce", "deviceID", "signStr"), addFields.keys)
        assertEquals("add", addFields["operate"])
        assertEquals("0", addFields["anchorType"])
        assertEquals(model, addFields["model"])
        assertEquals(params, addFields["params"])
        verifyUploadIdentityAndSignatures(add, addFields)
        val delete = api.buildBluetoothCalibrationRequest(null, model)
        val deleteFields = formFields(delete)
        assertEquals("delete", deleteFields["operate"])
        assertEquals("", deleteFields["params"])
        assertEquals(addFields.keys, deleteFields.keys)
        verifyUploadIdentityAndSignatures(delete, deleteFields)
    }

    @Test
    fun invalidCalibrationCannotReachExecutor() {
        val api = LeapmotorApi(session())
        listOf("", "69;1;04;21", "69;1.00;04;21;", "256;1.00;04;21", "69;655.36;04;21", "69;1.00;04;999")
            .forEach { invalid ->
                assertThrows(ApiException::class.java) {
                    api.uploadBluetoothCalibration(invalid, "Synthetic") { throw AssertionError("must not send") }
                }
            }
        listOf("", " ", "secret\nmodel", "x".repeat(129)).forEach { invalid ->
            assertThrows(ApiException::class.java) {
                api.uploadBluetoothCalibration(null, invalid) { throw AssertionError("must not send") }
            }
        }
    }

    @Test
    fun gatewaySignatureAcceptsAsciiWhitespaceBase64ButRejectsArbitraryInvalidCharacters() {
        val source = session()
        val wrapped = source.newAuth?.signKeyBase64.orEmpty().chunked(8).joinToString(" \t\r\n")
        source.newAuth = source.newAuth?.copy(signKeyBase64 = wrapped)
        val request = LeapmotorApi(source).buildBluetoothConfigurationRequest(BlePassiveConfiguration())
        verifyUploadIdentityAndSignatures(request, formFields(request))
        source.newAuth = source.newAuth?.copy(signKeyBase64 = wrapped + "!")
        val error = assertThrows(ApiException::class.java) {
            LeapmotorApi(source).buildBluetoothConfigurationRequest(BlePassiveConfiguration())
        }
        assertNull(error.cause)
    }

    @Test
    fun missingIdentityIsRejectedBeforeRequestExecution() {
        val invalidSessions = listOf(
            session().apply { selectedVin = "" },
            session().apply { deviceId = "" },
            session().apply { newAuth = null },
            session().apply { newAuth = newAuth?.copy(accountId = "") },
            session().apply { newAuth = newAuth?.copy(accessToken = "") },
            session().apply { newAuth = newAuth?.copy(signKeyBase64 = "") }
        )
        invalidSessions.forEach { invalid ->
            val api = LeapmotorApi(invalid)
            assertThrows(ApiException::class.java) {
                api.getBluetoothVehicleMetadata(execute = { throw AssertionError("must not send") })
            }
            assertThrows(ApiException::class.java) {
                api.uploadBluetoothConfiguration(BlePassiveConfiguration()) { throw AssertionError("must not send") }
            }
        }
        assertThrows(ApiException::class.java) {
            LeapmotorApi(session().apply { oldAuth = null })
                .uploadBluetoothConfiguration(BlePassiveConfiguration()) { throw AssertionError("must not send") }
        }
    }

    @Test
    fun metadataFetchParsesScopedSuccessAndReturnsNullForAbsentMetadata() {
        val api = LeapmotorApi(session())
        assertEquals(BleVehicleMetadata(address, "2.0", 100L), api.getBluetoothVehicleMetadata(
            execute = { VehicleListRawResponse(200, metadataResponse().toString()) }, fetchedAtMillis = 100L))
        assertNull(api.getBluetoothVehicleMetadata(execute = {
            VehicleListRawResponse(200, "{\"code\":0,\"result\":0,\"data\":{\"config\":{}}}")
        }))
    }

    @Test
    fun metadataResponseCannotBeAppliedToVehicleSelectedDuringRequest() {
        val source = session()
        val api = LeapmotorApi(source)
        val error = assertThrows(ApiException::class.java) {
            api.getBluetoothVehicleMetadata(execute = {
                source.selectedVin = "LTEST000000000002"
                VehicleListRawResponse(200, metadataResponse().toString())
            })
        }
        assertEquals("ble_vehicle_metadata", error.stage)
        assertFalse(error.message.orEmpty().contains(vin))
    }

    @Test
    fun cloudScopeTracksBothAccountIdentifiersVehicleDeviceAndSessionGeneration() {
        val source = session()
        val scope = BleCloudRequestScope.capture(source)
        assertTrue(scope.matches(source))
        assertFalse(scope.matches(session(generation = 1L)))
        identityMutations().forEach { mutate ->
            assertFalse(scope.matches(session().also(mutate)))
        }
        val gatewayOnly = session().apply { oldAuth = null }
        val gatewayScope = BleCloudRequestScope.capture(gatewayOnly)
        assertTrue(gatewayScope.matches(gatewayOnly))
        gatewayOnly.oldAuth = session().oldAuth
        assertFalse(gatewayScope.matches(gatewayOnly))
        assertEquals("BleCloudRequestScope(redacted)", scope.toString())
        assertFalse(scope.toString().contains(vin))
    }

    @Test
    fun identityChangesDuringRefreshPreventEveryCloudEndpointFromExecuting() {
        guardedOperations().forEach { operation ->
            identityMutations().forEach { mutate ->
                val source = session()
                var attempts = 0
                val error = assertThrows(ApiException::class.java) {
                    operation(LeapmotorApi(source), { mutate(source) }) {
                        attempts++
                        VehicleListRawResponse(200, metadataResponse().toString())
                    }
                }
                assertEquals(0, attempts)
                assertFalse(error.message.orEmpty().contains("secret-changed"))
                assertNull(error.cause)
            }
        }
    }

    @Test
    fun tokenOnlyRefreshPreservesScopeAndUsesRefreshedCredentials() {
        guardedOperations().forEach { operation ->
            val source = session()
            val scope = BleCloudRequestScope.capture(source)
            var attempts = 0
            operation(LeapmotorApi(source), {
                source.oldAuth = source.oldAuth?.copy(token = "rotated-old-token", refreshToken = "rotated-old-refresh")
                source.newAuth = source.newAuth?.copy(accessToken = "rotated-access-token", refreshToken = "rotated-refresh")
            }) { request ->
                attempts++
                assertEquals("rotated-access-token", request.header("token"))
                assertEquals(scope.deviceId, request.header("deviceId"))
                assertEquals(scope.vin, request.header("carvin"))
                VehicleListRawResponse(200, metadataResponse().toString())
            }
            assertEquals(1, attempts)
            assertTrue(scope.matches(source))
        }
    }

    @Test
    fun identityChangesDuringExecutionCannotReturnCloudSuccess() {
        guardedOperations().forEach { operation ->
            identityMutations().forEach { mutate ->
                val source = session()
                var attempts = 0
                val error = assertThrows(ApiException::class.java) {
                    operation(LeapmotorApi(source), {}) {
                        attempts++
                        mutate(source)
                        VehicleListRawResponse(200, metadataResponse().toString())
                    }
                }
                assertEquals(1, attempts)
                assertFalse(error.message.orEmpty().contains("secret-changed"))
                assertNull(error.cause)
            }
        }
    }

    @Test
    fun refreshFailureIsSanitizedAndNeverExecutesBusinessRequest() {
        guardedOperations().forEach { operation ->
            val error = assertThrows(ApiException::class.java) {
                operation(LeapmotorApi(session()), { throw IOException("secret-refresh-response") }) {
                    throw AssertionError("must not send")
                }
            }
            assertFalse(error.message.orEmpty().contains("secret-refresh-response"))
            assertNull(error.cause)
        }
    }

    @Test
    fun bluetoothPreflightTransportIsInstanceScopedAndRestoredBeforeBusinessExecution() {
        guardedOperations().forEach { operation ->
            val api = LeapmotorApi(session())
            val unrelated = LeapmotorApi(session())
            assertNull(preflightHttpClient(api))
            operation(api, {
                val client = requireNotNull(preflightHttpClient(api))
                assertSame(LeapmotorApi.bluetoothCertificateHttpClient, client)
                assertTrue(client.interceptors.isEmpty())
                assertTrue(client.networkInterceptors.isEmpty())
                assertFalse(client.followRedirects)
                assertFalse(client.followSslRedirects)
                assertNull(preflightHttpClient(unrelated))
            }) {
                assertNull(preflightHttpClient(api))
                VehicleListRawResponse(200, metadataResponse().toString())
            }
            assertNull(preflightHttpClient(api))
            assertNull(preflightHttpClient(unrelated))
        }
    }

    @Test
    fun bluetoothPreflightTransportIsRestoredWhenRefreshThrows() {
        guardedOperations().forEach { operation ->
            val api = LeapmotorApi(session())
            assertThrows(ApiException::class.java) {
                operation(api, {
                    assertSame(LeapmotorApi.bluetoothCertificateHttpClient, preflightHttpClient(api))
                    throw IOException("secret-refresh-response")
                }) { throw AssertionError("must not send") }
            }
            assertNull(preflightHttpClient(api))
        }
    }

    @Test
    fun allCloudEndpointsSanitizeTransportHttpBusinessAndMalformedResponseFailuresWithoutRetry() {
        val api = LeapmotorApi(session())
        val operations: List<((Request) -> VehicleListRawResponse) -> Unit> = listOf(
            { execute -> api.getBluetoothVehicleMetadata(execute = execute); Unit },
            { execute -> api.uploadBluetoothConfiguration(BlePassiveConfiguration(), execute) },
            { execute -> api.uploadBluetoothCalibration(null, "Synthetic", execute) }
        )
        val failures = listOf(
            VehicleListRawResponse(403, "secret-response"),
            VehicleListRawResponse(200, "secret-response"),
            VehicleListRawResponse(200, "{\"message\":\"secret-response\"}"),
            VehicleListRawResponse(200, "{\"code\":9,\"message\":\"secret-response\"}"),
            VehicleListRawResponse(200, "{\"code\":0,\"result\":9,\"data\":\"secret-response\"}")
        )
        operations.forEach { operation ->
            failures.forEach { failure ->
                var attempts = 0
                val error = assertThrows(ApiException::class.java) {
                    operation { attempts++; failure }
                }
                assertEquals(1, attempts)
                assertFalse(error.message.orEmpty().contains("secret-response"))
                assertNull(error.cause)
            }
            val error = assertThrows(ApiException::class.java) {
                operation { throw IOException("secret-transport") }
            }
            assertFalse(error.message.orEmpty().contains("secret-transport"))
            assertNull(error.cause)
        }
    }

    @Test
    fun uploadAcceptanceDoesNotMutatePreferencesOrTriggerAnotherRequest() {
        val api = LeapmotorApi(session())
        val preferences = BlePassiveConfiguration(buttonEnabled = true)
        var attempts = 0
        api.uploadBluetoothConfiguration(preferences) { attempts++; VehicleListRawResponse(200, "{\"code\":0}") }
        api.uploadBluetoothCalibration(null, "Synthetic") { attempts++; VehicleListRawResponse(200, "{\"code\":0}") }
        assertEquals(2, attempts)
        assertFalse(preferences.enabled)
        assertTrue(preferences.buttonEnabled)
    }

    private fun identityMutations(): List<(Session) -> Unit> = listOf(
        { it.deviceId = "secret-changed-device" },
        { it.selectedVin = "secret-changed-vehicle" },
        { it.oldAuth = it.oldAuth?.copy(accountId = "secret-changed-old-account") },
        { it.newAuth = it.newAuth?.copy(accountId = "secret-changed-gateway-account") },
        { it.oldAuth = null },
        { it.newAuth = null }
    )

    private fun guardedOperations(): List<(LeapmotorApi, () -> Unit, (Request) -> VehicleListRawResponse) -> Unit> = listOf(
        { api, refresh, execute -> api.getBluetoothVehicleMetadata(execute = execute, refreshSession = refresh); Unit },
        { api, refresh, execute -> api.uploadBluetoothConfiguration(BlePassiveConfiguration(), refresh, execute) },
        { api, refresh, execute -> api.uploadBluetoothCalibration(null, "Synthetic", refresh, execute) }
    )

    private fun preflightHttpClient(api: LeapmotorApi): OkHttpClient? {
        val field = LeapmotorApi::class.java.getDeclaredField("bluetoothPreflightHttpClient").apply { isAccessible = true }
        return (field.get(api) as ThreadLocal<*>).get() as? OkHttpClient
    }

    private fun formFields(request: Request): Map<String, String> {
        assertNull(request.url.query)
        val body = request.body as FormBody
        assertEquals("application/x-www-form-urlencoded", body.contentType().toString())
        return (0 until body.size).associate { body.name(it) to body.value(it) }
    }

    private fun verifyUploadIdentityAndSignatures(request: Request, fields: Map<String, String>) {
        assertEquals(vin, fields["vin"])
        assertEquals(vin, request.header("carvin"))
        assertEquals("synthetic-device", fields["deviceID"])
        assertEquals(fields["deviceID"], request.header("deviceId"))
        assertNull(request.header("XFX-CDN-CROSS-NODE"))
        assertNull(request.header("APPImei"))
        assertEquals("synthetic-access-token", request.header("token"))
        val signed = fields.filterKeys { it != "signStr" } + ("token" to "synthetic-old-token")
        val oldDigest = MessageDigest.getInstance("MD5").digest(signed.toSortedMap().values.joinToString("").toByteArray())
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }.substring(8, 24)
        assertEquals(oldDigest, fields["signStr"])
        verifyGatewaySignature(request, fields)
    }

    private fun verifyGatewaySignature(request: Request, fields: Map<String, String>) {
        val signedHeaders = listOf("acceptLanguage", "channel", "deviceId", "deviceType", "nonce", "source", "timestamp", "version")
            .associateWith { requireNotNull(request.header(it)) }
        val canonical = (signedHeaders + fields).toSortedMap().values.joinToString("")
        val mac = Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(signingKey, "HmacSHA256")) }
        val expected = mac.doFinal(canonical.toByteArray()).joinToString("") { "%02x".format(it.toInt() and 0xff) }
        assertEquals(expected, request.header("sign"))
    }
}
