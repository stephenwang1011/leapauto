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

    @Test
    fun `vehicle image file names are isolated between custom and official`() {
        val tempDir = java.io.File(System.getProperty("java.io.tmpdir"), "test_vehicle_images")
        val context = object : android.content.ContextWrapper(null) {
            override fun getFilesDir(): java.io.File = tempDir
        }

        val vin = "TEST_VIN_123"
        val officialFile = VehicleImageCache.getCacheFile(context, vin)
        val customFile = VehicleImageCache.getCustomFile(context, vin)
        val snapshot3DFile = VehicleImageCache.get3DSnapshotFile(context, vin)

        assertEquals("TEST_VIN_123.png", officialFile.name)
        assertEquals("TEST_VIN_123_custom.png", customFile.name)
        assertEquals("TEST_VIN_123_3d.png", snapshot3DFile.name)
        org.junit.Assert.assertNotEquals(officialFile.name, customFile.name)
        org.junit.Assert.assertNotEquals(officialFile.name, snapshot3DFile.name)
    }

    @Test
    fun `widget image source prefers custom then 3d snapshot then official 2d`() {
        assertEquals(
            VehicleImageCache.WidgetImageSource.CUSTOM,
            VehicleImageCache.resolveWidgetImageSource(
                hasCustomImage = true,
                has3DSnapshot = true,
                hasOfficial2DImage = true
            )
        )
        assertEquals(
            VehicleImageCache.WidgetImageSource.THREE_D_SNAPSHOT,
            VehicleImageCache.resolveWidgetImageSource(
                hasCustomImage = false,
                has3DSnapshot = true,
                hasOfficial2DImage = true
            )
        )
        assertEquals(
            VehicleImageCache.WidgetImageSource.OFFICIAL_2D,
            VehicleImageCache.resolveWidgetImageSource(
                hasCustomImage = false,
                has3DSnapshot = false,
                hasOfficial2DImage = true
            )
        )
        assertEquals(
            VehicleImageCache.WidgetImageSource.NONE,
            VehicleImageCache.resolveWidgetImageSource(
                hasCustomImage = false,
                has3DSnapshot = false,
                hasOfficial2DImage = false
            )
        )
    }
}
