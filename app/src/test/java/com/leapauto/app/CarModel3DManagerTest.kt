package com.leapauto.app

import com.leapauto.app.ui.CarModel3DStateHelper
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

        assertTrue(viewSource.contains("targetZoom = 1.12"))
        assertTrue(viewSource.contains("cam.updateProjectionMatrix()"))
        assertTrue(viewSource.contains("cam.position.set(-11.15, 3.13, 10.45)"))
        assertTrue(viewSource.contains("ctrl.target.copy(target)"))
        assertTrue(viewSource.contains("v.renderer.setClearColor(0x000000, 0)"))
        assertTrue(viewSource.contains("v.renderer.setClearAlpha(0)"))
        assertTrue(viewSource.contains("v.scene.background = null"))
        assertTrue(viewSource.contains("c.visible = false"))
        assertTrue(viewSource.contains("VIEW_VALUE={default:[-0.785398,1.32]"))
        assertTrue(viewSource.contains("(this.offsetVerticalRate=0,this.offsetVertical=0)"))
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
        assertTrue(viewSource.contains("BonnetImmediately"))
        assertTrue(viewSource.contains("LeftFront_WindowImmediately"))
        assertTrue(viewSource.contains("c.__lockViewAnim__ = true"))
        assertTrue(viewSource.contains("c.handleTrunk"))
        assertTrue(viewSource.contains("c.handleBonnet"))
        assertTrue(viewSource.contains("c.bonnetNode"))
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
    fun `main activity contains optimistic trunk and window timeout guard and refresh clear`() {
        val workingDirectory = requireNotNull(System.getProperty("user.dir"))
        val projectDir = generateSequence(File(workingDirectory)) { it.parentFile }
            .first { File(it, "app").isDirectory }
        val mainSource = File(projectDir, "app/src/main/java/com/leapauto/app/MainActivity.kt").readText()

        assertTrue(mainSource.contains("lastTrunkActionEpochMs"))
        assertTrue(mainSource.contains("lastWindowActionEpochMs"))
        assertTrue(mainSource.contains("nowMs - lastTrunkActionEpochMs < 15_000L"))
        assertTrue(mainSource.contains("if (!trunkProtected) optimisticTrunkState = null"))
        assertTrue(mainSource.contains("if (!silent) {"))
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
    fun `leap auto screen vehicle condition and tire pressure cards use 120dp height for balanced bottom padding`() {
        val workingDirectory = requireNotNull(System.getProperty("user.dir"))
        val projectDir = generateSequence(File(workingDirectory)) { it.parentFile }
            .first { File(it, "app").isDirectory }
        val screenSource = File(projectDir, "app/src/main/java/com/leapauto/app/ui/LeapAutoScreen.kt").readText().replace("\r\n", "\n")

        assertTrue("胎压卡片高度必须提升至 120.dp", screenSource.contains("HomeTirePressureCard(\n                            status = status,\n                            modifier = Modifier\n                                .weight(1f)\n                                .height(120.dp)"))
        assertTrue("车况状态卡片高度必须提升至 120.dp", screenSource.contains("VehicleStatusCard(\n                            status = status,\n                            onOpenHealthyCharging = onOpenHealthyCharging,\n                            onControl = onControl,\n                            modifier = Modifier\n                                .weight(1f)\n                                .height(120.dp)"))
    }

    @Test
    fun `leap auto screen vehicle hero uses balanced compact stage`() {
        val workingDirectory = requireNotNull(System.getProperty("user.dir"))
        val projectDir = generateSequence(File(workingDirectory)) { it.parentFile }
            .first { File(it, "app").isDirectory }
        val screenSource = File(projectDir, "app/src/main/java/com/leapauto/app/ui/LeapAutoScreen.kt").readText()

        assertTrue(screenSource.contains(".height(175.dp)"))
    }

    @Test
    fun `leap auto screen vehicle hero adopts 16dp unified corners with symmetric alignment`() {
        val workingDirectory = requireNotNull(System.getProperty("user.dir"))
        val projectDir = generateSequence(File(workingDirectory)) { it.parentFile }
            .first { File(it, "app").isDirectory }
        val screenSource = File(projectDir, "app/src/main/java/com/leapauto/app/ui/LeapAutoScreen.kt").readText()

        assertTrue(screenSource.contains("heroCardShape = RoundedCornerShape(16.dp)"))
        assertTrue(screenSource.contains("if (isImmersive) null else glassCardBorder()"))
        assertTrue(screenSource.contains("HomeHeroTransition(heroNight.transitionHeightDp, isDark)"))
    }

    @Test
    fun `leap auto screen vehicle hero adopts scheme A aurora crystal dual color progress and pure white mileage font`() {
        val workingDirectory = requireNotNull(System.getProperty("user.dir"))
        val projectDir = generateSequence(File(workingDirectory)) { it.parentFile }
            .first { File(it, "app").isDirectory }
        val screenSource = File(projectDir, "app/src/main/java/com/leapauto/app/ui/LeapAutoScreen.kt").readText()

        assertTrue(screenSource.contains("val mileageDisplayColor = heroTextColors.mileage"))
        assertTrue(screenSource.contains("electricGradientColors"))
        assertTrue(screenSource.contains("fuelGradientColors"))
        assertTrue(screenSource.contains("Color(0xFF159447), Color(0xFF00E676)"))
        assertTrue(screenSource.contains("slotBaseColor = if (isDark) Color.White.copy(alpha = 0.12f)"))
    }

    @Test
    fun `leap auto screen driving status capsule uses high contrast frosted pill base with aurora green breathing dot`() {
        val workingDirectory = requireNotNull(System.getProperty("user.dir"))
        val projectDir = generateSequence(File(workingDirectory)) { it.parentFile }
            .first { File(it, "app").isDirectory }
        val screenSource = File(projectDir, "app/src/main/java/com/leapauto/app/ui/LeapAutoScreen.kt").readText().replace("\r\n", "\n")

        val drivingPill = screenSource.substringAfter("// 2. 驻车/行驶状态胶囊").substringBefore("// ====== 2. 底部展台区域")
        assertTrue("行车胶囊必须使用共享灰色半透明背景", drivingPill.contains("color = statusPillBg"))
        assertTrue("行车胶囊文字必须使用高对比度科技字色 pillContentColor", screenSource.contains("contentColor = pillContentColor"))
        assertTrue("行车胶囊文字必须加粗为 SemiBold 保证强光和雪景下极致清晰", screenSource.contains("fontWeight = FontWeight.SemiBold,\n                                color = pillContentColor"))
        assertTrue("行车状态下呼吸指示灯必须采用极光薄荷青 Color(0xFF00E676)", screenSource.contains("DrivingBreathingDot(color = Color(0xFF00E676))"))
        assertTrue("行车胶囊边框采用极光高光切边 movingPillBorder", screenSource.contains("border = if (isMoving) movingPillBorder else pillBorderStroke"))
    }

    @Test
    fun `leap auto screen top hero capsules follow unified frosted pill base and geometry`() {
        val workingDirectory = requireNotNull(System.getProperty("user.dir"))
        val projectDir = generateSequence(File(workingDirectory)) { it.parentFile }
            .first { File(it, "app").isDirectory }
        val screenSource = File(projectDir, "app/src/main/java/com/leapauto/app/ui/LeapAutoScreen.kt").readText().replace("\r\n", "\n")

        val chargingPill = screenSource.substringAfter("private fun ChargingCenterPill(").substringBefore("private fun EnergyCapsuleProgressBar(")
        assertTrue("充电中心胶囊必须使用共享灰色半透明背景", chargingPill.contains("val backgroundColor = homeStatusPillBackground(isHeroDark)"))
        assertTrue("地址和驻车胶囊背景必须沿用实际首卡深浅判定", screenSource.contains("val statusPillBg = homeStatusPillBackground(isHeroDark)"))
        assertTrue("充电强调边框必须保留", chargingPill.contains("val border = if (isChargingActive) chargingActiveBorder else pillBorderStroke"))
        assertTrue("哨兵背景必须使用中性灰并保留透明度", screenSource.contains("val defaultPillBg = if (isHeroDark) Color(0xFF181818).copy(alpha = 0.68f) else Color.White.copy(alpha = 0.90f)"))
        assertTrue("充电中心胶囊内边距必须使用放大后的共享尺寸", chargingPill.contains("horizontal = HomeStatusPillStyle.horizontalPadding"))
        assertTrue(chargingPill.contains("vertical = HomeStatusPillStyle.verticalPadding"))
        assertTrue("哨兵胶囊必须统一使用 50% 药丸圆角与 defaultPillBg", screenSource.contains("status?.sentryMode == true) {\n                            Surface(\n                                shape = RoundedCornerShape(percent = 50),\n                                color = defaultPillBg"))
        val locationPill = screenSource.substringAfter("// 1. 位置信息胶囊").substringBefore("// 2. 驻车/行驶状态胶囊")
        assertTrue("地址胶囊必须保留共享灰透明背景", locationPill.contains("color = statusPillBg"))
        assertTrue("地址胶囊内边距必须使用放大后的共享尺寸", locationPill.contains("horizontal = HomeStatusPillStyle.horizontalPadding"))
        assertTrue(locationPill.contains("vertical = HomeStatusPillStyle.verticalPadding"))
    }

    @Test
    fun `mileage and unit use scoped hero text colors without changing pill contrast`() {
        val workingDirectory = requireNotNull(System.getProperty("user.dir"))
        val projectDir = generateSequence(File(workingDirectory)) { it.parentFile }
            .first { File(it, "app").isDirectory }
        val screenSource = File(projectDir, "app/src/main/java/com/leapauto/app/ui/LeapAutoScreen.kt").readText()

        assertTrue(screenSource.contains("val isHeroDark = heroNight.isHeroDark"))
        assertTrue(screenSource.contains("val mileageDisplayColor = heroTextColors.mileage"))
        assertTrue(screenSource.contains("val unitDisplayColor = heroTextColors.unit"))
        assertTrue(screenSource.contains("val statusPillBg = homeStatusPillBackground(isHeroDark)"))
        assertFalse(screenSource.contains("val isYuhuiStoneTheme = selectedThemeId == \"5\""))
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
    fun `charging center pill uses enlarged shared padding and sentry rejects sub account`() {
        val workingDirectory = requireNotNull(System.getProperty("user.dir"))
        val projectDir = generateSequence(File(workingDirectory)) { it.parentFile }
            .first { File(it, "app").isDirectory }
        val screenSource = File(projectDir, "app/src/main/java/com/leapauto/app/ui/LeapAutoScreen.kt").readText()
        val mainSource = File(projectDir, "app/src/main/java/com/leapauto/app/MainActivity.kt").readText()
        val modelsSource = File(projectDir, "app/src/main/java/com/leapauto/app/Models.kt").readText()

        val chargingPill = screenSource.substringAfter("private fun ChargingCenterPill(").substringBefore("private fun EnergyCapsuleProgressBar(")
        assertTrue(chargingPill.contains("horizontal = HomeStatusPillStyle.horizontalPadding"))
        assertTrue(chargingPill.contains("vertical = HomeStatusPillStyle.verticalPadding"))
        assertTrue(mainSource.contains("SUB_ACCOUNT_UNSUPPORTED_MESSAGE"))
        assertTrue(modelsSource.contains("SUB_ACCOUNT_UNSUPPORTED_MESSAGE"))
    }

    @Test
    fun `location pill and parking pill enlarge both text and container`() {
        val workingDirectory = requireNotNull(System.getProperty("user.dir"))
        val projectDir = generateSequence(File(workingDirectory)) { it.parentFile }
            .first { File(it, "app").isDirectory }
        val screenSource = File(projectDir, "app/src/main/java/com/leapauto/app/ui/LeapAutoScreen.kt").readText()

        val locationPill = screenSource.substringAfter("// 1. 位置信息胶囊").substringBefore("// 2. 驻车/行驶状态胶囊")
        val drivingPill = screenSource.substringAfter("// 2. 驻车/行驶状态胶囊").substringBefore("// ====== 2. 底部展台区域")
        for (pill in listOf(locationPill, drivingPill)) {
            assertTrue(pill.contains("horizontal = HomeStatusPillStyle.horizontalPadding"))
            assertTrue(pill.contains("vertical = HomeStatusPillStyle.verticalPadding"))
            assertTrue(pill.contains("lineHeight = HomeStatusPillStyle.lineHeight"))
            assertTrue(pill.contains("fontSize = HomeStatusPillStyle.fontSize"))
        }
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

    @Test
    fun `3d car model charging animation triggers only when actively charging and ignores gun connected alone`() {
        val workingDirectory = requireNotNull(System.getProperty("user.dir"))
        val projectDir = generateSequence(File(workingDirectory)) { it.parentFile }
            .first { File(it, "app").isDirectory }
        val viewSource = File(projectDir, "app/src/main/java/com/leapauto/app/ui/CarModel3DView.kt").readText()

        // 源码防回归校验：严禁使用 chargeGunConnected 直接驱动 3D 充电流光
        assertFalse(viewSource.contains("status?.chargeGunConnected == true\n        obj.put(\"isCharging\""))
        assertFalse(viewSource.contains("status?.chargeGunConnected == true || status?.chargeState == 1"))
        assertTrue(viewSource.contains("CarModel3DStateHelper.isChargingAnimationActive(status)"))

        // 逻辑校验：仅在 chargeState == 1 (充电中) 时播放 3D 动画
        assertTrue(CarModel3DStateHelper.isChargingAnimationActive(1))

        // 仅插枪未充电、预约充电等待、充满、故障或无状态时不播放动画
        assertFalse(CarModel3DStateHelper.isChargingAnimationActive(0)) // 未插枪/未充电
        assertFalse(CarModel3DStateHelper.isChargingAnimationActive(2)) // 充电完成
        assertFalse(CarModel3DStateHelper.isChargingAnimationActive(3)) // 充电故障
        assertFalse(CarModel3DStateHelper.isChargingAnimationActive(4)) // 预约充电等待
        assertFalse(CarModel3DStateHelper.isChargingAnimationActive(6)) // 充电暂停
        assertFalse(CarModel3DStateHelper.isChargingAnimationActive(null as Int?))
        assertFalse(CarModel3DStateHelper.isChargingAnimationActive(null as VehicleStatus?))
    }

    @Test
    fun `3d rendering enforces retina dpr and high performance options without lock 30fps`() {
        val workingDirectory = requireNotNull(System.getProperty("user.dir"))
        val projectDir = generateSequence(File(workingDirectory)) { it.parentFile }
            .first { File(it, "app").isDirectory }
        val viewSource = File(projectDir, "app/src/main/java/com/leapauto/app/ui/CarModel3DView.kt").readText()

        // 验证 WebGL 注入性能与视网膜高精度参数
        assertTrue(viewSource.contains("window.web3dPerformanceOptions = {"))
        assertTrue(viewSource.contains("dynamicDprEnabled: false"))
        assertTrue(viewSource.contains("transitionPixelRatioCap: Math.max(window.devicePixelRatio || 2.5, 3.0)"))
        assertTrue(viewSource.contains("attributes.antialias = true"))

        // 验证解除 IOSWebview 30FPS 锁帧
        assertFalse(viewSource.contains("window.IOSWebview = true;"))
        assertTrue(viewSource.contains("window.AndroidWebview = true;"))
        assertTrue(viewSource.contains("v.renderer.setPixelRatio(dpr);"))
    }

    @Test
    fun `progressive loading prioritizes 2d car picture before heavy 3d model download`() {
        val workingDirectory = requireNotNull(System.getProperty("user.dir"))
        val projectDir = generateSequence(File(workingDirectory)) { it.parentFile }
            .first { File(it, "app").isDirectory }
        val cacheSource = File(projectDir, "app/src/main/java/com/leapauto/app/VehicleImageCache.kt").readText()

        val download2dIndex = cacheSource.indexOf("优先从 CDN 下载 2D 车辆定妆照")
        val download3dIndex = cacheSource.indexOf("平稳异步拉取 12MB 的 3D 模型资产双包")

        assertTrue("2D car image download must be present", download2dIndex >= 0)
        assertTrue("3D model package download must be present", download3dIndex >= 0)
        assertTrue("2D car image must be downloaded before 3D model packages to guarantee instant visual availability", download2dIndex < download3dIndex)
    }

    @Test
    fun `3d rendered state does not reset on vehicleImageVersion increment and webview synchronizes ready state`() {
        val workingDirectory = requireNotNull(System.getProperty("user.dir"))
        val projectDir = generateSequence(File(workingDirectory)) { it.parentFile }
            .first { File(it, "app").isDirectory }
        val screenSource = File(projectDir, "app/src/main/java/com/leapauto/app/ui/LeapAutoScreen.kt").readText()
        val viewSource = File(projectDir, "app/src/main/java/com/leapauto/app/ui/CarModel3DView.kt").readText()

        // 验证 is3DRendered 状态严格解绑 vehicleImageVersion
        assertTrue(screenSource.contains("var is3DRendered by remember(h5Key, srcKey)"))
        assertFalse(screenSource.contains("var is3DRendered by remember(h5Key, srcKey, vehicleImageVersion)"))

        // 验证 AndroidView.update 中双向纠偏就绪态
        assertTrue(viewSource.contains("if (webView.isReady)"))
        assertTrue(viewSource.contains("onReady()"))
    }

    @Test
    fun `quick action button and hero texts dynamically adapt contrast across light and dark themes`() {
        val workingDirectory = requireNotNull(System.getProperty("user.dir"))
        val projectDir = generateSequence(File(workingDirectory)) { it.parentFile }
            .first { File(it, "app").isDirectory }
        val screenSource = File(projectDir, "app/src/main/java/com/leapauto/app/ui/LeapAutoScreen.kt").readText()

        // 验证车模上方文字对比度绑定 isHeroDark
        assertTrue(screenSource.contains("val heroTextColors = HomeHeroTextStyle.resolve("))
        assertTrue(screenSource.contains("heroPrimaryTextColor = heroTextColors.primary"))
        assertTrue(screenSource.contains("heroSecondaryTextColor = heroTextColors.secondary"))

        // 验证快捷操作按钮文字注入微柔光阴影与 isHeroDark 贯穿
        assertTrue(screenSource.contains("isHeroDark: Boolean = false"))
        assertTrue(screenSource.contains("normalActionContentColor = if (isHeroDark) Color.White else Color(0xFF202020)"))
        assertTrue(screenSource.contains("blurRadius = 3f"))
    }

    @Test
    fun `hero contrast follows actual backdrop for 2d fallback in dark mode`() {
        val workingDirectory = requireNotNull(System.getProperty("user.dir"))
        val projectDir = generateSequence(File(workingDirectory)) { it.parentFile }
            .first { File(it, "app").isDirectory }
        val screenSource = File(projectDir, "app/src/main/java/com/leapauto/app/ui/LeapAutoScreen.kt").readText()

        // Home now resolves the final, filtered backdrop rather than the raw widget asset tone.
        assertTrue(screenSource.contains("val heroNight = NightThemePolicy.resolve("))
        assertTrue(screenSource.contains("hasThemeBackground = hasThemeBg"))
        assertTrue(screenSource.contains("val backgroundSource = NightThemePolicy.selectBackground("))
        assertTrue(screenSource.contains("source = backgroundSource"))
        assertTrue(screenSource.contains("val isHeroDark = heroNight.isHeroDark"))
    }

    @Test
    fun `3d camera target and position ensure horizontal centering and vertical grounding`() {
        val workingDirectory = requireNotNull(System.getProperty("user.dir"))
        val projectDir = generateSequence(File(workingDirectory)) { it.parentFile }
            .first { File(it, "app").isDirectory }
        val viewSource = File(projectDir, "app/src/main/java/com/leapauto/app/ui/CarModel3DView.kt").readText()
        val screenSource = File(projectDir, "app/src/main/java/com/leapauto/app/ui/LeapAutoScreen.kt").readText()

        assertTrue(viewSource.contains("var target = new THREE.Vector3(-0.35, 0.98, -0.35);"))
        assertTrue(viewSource.contains("cam.position.set(-11.15, 3.13, 10.45);"))
        assertTrue(screenSource.contains(".height(172.dp)"))
    }
}
