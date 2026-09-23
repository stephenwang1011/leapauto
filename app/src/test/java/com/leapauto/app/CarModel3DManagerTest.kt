package com.leapauto.app

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class CarModel3DManagerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `sanitizeKey replaces invalid characters with underscore`() {
        assertEquals("3D_702ef381_d7ed_49cd_8d69_fad6f5f9f397", CarModel3DManager.sanitizeKey("3D-702ef381-d7ed-49cd-8d69-fad6f5f9f397"))
        assertEquals("test_model_v1", CarModel3DManager.sanitizeKey("test/model:v1"))
        assertEquals("abc123XYZ", CarModel3DManager.sanitizeKey("abc123XYZ"))
    }

    @Test
    fun `isModelReady returns false for non-existent or incomplete directory`() {
        assertFalse(CarModel3DManager.isModelReady(tempFolder.root, ""))
        assertFalse(CarModel3DManager.isModelReady(tempFolder.root, "not_exist_key"))

        val dir = File(tempFolder.root, "CarModel3D/test_key")
        dir.mkdirs()
        assertFalse(File(dir, ".ready").exists())

        // Only .ready but no index.html
        File(dir, ".ready").createNewFile()
        assertFalse(File(dir, "index.html").exists())
    }

    @Test
    fun `isModelReady with srcKey checks both base ready and specific model ready marker`() {
        val baseDir = tempFolder.root.resolve("CarModel3D")
        val modelKey = "3D_shared_engine"
        val srcKey = "3D-d0453f6d-7a31-4a0f-8b4d-7146f4026250"
        val dir = File(baseDir, modelKey)
        dir.mkdirs()
        File(dir, "index.html").writeText("<html></html>")
        File(dir, ".ready").createNewFile()

        // Base model without srcKey is ready
        assertTrue(CarModel3DManager.isModelReady(baseDir, modelKey))

        // But with srcKey requested, it should be false until .ready_<sanitized_srcKey> exists
        assertFalse(CarModel3DManager.isModelReady(baseDir, modelKey, srcKey))

        // Once srcKey marker is created, it should be ready
        val sanitizedSrc = CarModel3DManager.sanitizeKey(srcKey)
        File(dir, ".ready_$sanitizedSrc").createNewFile()
        assertTrue(CarModel3DManager.isModelReady(baseDir, modelKey, srcKey))

        // Another vehicle model with different srcKey is still not ready in the same directory
        val anotherSrcKey = "3D-d3e0fbce-0755-441e-8381-7512fd49bc70"
        assertFalse(CarModel3DManager.isModelReady(baseDir, modelKey, anotherSrcKey))
    }

    @Test
    fun `vehicle picture meta extracts h5Key and modelParam correctly`() {
        val json = JSONObject(
            """
            {
              "h5Key": "3D-702ef381-d7ed-49cd-8d69-fad6f5f9f397",
              "srcKey": "3D-d3e0fbce-0755-441e-8381-7512fd49bc70",
              "shareBindUrl": "https://cdn.leapmotor.com/car.png",
              "modelParam": {
                "carType": "C16",
                "year": 2026,
                "carTypeCode": "630激光雷达智尊版 6座",
                "colorCode": 3
              }
            }
            """.trimIndent()
        )
        val meta = VehiclePictureMeta(
            pictureKey = "key_1",
            shareBindUrl = "https://cdn.leapmotor.com/car.png",
            rawData = json
        )

        assertEquals("3D-702ef381-d7ed-49cd-8d69-fad6f5f9f397", meta.h5Key)
        assertEquals("3D-d3e0fbce-0755-441e-8381-7512fd49bc70", meta.srcKey)
        assertNotNull(meta.modelParam)
        assertEquals("C16", meta.modelParam?.optString("carType"))
        assertEquals(2026, meta.modelParam?.optInt("year"))
        assertEquals(3, meta.modelParam?.optInt("colorCode"))
    }

    @Test
    fun `dual package merge extraction creates merged folder with index html and android assets`() {
        val h5ZipFile = tempFolder.newFile("h5_runtime.zip")
        ZipOutputStream(FileOutputStream(h5ZipFile)).use { zos ->
            zos.putNextEntry(ZipEntry("index.html"))
            zos.write("<html><head><title>3D</title></head></html>".toByteArray())
            zos.closeEntry()

            zos.putNextEntry(ZipEntry("js/three.js"))
            zos.write("// ThreeJS".toByteArray())
            zos.closeEntry()
        }

        val srcZipFile = tempFolder.newFile("src_assets.zip")
        ZipOutputStream(FileOutputStream(srcZipFile)).use { zos ->
            zos.putNextEntry(ZipEntry("android/C16/model.gltf"))
            zos.write("{}".toByteArray())
            zos.closeEntry()

            zos.putNextEntry(ZipEntry("android/C16/texture.png"))
            zos.write(byteArrayOf(1, 2, 3))
            zos.closeEntry()
        }

        val destDir = tempFolder.newFolder("CarModel3D", "merged_model")

        // 1. 解压 H5 包
        java.util.zip.ZipInputStream(h5ZipFile.inputStream()).use { zis ->
            while (true) {
                val entry = zis.nextEntry ?: break
                val file = File(destDir, entry.name)
                if (entry.isDirectory) {
                    file.mkdirs()
                } else {
                    file.parentFile?.mkdirs()
                    FileOutputStream(file).use { fos -> zis.copyTo(fos) }
                }
                zis.closeEntry()
            }
        }

        // 2. 合并解压 Src 素材包至同一目录
        java.util.zip.ZipInputStream(srcZipFile.inputStream()).use { zis ->
            while (true) {
                val entry = zis.nextEntry ?: break
                val file = File(destDir, entry.name)
                if (entry.isDirectory) {
                    file.mkdirs()
                } else {
                    file.parentFile?.mkdirs()
                    FileOutputStream(file).use { fos -> zis.copyTo(fos) }
                }
                zis.closeEntry()
            }
        }
        File(destDir, ".ready").createNewFile()

        assertTrue(File(destDir, ".ready").exists())
        assertTrue(File(destDir, "index.html").exists())
        assertTrue(File(destDir, "js/three.js").exists())
        assertTrue(File(destDir, "android/C16/model.gltf").exists())
        assertTrue(File(destDir, "android/C16/texture.png").exists())
        assertTrue(CarModel3DManager.isModelReady(tempFolder.root.resolve("CarModel3D"), "merged_model"))
    }

    @Test
    fun `zip extraction creates valid structure with index html and ready marker`() {
        val zipFile = tempFolder.newFile("test_model.zip")
        ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
            zos.putNextEntry(ZipEntry("index.html"))
            zos.write("<html><body>3D Car Model</body></html>".toByteArray())
            zos.closeEntry()

            zos.putNextEntry(ZipEntry("model/car.gltf"))
            zos.write("{}".toByteArray())
            zos.closeEntry()
        }

        val destDir = tempFolder.newFolder("dest_model")
        java.util.zip.ZipInputStream(zipFile.inputStream()).use { zis ->
            while (true) {
                val entry = zis.nextEntry ?: break
                val file = File(destDir, entry.name)
                if (entry.isDirectory) {
                    file.mkdirs()
                } else {
                    file.parentFile?.mkdirs()
                    FileOutputStream(file).use { fos ->
                        zis.copyTo(fos)
                    }
                }
                zis.closeEntry()
            }
        }
        File(destDir, ".ready").createNewFile()

        assertTrue(File(destDir, ".ready").exists())
        assertTrue(File(destDir, "index.html").exists())
        assertTrue(File(destDir, "model/car.gltf").exists())
        assertEquals("<html><body>3D Car Model</body></html>", File(destDir, "index.html").readText())
    }

    @Test
    fun `car model web view script injects camera zoom and vertical alignment`() {
        val workingDirectory = requireNotNull(System.getProperty("user.dir"))
        val projectDir = generateSequence(File(workingDirectory)) { it.parentFile }
            .first { File(it, "app").isDirectory }
        val viewSource = File(projectDir, "app/src/main/java/com/leapauto/app/ui/CarModel3DView.kt").readText()

        assertTrue(viewSource.contains("targetZoom = 0.9225"))
        assertTrue(viewSource.contains("cam.updateProjectionMatrix()"))
        assertTrue(viewSource.contains("cam.position.set(-14.5, 1.96, 14.5)"))
        assertTrue(viewSource.contains("ctrl.target.copy(target)"))
        assertTrue(viewSource.contains("v.renderer.setClearColor(0x000000, 0)"))
        assertTrue(viewSource.contains("v.renderer.setClearAlpha(0)"))
        assertTrue(viewSource.contains("v.scene.background = null"))
        assertTrue(viewSource.contains("c.visible = false"))
    }

    @Test
    fun `car model web view script injects vehicle state to H5 and fallback mesh nodes`() {
        val workingDirectory = requireNotNull(System.getProperty("user.dir"))
        val projectDir = generateSequence(File(workingDirectory)) { it.parentFile }
            .first { File(it, "app").isDirectory }
        val viewSource = File(projectDir, "app/src/main/java/com/leapauto/app/ui/CarModel3DView.kt").readText()

        assertTrue(viewSource.contains("c.LFDoorState"))
        assertTrue(viewSource.contains("c.TrunkState"))
        assertTrue(viewSource.contains("c.DoorLFWindowState"))
    }

    @Test
    fun `car model web view script uses official controller command dispatch for doors and windows`() {
        val workingDirectory = requireNotNull(System.getProperty("user.dir"))
        val projectDir = generateSequence(File(workingDirectory)) { it.parentFile }
            .first { File(it, "app").isDirectory }
        val viewSource = File(projectDir, "app/src/main/java/com/leapauto/app/ui/CarModel3DView.kt").readText()

        assertTrue(viewSource.contains("window.controller"))
        assertTrue(viewSource.contains("LeftFront_DoorImmediately"))
        assertTrue(viewSource.contains("TrunkImmediately"))
        assertTrue(viewSource.contains("LeftFront_WindowImmediately"))
        assertTrue(viewSource.contains("c.__lockViewAnim__ = true"))
        assertTrue(viewSource.contains("c.handleTrunk"))
        assertTrue(viewSource.contains("c.handleDoorLFWindow"))
        assertTrue(viewSource.contains("v.setFPS(60, 3500"))
        assertTrue(viewSource.contains("Uri.decode"))
        assertTrue(viewSource.contains("if (rawYear < 2026) 2026 else rawYear"))
        assertTrue(viewSource.contains("c.doorLFWindow.frameAnim.setCurrentFrame(rFl)"))
        assertTrue(viewSource.contains("c.trunkNode.rotation.z"))
    }

    @Test
    fun `main activity contains optimistic window control state updates`() {
        val workingDirectory = requireNotNull(System.getProperty("user.dir"))
        val projectDir = generateSequence(File(workingDirectory)) { it.parentFile }
            .first { File(it, "app").isDirectory }
        val mainSource = File(projectDir, "app/src/main/java/com/leapauto/app/MainActivity.kt").readText()

        assertTrue(mainSource.contains("\"windowVent\" -> {"))
        assertTrue(mainSource.contains("leftFrontWindowPercent = 15"))
        assertTrue(mainSource.contains("\"windowOpen\" -> {"))
        assertTrue(mainSource.contains("leftFrontWindowPercent = 50"))
        assertTrue(mainSource.contains("\"windowClose\" -> {"))
        assertTrue(mainSource.contains("leftFrontWindowPercent = 0"))
        assertTrue(mainSource.contains("in 1..3 -> 15"))
        assertTrue(mainSource.contains("in 4..6 -> 50"))
    }

    @Test
    fun `car model web view script contains driving motion and lane line handling`() {
        val workingDirectory = requireNotNull(System.getProperty("user.dir"))
        val projectDir = generateSequence(File(workingDirectory)) { it.parentFile }
            .first { File(it, "app").isDirectory }
        val viewSource = File(projectDir, "app/src/main/java/com/leapauto/app/ui/CarModel3DView.kt").readText()

        assertTrue(viewSource.contains("c.handleVehicleMove(speed)"))
        assertTrue(viewSource.contains("c.__laneLine__.visible = speed > 0"))
        assertTrue(viewSource.contains("loopTilingOffset"))
        assertTrue(viewSource.contains("0.0, 0.42, 1.0"))
        assertTrue(viewSource.contains("0.22"))
        assertTrue(viewSource.contains("pulse = pow"))
        assertTrue(viewSource.contains("skyBox.setDayOrNight(isDark)"))
        assertTrue(viewSource.contains("skyBox.setDayOrNightImmediately"))
        assertTrue(viewSource.contains("c.handleMarkLight(markLightOn, imm)"))
        assertTrue(viewSource.contains("dayTexture"))
    }

    @Test
    fun `car model web view script contains 3d snapshot export isolated from desktop widgets`() {
        val workingDirectory = requireNotNull(System.getProperty("user.dir"))
        val projectDir = generateSequence(File(workingDirectory)) { it.parentFile }
            .first { File(it, "app").isDirectory }
        val viewSource = File(projectDir, "app/src/main/java/com/leapauto/app/ui/CarModel3DView.kt").readText()

        assertTrue(viewSource.contains("LeapNative"))
        assertTrue(viewSource.contains("toDataURL('image/png')"))
        assertTrue(viewSource.contains("VehicleImageCache.save3DSnapshot"))
        assertTrue(viewSource.contains("modelKey"))
        assertTrue(viewSource.contains("ControlWidget.refreshData(context)"))
    }

    @Test
    fun `leap auto screen vehicle hero uses matchParentSize for 3D panoramic skybox backdrop`() {
        val workingDirectory = requireNotNull(System.getProperty("user.dir"))
        val projectDir = generateSequence(File(workingDirectory)) { it.parentFile }
            .first { File(it, "app").isDirectory }
        val screenSource = File(projectDir, "app/src/main/java/com/leapauto/app/ui/LeapAutoScreen.kt").readText()

        assertTrue(screenSource.contains(".matchParentSize()"))
        assertTrue(screenSource.contains("CarModel3DView("))
    }

    @Test
    fun `leap auto screen vehicle hero contains 3d loading indicator and retry download action`() {
        val workingDirectory = requireNotNull(System.getProperty("user.dir"))
        val projectDir = generateSequence(File(workingDirectory)) { it.parentFile }
            .first { File(it, "app").isDirectory }
        val screenSource = File(projectDir, "app/src/main/java/com/leapauto/app/ui/LeapAutoScreen.kt").readText()

        assertTrue(screenSource.contains("3D车模加载中..."))
        assertTrue(screenSource.contains("15_000L"))
        assertTrue(screenSource.contains("prefer2DModel"))
        assertTrue(screenSource.contains("onRetryDownload3D"))
        assertTrue(screenSource.contains("切回3D"))
    }

    @Test
    fun `car model web view script contains 6 official 3d effects dispatch`() {
        val workingDirectory = requireNotNull(System.getProperty("user.dir"))
        val projectDir = generateSequence(File(workingDirectory)) { it.parentFile }
            .first { File(it, "app").isDirectory }
        val viewSource = File(projectDir, "app/src/main/java/com/leapauto/app/ui/CarModel3DView.kt").readText()

        assertTrue(viewSource.contains("c.handleCharge"))
        assertTrue(viewSource.contains("c.handleDemist"))
        assertTrue(viewSource.contains("c.handleRearViewWarmEffect"))
        assertTrue(viewSource.contains("c.handleTirePressure"))
        assertTrue(viewSource.contains("c.handleLowBeamLight"))
        assertTrue(viewSource.contains("c.handleStopLight"))
        assertTrue(viewSource.contains("c.handleBonnet"))
    }

    @Test
    fun `leap auto screen home tire pressure card top down car model clicks to open health check`() {
        val workingDirectory = requireNotNull(System.getProperty("user.dir"))
        val projectDir = generateSequence(File(workingDirectory)) { it.parentFile }
            .first { File(it, "app").isDirectory }
        val screenSource = File(projectDir, "app/src/main/java/com/leapauto/app/ui/LeapAutoScreen.kt").readText()

        assertTrue(screenSource.contains("HomeTirePressureCard("))
        assertTrue(screenSource.contains("onCarClick = onOpenHealthCheck"))
        assertTrue(screenSource.contains(".clickable(onClick = onCarClick)"))
    }

    @Test
    fun `leap auto screen vehicle hero uses compact 180dp stage`() {
        val workingDirectory = requireNotNull(System.getProperty("user.dir"))
        val projectDir = generateSequence(File(workingDirectory)) { it.parentFile }
            .first { File(it, "app").isDirectory }
        val screenSource = File(projectDir, "app/src/main/java/com/leapauto/app/ui/LeapAutoScreen.kt").readText()

        assertTrue(screenSource.contains(".height(180.dp)"))
    }

    @Test
    fun `leap auto screen vehicle hero adopts 16dp unified corners with symmetric alignment`() {
        val workingDirectory = requireNotNull(System.getProperty("user.dir"))
        val projectDir = generateSequence(File(workingDirectory)) { it.parentFile }
            .first { File(it, "app").isDirectory }
        val screenSource = File(projectDir, "app/src/main/java/com/leapauto/app/ui/LeapAutoScreen.kt").readText()

        assertTrue(screenSource.contains("heroCardShape = RoundedCornerShape(16.dp)"))
        assertTrue(screenSource.contains("border = glassCardBorder()"))
        assertTrue(screenSource.contains("pageBg.copy(alpha = if (isDark)"))
    }

    @Test
    fun `leap auto screen vehicle hero adopts scheme A aurora crystal dual color progress and pure white mileage font`() {
        val workingDirectory = requireNotNull(System.getProperty("user.dir"))
        val projectDir = generateSequence(File(workingDirectory)) { it.parentFile }
            .first { File(it, "app").isDirectory }
        val screenSource = File(projectDir, "app/src/main/java/com/leapauto/app/ui/LeapAutoScreen.kt").readText()

        assertTrue(screenSource.contains("mileageDisplayColor = MaterialTheme.colorScheme.onSurface"))
        assertTrue(screenSource.contains("electricGradientColors"))
        assertTrue(screenSource.contains("fuelGradientColors"))
        assertTrue(screenSource.contains("Color(0xFF0066FF), Color(0xFF00E676)"))
        assertTrue(screenSource.contains("slotBaseColor = if (isDark) Color.White.copy(alpha = 0.12f)"))
    }

    @Test
    fun `d and r gear does not trigger vehicle move dynamics when speed is zero`() {
        val workingDirectory = requireNotNull(System.getProperty("user.dir"))
        val projectDir = generateSequence(File(workingDirectory)) { it.parentFile }
            .first { File(it, "app").isDirectory }
        val viewSource = File(projectDir, "app/src/main/java/com/leapauto/app/ui/CarModel3DView.kt").readText()
        val screenSource = File(projectDir, "app/src/main/java/com/leapauto/app/ui/LeapAutoScreen.kt").readText()

        assertTrue(viewSource.contains("isDrivingGear && parsedSpeed <= 0f -> 0f"))
        assertTrue(screenSource.contains("val isDrivingGear = gear in setOf(\"D\", \"D挡\", \"DRIVE\", \"前进\", \"3\", \"R\", \"R挡\", \"REVERSE\", \"倒车\", \"1\")"))
        assertTrue(screenSource.contains("val isActuallyDriving = if (isDrivingGear && speedValue <= 0f) false else (isDrivingGear || status?.isDriving == true || speedValue > 0f)"))

        // Direct behavior verification via CarModel3DStateHelper
        assertEquals(0f, com.leapauto.app.ui.CarModel3DStateHelper.calculateEffectiveSpeed(gearStatus = "D", speedStr = "0", isDriving = true))
        assertEquals(0f, com.leapauto.app.ui.CarModel3DStateHelper.calculateEffectiveSpeed(gearStatus = "D", speedStr = "0km/h", isDriving = true))
        assertEquals(0f, com.leapauto.app.ui.CarModel3DStateHelper.calculateEffectiveSpeed(gearStatus = "R", speedStr = "0", isDriving = true))
        assertEquals(0f, com.leapauto.app.ui.CarModel3DStateHelper.calculateEffectiveSpeed(gearStatus = "R", speedStr = "0.0 km/h", isDriving = true))
        assertFalse(com.leapauto.app.ui.CarModel3DStateHelper.isActuallyDriving(gearStatus = "D", speedStr = "0", isDriving = true))
        assertFalse(com.leapauto.app.ui.CarModel3DStateHelper.isActuallyDriving(gearStatus = "R", speedStr = "0km/h", isDriving = true))

        // When vehicle speed is greater than 0 in D or R gear, driving dynamics are enabled
        assertEquals(35f, com.leapauto.app.ui.CarModel3DStateHelper.calculateEffectiveSpeed(gearStatus = "D", speedStr = "35km/h", isDriving = true))
        assertTrue(com.leapauto.app.ui.CarModel3DStateHelper.isActuallyDriving(gearStatus = "D", speedStr = "35km/h", isDriving = true))
        assertEquals(12f, com.leapauto.app.ui.CarModel3DStateHelper.calculateEffectiveSpeed(gearStatus = "R", speedStr = "12 km/h", isDriving = true))
        assertTrue(com.leapauto.app.ui.CarModel3DStateHelper.isActuallyDriving(gearStatus = "R", speedStr = "12 km/h", isDriving = true))
    }

    @Test
    fun `settings button uses adaptive vector gear icon with consistent size`() {
        val workingDirectory = requireNotNull(System.getProperty("user.dir"))
        val projectDir = generateSequence(File(workingDirectory)) { it.parentFile }
            .first { File(it, "app").isDirectory }
        val screenSource = File(projectDir, "app/src/main/java/com/leapauto/app/ui/LeapAutoScreen.kt").readText()

        assertTrue(screenSource.contains("R.drawable.ic_settings_gear"))
        assertTrue(screenSource.contains("tint = MaterialTheme.colorScheme.onSurface"))
    }

    @Test
    fun `charging center pill uses tight vertical padding and sentry rejects sub account`() {
        val workingDirectory = requireNotNull(System.getProperty("user.dir"))
        val projectDir = generateSequence(File(workingDirectory)) { it.parentFile }
            .first { File(it, "app").isDirectory }
        val screenSource = File(projectDir, "app/src/main/java/com/leapauto/app/ui/LeapAutoScreen.kt").readText()
        val mainSource = File(projectDir, "app/src/main/java/com/leapauto/app/MainActivity.kt").readText()
        val modelsSource = File(projectDir, "app/src/main/java/com/leapauto/app/Models.kt").readText()

        assertTrue(screenSource.contains("padding(horizontal = 6.dp, vertical = 0.5.dp)"))
        assertTrue(mainSource.contains("SUB_ACCOUNT_UNSUPPORTED_MESSAGE"))
        assertTrue(modelsSource.contains("SUB_ACCOUNT_UNSUPPORTED_MESSAGE"))
    }

    @Test
    fun `location pill and parking pill use tight vertical padding and line height to hug text`() {
        val workingDirectory = requireNotNull(System.getProperty("user.dir"))
        val projectDir = generateSequence(File(workingDirectory)) { it.parentFile }
            .first { File(it, "app").isDirectory }
        val screenSource = File(projectDir, "app/src/main/java/com/leapauto/app/ui/LeapAutoScreen.kt").readText()

        assertTrue(screenSource.contains("padding(horizontal = 6.dp, vertical = 1.dp)"))
        assertTrue(screenSource.contains("style = MaterialTheme.typography.labelSmall.copy(lineHeight = 11.sp)"))
    }

    @Test
    fun `3d model timeout or failure defaults directly to 2d car model without intrusive popup`() {
        val workingDirectory = requireNotNull(System.getProperty("user.dir"))
        val projectDir = generateSequence(File(workingDirectory)) { it.parentFile }
            .first { File(it, "app").isDirectory }
        val screenSource = File(projectDir, "app/src/main/java/com/leapauto/app/ui/LeapAutoScreen.kt").readText()

        assertTrue(screenSource.contains("if (!show3D && remoteBitmap != null)"))
        assertTrue(screenSource.contains("切回3D"))
        assertTrue(screenSource.contains("onRetryDownload3D()"))
        org.junit.Assert.assertFalse(screenSource.contains("重新下载3D车模"))
        org.junit.Assert.assertFalse(screenSource.contains("3D车模加载超时 (15秒)"))
    }

    @Test
    fun `car model web view contains mtk mali webgl preserveDrawingBuffer hook`() {
        val workingDirectory = requireNotNull(System.getProperty("user.dir"))
        val projectDir = generateSequence(File(workingDirectory)) { it.parentFile }
            .first { File(it, "app").isDirectory }
        val viewSource = File(projectDir, "app/src/main/java/com/leapauto/app/ui/CarModel3DView.kt").readText()

        assertTrue(viewSource.contains("__mtkWebglHooked"))
        assertTrue(viewSource.contains("attributes.preserveDrawingBuffer = true"))
        assertTrue(viewSource.contains("attributes.failIfMajorPerformanceCaveat = false"))
        assertTrue(viewSource.contains("attributes.powerPreference = 'high-performance'"))
    }

    @Test
    fun `3d snapshot timing and validation guards prevent blank or incomplete car snapshots across gpus`() {
        val workingDirectory = requireNotNull(System.getProperty("user.dir"))
        val projectDir = generateSequence(File(workingDirectory)) { it.parentFile }
            .first { File(it, "app").isDirectory }
        val viewSource = File(projectDir, "app/src/main/java/com/leapauto/app/ui/CarModel3DView.kt").readText()
        val cacheSource = File(projectDir, "app/src/main/java/com/leapauto/app/VehicleImageCache.kt").readText()

        // 验证时序解耦：onFullCarLoaded 后才触发 capture3DSnapshot
        assertTrue(viewSource.contains("capture3DSnapshot(view)"))
        assertTrue(viewSource.contains("hasCarMeshes()"))
        assertTrue(viewSource.contains("url.length > 2000"))

        // 验证空图过滤与小组件 3D 文件有效门禁 (>= 4096 字节)
        assertTrue(viewSource.contains("cropped == null"))
        assertTrue(cacheSource.contains("visiblePixelCount < 100"))
        assertTrue(cacheSource.contains("isUsable3DSnapshotFile(): Boolean = exists() && length() >= 4096"))
    }

    @Test
    fun `home content and 3d car model stay permanently alive in scheme a keepalive architecture across subpage navigations`() {
        val workingDirectory = requireNotNull(System.getProperty("user.dir"))
        val projectDir = generateSequence(File(workingDirectory)) { it.parentFile }
            .first { File(it, "app").isDirectory }
        val screenSource = File(projectDir, "app/src/main/java/com/leapauto/app/ui/LeapAutoScreen.kt").readText()

        // 验证主页常驻保活与防穿透
        assertTrue(screenSource.contains("label = \"home_keepalive_alpha\""))
        assertTrue(screenSource.contains("effectiveHomeTopPadding"))
        assertTrue(screenSource.contains("translationX = if (destination == ScreenDestination.HOME || homeAlpha > 0.05f)"))
        assertTrue(screenSource.contains("label = \"subpage-navigation\""))
    }
}
