package com.leapauto.app

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChassisParkingPhotoTest {

    @Test
    fun `chassis parking photo converts http url to https`() {
        val httpUrl = "http://lp-carnet.oss-cn-hangzhou.aliyuncs.com/ChassisPicture/prod/TEST_CHASSIS_VIN_001?Expires=4942772801"
        val photo = ChassisParkingPhoto(fileUrl = httpUrl, uploadTimeMs = 1789172801313L)

        assertTrue(photo.secureUrl.startsWith("https://"))
        assertEquals("https://lp-carnet.oss-cn-hangzhou.aliyuncs.com/ChassisPicture/prod/TEST_CHASSIS_VIN_001?Expires=4942772801", photo.secureUrl)
        assertEquals(1789172801313L, photo.uploadTimeMs)
    }

    @Test
    fun `parses chassis query json response correctly`() {
        val json = """
            {
              "code": 0,
              "result": 0,
              "message": "请求成功",
              "data": {
                "fileUrl": "http://lp-carnet.oss-cn-hangzhou.aliyuncs.com/ChassisPicture/prod/TEST_VIN?Expires=12345",
                "uploadTime": 1789172801313
              }
            }
        """.trimIndent()
        val resp = JSONObject(json)
        val data = resp.optJSONObject("data")
        assertNotNull(data)

        val rawUrl = data!!.optString("fileUrl")
        val uploadTime = data.optLong("uploadTime")

        val photo = ChassisParkingPhoto(rawUrl, uploadTime)
        assertEquals("https://lp-carnet.oss-cn-hangzhou.aliyuncs.com/ChassisPicture/prod/TEST_VIN?Expires=12345", photo.secureUrl)
        assertEquals(1789172801313L, photo.uploadTimeMs)
    }
}
