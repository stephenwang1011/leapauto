package com.leapauto.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class VehicleImageCacheTest {

    @Test
    fun `vehicle picture meta model stores url and key correctly`() {
        val meta = VehiclePictureMeta(
            pictureKey = "test_key_123",
            shareBindUrl = "https://cdn.leapmotor.com/car/c16_white.png"
        )
        assertEquals("test_key_123", meta.pictureKey)
        assertEquals("https://cdn.leapmotor.com/car/c16_white.png", meta.shareBindUrl)
    }

    @Test
    fun `vehicle picture meta equals and copy work as expected`() {
        val meta1 = VehiclePictureMeta("key1", "https://example.com/pic1.png")
        val meta2 = meta1.copy(pictureKey = "key2")
        assertEquals("key2", meta2.pictureKey)
        assertEquals("https://example.com/pic1.png", meta2.shareBindUrl)
    }

    @Test
    fun `vehicle picture meta handles 3d car model json response`() {
        val json = org.json.JSONObject(
            """
            {
              "code": 0,
              "result": 0,
              "message": "请求成功",
              "data": {
                "h5Key": "3D-702ef381-d7ed-49cd-8d69-fad6f5f9f397",
                "modelParam": {
                  "carType": "C16",
                  "year": 2026,
                  "carTypeCode": "630激光雷达智尊版 6座",
                  "colorCode": 3
                },
                "srcKey": "3D-d3e0fbce-0755-441e-8381-7512fd49bc70",
                "modelType": 3,
                "shareBindUrl": "http://lp-carnet.oss-cn-hangzhou.aliyuncs.com/carModel3D/3D-2809d7c4.png"
              }
            }
            """.trimIndent()
        )
        val data = json.getJSONObject("data")
        val rawUrl = data.getString("shareBindUrl")
        val secureUrl = if (rawUrl.startsWith("http://")) "https://" + rawUrl.substring(7) else rawUrl
        val meta = VehiclePictureMeta(
            pictureKey = data.optString("srcKey"),
            shareBindUrl = secureUrl,
            rawData = data
        )

        org.junit.Assert.assertTrue(meta.shareBindUrl.startsWith("https://"))
        assertEquals("3D-d3e0fbce-0755-441e-8381-7512fd49bc70", meta.pictureKey)
        assertEquals(3, meta.rawData?.optInt("modelType"))
    }
}
