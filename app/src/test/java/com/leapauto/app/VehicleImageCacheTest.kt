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

    @Test
    fun `custom vehicle image file can be created and deleted independently`() {
        val tempDir = java.io.File(System.getProperty("java.io.tmpdir"), "test_vehicle_cache_${System.currentTimeMillis()}")
        tempDir.mkdirs()
        try {
            val context = object : android.content.ContextWrapper(null) {
                override fun getFilesDir(): java.io.File = tempDir
            }
            val vin = "TEST_VIN_RESET_FLOW"
            val customFile = VehicleImageCache.getCustomFile(context, vin)
            customFile.writeText("custom-image-binary-placeholder")
            org.junit.Assert.assertTrue(customFile.exists())

            if (customFile.exists()) customFile.delete()

            org.junit.Assert.assertFalse(customFile.exists())
            val officialFile = VehicleImageCache.getCacheFile(context, vin)
            org.junit.Assert.assertEquals("TEST_VIN_RESET_FLOW.png", officialFile.name)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `removing custom image transitions widget source resolution to official or 3d snapshot`() {
        val sourceWithCustom = VehicleImageCache.resolveWidgetImageSource(
            hasCustomImage = true,
            has3DSnapshot = true,
            hasOfficial2DImage = true
        )
        assertEquals(VehicleImageCache.WidgetImageSource.CUSTOM, sourceWithCustom)

        val sourceAfterResetWithSnapshot = VehicleImageCache.resolveWidgetImageSource(
            hasCustomImage = false,
            has3DSnapshot = true,
            hasOfficial2DImage = true
        )
        assertEquals(VehicleImageCache.WidgetImageSource.THREE_D_SNAPSHOT, sourceAfterResetWithSnapshot)

        val sourceAfterResetOfficialOnly = VehicleImageCache.resolveWidgetImageSource(
            hasCustomImage = false,
            has3DSnapshot = false,
            hasOfficial2DImage = true
        )
        assertEquals(VehicleImageCache.WidgetImageSource.OFFICIAL_2D, sourceAfterResetOfficialOnly)
    }

    @Test
    fun `reset custom image refresh runs inside resource lane and outside main callback`() {
        val projectDir = projectDirectory()
        val mainActivityFile = java.io.File(projectDir, "app/src/main/java/com/leapauto/app/MainActivity.kt")
        val content = mainActivityFile.readText().replace("\r\n", "\n")

        val resetMethod = content.substringAfter("private fun resetCustomVehicleImage()")
            .substringBefore("private fun switchVehicle")
        val laneCall = "resourceWorker.submit(\"reset-custom-image\")"
        val laneStart = resetMethod.indexOf(laneCall)
        org.junit.Assert.assertTrue("Reset must use the bounded resource lane", laneStart >= 0)
        val laneBody = blockAt(resetMethod, resetMethod.indexOf('{', laneStart))
        val mainStart = laneBody.indexOf("runOnMain")
        org.junit.Assert.assertTrue("UI updates must return to main", mainStart >= 0)
        val mainBody = blockAt(laneBody, laneBody.indexOf('{', mainStart))
        val refresh = "ControlWidget.refreshData(applicationContext)"
        org.junit.Assert.assertEquals("Only one immediate widget refresh", 1,
            Regex("ControlWidget\\.refreshData\\(").findAll(resetMethod).count())
        org.junit.Assert.assertTrue(laneBody.substringBefore("runOnMain").contains(refresh))
        org.junit.Assert.assertTrue(laneBody.substringBefore("runOnMain").contains("VehicleImageCache.removeCustomImage(applicationContext, vin)"))
        org.junit.Assert.assertTrue(laneBody.contains("if (!isCurrentBackground(snapshot)) return@submit"))
        org.junit.Assert.assertFalse(mainBody.contains("ControlWidget.refreshData"))
        org.junit.Assert.assertFalse(resetMethod.substringBefore(laneCall).contains("ControlWidget.refreshData"))
        org.junit.Assert.assertFalse("Must not consume the control lane", resetMethod.contains("worker.execute"))
        org.junit.Assert.assertFalse(resetMethod.contains("CompactControlWidget.refreshData"))
        org.junit.Assert.assertTrue(mainBody.contains("syncVehicleImage(vin)"))
    }

    private fun blockAt(source: String, opening: Int): String {
        require(opening >= 0 && source[opening] == '{')
        var depth = 0
        for (index in opening until source.length) {
            when (source[index]) {
                '{' -> depth++
                '}' -> if (--depth == 0) return source.substring(opening + 1, index)
            }
        }
        error("Unbalanced source block")
    }

    @Test
    fun `stripGroundShadow is integrated in vehicle image cache lifecycle`() {
        val projectDir = projectDirectory()
        val cacheFile = java.io.File(projectDir, "app/src/main/java/com/leapauto/app/VehicleImageCache.kt").readText()

        org.junit.Assert.assertTrue("VehicleImageCache must define stripGroundShadow", cacheFile.contains("fun stripGroundShadow(src: Bitmap): Bitmap"))
        org.junit.Assert.assertTrue("sync must call stripGroundShadow", cacheFile.contains("val stripped = stripGroundShadow(decoded)"))
        org.junit.Assert.assertTrue("loadCachedBitmap must call stripGroundShadow", cacheFile.contains("stripGroundShadow(raw)"))
        org.junit.Assert.assertTrue("sync must refresh stale shadow-version caches", cacheFile.contains("fun shouldRefreshOfficialImage("))
        org.junit.Assert.assertTrue("sync must use the refresh decision", cacheFile.contains("shouldRefreshOfficialImage("))
        org.junit.Assert.assertTrue("loadWidgetBitmap must call stripGroundShadow for official 2d", cacheFile.contains("val processed = if (source == WidgetImageSource.OFFICIAL_2D) stripGroundShadow(bitmap) else bitmap"))

        val loadSection = cacheFile.substringAfter("fun loadCachedBitmap").substringBefore("fun loadWidgetBitmap")
        org.junit.Assert.assertFalse(
            "a stale cache must not be re-stripped and stamped on read; sync re-downloads the original instead",
            loadSection.contains("CURRENT_SHADOW_VERSION") || loadSection.contains("KEY_PREFIX_SHADOW_VERSION")
        )
    }

    @Test
    fun `official image refresh follows url, file and shadow version staleness`() {
        org.junit.Assert.assertTrue(
            "a URL change must trigger a refresh",
            VehicleImageCache.shouldRefreshOfficialImage("https://cdn/a.png", "https://cdn/b.png", true, 1024L, 3)
        )
        org.junit.Assert.assertTrue(
            "a missing cache file must trigger a refresh",
            VehicleImageCache.shouldRefreshOfficialImage("https://cdn/a.png", "https://cdn/a.png", false, 0L, 3)
        )
        org.junit.Assert.assertTrue(
            "an empty cache file must trigger a refresh",
            VehicleImageCache.shouldRefreshOfficialImage("https://cdn/a.png", "https://cdn/a.png", true, 0L, 3)
        )
        org.junit.Assert.assertTrue(
            "an outdated shadow algorithm version must re-download the original image",
            VehicleImageCache.shouldRefreshOfficialImage("https://cdn/a.png", "https://cdn/a.png", true, 1024L, 2)
        )
        org.junit.Assert.assertFalse(
            "an up-to-date cache must be reused",
            VehicleImageCache.shouldRefreshOfficialImage("https://cdn/a.png", "https://cdn/a.png", true, 1024L, 3)
        )
    }

    @Test
    fun `sync must not persist a 3d downgrade when metadata lookup fails`() {
        val projectDir = projectDirectory()
        val cacheFile = java.io.File(projectDir, "app/src/main/java/com/leapauto/app/VehicleImageCache.kt").readText()

        val failureBranch = cacheFile.substringAfter("if (meta == null) {").substringBefore("return false")
        org.junit.Assert.assertFalse(
            "a transient metadata/network failure must not permanently downgrade the vehicle to 2D",
            failureBranch.contains("setVehicle3DSupported(context, vin, false)")
        )
    }

    @Test
    fun `official 2d car image uses official 2 to 1 dynamic adaptive ratio`() {
        val projectDir = projectDirectory()
        val screenFile = java.io.File(projectDir, "app/src/main/java/com/leapauto/app/ui/LeapAutoScreen.kt").readText()

        org.junit.Assert.assertTrue("2D car image must use fillMaxWidth(0.82f)", screenFile.contains(".fillMaxWidth(0.82f)"))
        org.junit.Assert.assertTrue("2D car image must use aspectRatio(2.0f)", screenFile.contains(".aspectRatio(2.0f)"))
    }

    private fun projectDirectory(): java.io.File {
        val workingDirectory = requireNotNull(System.getProperty("user.dir"))
        return generateSequence(java.io.File(workingDirectory)) { it.parentFile }
            .first { java.io.File(it, "app").isDirectory }
    }
}
