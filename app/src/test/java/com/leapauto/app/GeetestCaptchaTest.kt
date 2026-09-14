package com.leapauto.app

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GeetestCaptchaTest {

    @Test
    fun `parses standard geetest validate json successfully`() {
        val json = """
            {
                "lot_number": "lot_123456789",
                "pass_token": "pass_abcdef123",
                "gen_time": "1725900000",
                "captcha_output": "output_xyz987"
            }
        """.trimIndent()

        val result = GeetestCaptchaResult.fromValidateJson(json, requestId = "req_test_001")
        assertEquals("lot_123456789", result.lotNumber)
        assertEquals("pass_abcdef123", result.passToken)
        assertEquals("1725900000", result.genTime)
        assertEquals("output_xyz987", result.captchaOutput)
        assertEquals("req_test_001", result.requestId)
    }

    @Test
    fun `challenge exception retains full challenge details`() {
        val challenge = GeetestChallenge(
            captchaId = "278c775b5dcb8dda2a2f196f60a28a34",
            riskType = "slide|1725900000|278c775b5dcb8dda2a2f196f60a28a34|token123",
            requestId = "req_999",
            phone = "13800138000",
            smsCode = "123456"
        )
        val ex = GeetestChallengeRequiredException(challenge, "触发安全风控")

        assertEquals("触发安全风控", ex.message)
        assertEquals("278c775b5dcb8dda2a2f196f60a28a34", ex.challenge.captchaId)
        assertEquals("13800138000", ex.challenge.phone)
        assertEquals("123456", ex.challenge.smsCode)
    }

    @Test
    fun `extracts old auth from full check_login response json`() {
        val fullResponse = """
            {
                "code": 0,
                "msg": "操作成功",
                "data": {
                    "appLoginVO": {
                        "accountId": "10086",
                        "token": "token_mock_test_123",
                        "refreshToken": "refresh_mock_456",
                        "tokenExpired": "21600"
                    },
                    "phone": "13800138000"
                }
            }
        """.trimIndent()

        val json = JSONObject(fullResponse)
        val authObj = json.optJSONObject("data")?.optJSONObject("appLoginVO")
        assertNotNull(authObj)
        assertEquals("10086", authObj?.optString("accountId"))
        assertEquals("token_mock_test_123", authObj?.optString("token"))
        assertEquals("refresh_mock_456", authObj?.optString("refreshToken"))
        assertEquals("21600", authObj?.optString("tokenExpired"))
    }

    @Test
    fun `parses comma separated format correctly`() {
        val input = "10086,token_mock_test_123,refresh_mock_456"
        val parts = input.split(",")
        assertEquals("10086", parts[0].trim())
        assertEquals("token_mock_test_123", parts[1].trim())
        assertEquals("refresh_mock_456", parts[2].trim())
    }

    @Test
    fun `parses header formatted string with regex`() {
        val input = """
            userId: 10086
            XFX-CDN-CROSS-NODE: token_mock_test_123456789
        """.trimIndent()

        val tokenMatch = Regex("""(?:token|XFX-CDN-CROSS-NODE)["':=\s]+([a-zA-Z0-9_-]{16,})""", RegexOption.IGNORE_CASE).find(input)
        val accountMatch = Regex("""(?:accountId|userId|account_id)["':=\s]+([a-zA-Z0-9_-]+)""", RegexOption.IGNORE_CASE).find(input)

        assertEquals("token_mock_test_123456789", tokenMatch?.groupValues?.getOrNull(1))
        assertEquals("10086", accountMatch?.groupValues?.getOrNull(1))
    }

    @Test
    fun `missing phone in refresh message is not considered session expired`() {
        org.junit.Assert.assertFalse(SessionExpiry.isRefreshTokenInvalid("旧 token 续期需要手机号，请重新登录"))
    }

    @Test
    fun `extracts deviceId from curl or json if present`() {
        val input = """
            {"identifier":"728504145190903808","security":"A87EE68BF4C7466AB4FFA8EAE27DEAC9","deviceID":"159272b1e9e64753b0411474ebb8fb10"}
        """.trimIndent()
        val json = JSONObject(input)
        assertEquals("159272b1e9e64753b0411474ebb8fb10", json.optString("deviceID"))
        assertEquals("728504145190903808", json.optString("identifier"))
        assertEquals("A87EE68BF4C7466AB4FFA8EAE27DEAC9", json.optString("security"))
    }

    @Test
    fun `extracts deviceId from gateway jwt token payload`() {
        val jwt = "eyJub25jZSI6IjMwNmI2ZGFmODM1ZTQxZDViMWY4Y2YyNWIyM2Y1MDVjIiwiYWxnIjoiSFMyNTYiLCJ0eXAiOiJKV1QifQ.eyJ1c2VyX25hbWUiOiJhY2NvdW50SWQ6NzI4NTA0MTQ1MTkwOTAzODA4LDEsZGV2aWNlSWQ6MTU5MjcyYjFlOWU2NDc1M2IwNDExNDc0ZWJiOGZiMTAscGFzc3dvcmQ6Iiwic2NvcGUiOlsicmVhZCJdLCJleHAiOjE3ODkxMTYyNTUsImF1dGhvcml0aWVzIjpbImFjY291bnRJZDo3Mjg1MDQxNDUxOTA5MDM4MDgiXSwianRpIjoiNzEzMzRiYzMtYTgxOS00NTliLWJiZjctODk0Y2E2NDdiOGRkIiwic2lnbl90aW1lIjoxNzg5MTA5MDU1LCJjbGllbnRfaWQiOiJIelRtY3NCZyJ9.gr4qb8KJLkeEog4sdGQDp3g6XwSCkH_4qYoN1AcWB9w"
        assertEquals("159272b1e9e64753b0411474ebb8fb10", Crypto.jwtDeviceId(jwt))
    }

    @Test
    fun `encryptOperationPassword produces clean base64 without newlines`() {
        val token = "A87EE68BF4C7466AB4FFA8EAE27DEAC9A87EE68BF4C7466AB4FFA8EAE27DEAC9"
        val encrypted = Crypto.encryptOperationPassword("1234", token)
        assertTrue(encrypted.isNotBlank())
        org.junit.Assert.assertFalse(encrypted.contains("\n"))
        org.junit.Assert.assertFalse(encrypted.contains("\r"))
        org.junit.Assert.assertFalse(encrypted.contains(" "))
    }

    @Test
    fun `verify real oppwd from api log matches jwt decryption and encryption`() {
        val jwt = "eyJub25jZSI6ImJkOTE5YTIzZDU2MzRjZTE4OTZjMGQ4NWI1M2Q3ODM3IiwiYWxnIjoiSFMyNTYiLCJ0eXAiOiJKV1QifQ.eyJ1c2VyX25hbWUiOiJhY2NvdW50SWQ6NzI4NTA0MTQ1MTkwOTAzODA4LDEsZGV2aWNlSWQ6MTU5MjcyYjFlOWU2NDc1M2IwNDExNDc0ZWJiOGZiMTAscGFzc3dvcmQ6Iiwic2NvcGUiOlsicmVhZCJdLCJleHAiOjE3ODkxMjI4ODYsImF1dGhvcml0aWVzIjpbImFjY291bnRJZDo3Mjg1MDQxNDUxOTA5MDM4MDgiXSwianRpIjoiMmY4ZDI0YmUtMTZiZC00MjMxLWJhNmEtMmUwNmU0MmZhMzgzIiwic2lnbl90aW1lIjoxNzg5MTE1Njg2LCJjbGllbnRfaWQiOiJIelRtY3NCZyJ9.yFzTncwRyHZVcmm8L2hc1MCBr_diAv4BUmXeMLzVP8Y"
        val target = "oETH2fp4MZOk8xFOdz1akQ=="
        val decryptedPin = Crypto.decryptOperationPassword(target, jwt)
        assertEquals("8791", decryptedPin)

        val reEncrypted = Crypto.encryptOperationPassword(decryptedPin, jwt)
        assertEquals(target, reEncrypted)
    }

    @Test
    fun `encryptOperationPassword supports 32 char token by doubling`() {
        val shortToken = "A87EE68BF4C7466AB4FFA8EAE27DEAC9"
        val encrypted = Crypto.encryptOperationPassword("8791", shortToken)
        assertTrue(encrypted.isNotBlank())
        val decrypted = Crypto.decryptOperationPassword(encrypted, shortToken)
        assertEquals("8791", decrypted)
    }

    @Test
    fun `extracts smDeviceId from json or string if present`() {
        val input = """
            {"identifier":"728504145190903808","security":"A87EE68BF4C7466AB4FFA8EAE27DEAC9","deviceID":"159272b1e9e64753b0411474ebb8fb10","smDeviceId":"BtuFPatpuhYEnHSCJjatnQbsYId7c6LGc4vWXe0ZzLCf21mXdDS"}
        """.trimIndent()
        val json = JSONObject(input)
        assertEquals("BtuFPatpuhYEnHSCJjatnQbsYId7c6LGc4vWXe0ZzLCf21mXdDS", json.optString("smDeviceId"))
    }
}
