package com.leapauto.app

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ThemeManagerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `sanitizeKey sanitizes special characters`() {
        assertEquals("theme_7", ThemeManager.sanitizeKey("theme-7"))
        assertEquals("20260920004", ThemeManager.sanitizeKey("20260920004"))
        assertEquals("custom_v1_0", ThemeManager.sanitizeKey("custom/v1.0"))
    }

    @Test
    fun `preset themes list contains 4 official fallback themes`() {
        val themes = ThemeManager.PRESET_THEMES
        assertEquals(4, themes.size)
        val defaultTheme = themes.first()
        assertEquals(ThemeManager.DEFAULT_THEME_ID, defaultTheme.themeId)
        assertEquals("7", defaultTheme.themeId)
        assertTrue(defaultTheme.isDefault)
        assertEquals("雾川雪境", defaultTheme.name)

        val cengLuan = themes.find { it.themeId == "4" }
        assertNotNull(cengLuan)
        assertEquals("层峦叠嶂", cengLuan?.name)
        assertFalse(cengLuan?.isDefault == true)

        val wuchuan = themes.find { it.themeId == "7" }
        assertNotNull(wuchuan)
        assertEquals("雾川雪境", wuchuan?.name)
        assertEquals("e3a513f907c4df15b942d177dcf04547", wuchuan?.fileMd5)

        val yuhui = themes.find { it.themeId == "5" }
        assertNotNull(yuhui)
        assertEquals("余晖石筑", yuhui?.name)

        val qingkong = themes.find { it.themeId == "8" }
        assertNotNull(qingkong)
        assertEquals("晴空广场", qingkong?.name)
    }

    @Test
    fun `findThemeFile resolves files directly or in resource subfolder`() {
        val themeDir = tempFolder.newFolder("theme_test")
        assertNull(ThemeManager.findThemeFile(themeDir, "lpcar_pic_3d_bg.png"))

        // Case 1: In resource subfolder
        val resDir = File(themeDir, "resource").apply { mkdirs() }
        val subFile = File(resDir, "lpcar_pic_3d_bg.png").apply { writeText("image_data") }
        val foundSub = ThemeManager.findThemeFile(themeDir, "lpcar_pic_3d_bg.png")
        assertNotNull(foundSub)
        assertEquals(subFile.absolutePath, foundSub?.absolutePath)

        // Case 2: Directly in root folder
        val directFile = File(themeDir, "direct_bg.png").apply { writeText("direct_data") }
        val foundDirect = ThemeManager.findThemeFile(themeDir, "direct_bg.png")
        assertNotNull(foundDirect)
        assertEquals(directFile.absolutePath, foundDirect?.absolutePath)

        // Case 3: Priority - resource subfolder takes precedence over small root file
        val rootSmall = File(themeDir, "lpcar_pic_3d_bg.png").apply { writeText("small_blurred_thumb") }
        val foundPreferSub = ThemeManager.findThemeFile(themeDir, "lpcar_pic_3d_bg.png")
        assertNotNull(foundPreferSub)
        assertEquals(subFile.absolutePath, foundPreferSub?.absolutePath)
    }

    @Test
    fun `isThemeReady returns true for original skybox theme and checks ready marker for custom themes`() {
        val baseDir = tempFolder.newFolder("CarThemes")
        // Original skybox theme (4) is always ready
        assertTrue(ThemeManager.isThemeReady(baseDir, ThemeManager.ORIGINAL_SKYBOX_THEME_ID))

        val customDir = File(baseDir, "7")
        customDir.mkdirs()
        // Not ready without .ready marker
        val bgFile = File(customDir, "resource/lpcar_pic_3d_bg.png").apply {
            parentFile?.mkdirs()
            writeText("dummy_bytes")
        }
        assertFalse(ThemeManager.isThemeReady(baseDir, "7"))

        File(customDir, ".ready").createNewFile()
        assertTrue(ThemeManager.isThemeReady(baseDir, "7"))
    }

    @Test
    fun `vehicle theme fromJson correctly parses real packet data`() {
        val jsonStr = """
        {
          "themeId": "7",
          "themeCode": "20260920004",
          "name": "雾川雪镜",
          "description": "云雾缭绕的巍峨雪山与静谧冰河相映，宛如一幅空灵悠远的冬日水墨画卷。",
          "tags": ["主题上新"],
          "resource": {
            "affiliatedResourceType": 0,
            "resourceBg": "http://lp-carnet.oss-cn-hangzhou.aliyuncs.com/theme/7/bg.png",
            "resourceBgName": "bg.png",
            "affiliatedResource": [
              {
                "name": "首页",
                "preview": "http://lp-carnet.oss-cn-hangzhou.aliyuncs.com/theme/7/home_preview.png",
                "thumbnail": "http://lp-carnet.oss-cn-hangzhou.aliyuncs.com/theme/7/home_thumb.png"
              },
              {
                "name": "小组件",
                "preview": "http://lp-carnet.oss-cn-hangzhou.aliyuncs.com/theme/7/widget_preview.png",
                "thumbnail": "http://lp-carnet.oss-cn-hangzhou.aliyuncs.com/theme/7/widget_thumb.png"
              }
            ],
            "resourcePkg": "http://lp-carnet.oss-cn-hangzhou.aliyuncs.com/theme/7/pkg.zip",
            "resourcePkgName": "resource - 雾川雪境.zip",
            "fileSize": 2199406,
            "fileMd5": "e3a513f907c4df15b942d177dcf04547"
          },
          "status": "ONLINE",
          "isDefault": false
        }
        """.trimIndent()

        val theme = VehicleTheme.fromJson(JSONObject(jsonStr))
        assertEquals("7", theme.themeId)
        assertEquals("20260920004", theme.themeCode)
        assertEquals("雾川雪境", theme.name)
        assertTrue(theme.isDefault)
        assertEquals(listOf("主题上新"), theme.tags)
        assertEquals("http://lp-carnet.oss-cn-hangzhou.aliyuncs.com/theme/7/bg.png", theme.resourceBg)
        assertEquals("http://lp-carnet.oss-cn-hangzhou.aliyuncs.com/theme/7/pkg.zip", theme.resourcePkg)
        assertEquals("resource - 雾川雪境.zip", theme.resourcePkgName)
        assertEquals(2199406L, theme.fileSize)
        assertEquals("e3a513f907c4df15b942d177dcf04547", theme.fileMd5)
        assertEquals("http://lp-carnet.oss-cn-hangzhou.aliyuncs.com/theme/7/home_preview.png", theme.homePreview)
        assertEquals("http://lp-carnet.oss-cn-hangzhou.aliyuncs.com/theme/7/home_thumb.png", theme.homeThumbnail)
        assertEquals("http://lp-carnet.oss-cn-hangzhou.aliyuncs.com/theme/7/widget_preview.png", theme.widgetPreview)
        assertEquals("http://lp-carnet.oss-cn-hangzhou.aliyuncs.com/theme/7/widget_thumb.png", theme.widgetThumbnail)
    }

    @Test
    fun `vehicle theme fromJson maps theme 4 to ceng luan die zhang and removes isDefault`() {
        val theme4Json = JSONObject().apply {
            put("themeId", "4")
            put("name", "默认主题")
            put("isDefault", true)
        }
        val theme4 = VehicleTheme.fromJson(theme4Json)
        assertEquals("层峦叠嶂", theme4.name)
        assertFalse(theme4.isDefault)
    }

    @Test
    fun `vehicle picture meta extracts h5Whole srcWhole and currentTheme`() {
        val jsonStr = """
        {
          "h5Key": "3D-a6d2ca11-8388-4371-a308-72b2d86482e4",
          "srcKey": "3D-d3e0fbce-0755-441e-8381-7512fd49bc70",
          "h5Whole": "BG5gS2SQwHKC/5v2yDk/Ew==",
          "srcWhole": "pqxd6vFtMpktN6IfOpw9wA==",
          "shareBindUrl": "https://cdn.leapmotor.com/car.png",
          "currentTheme": {
            "themeId": "7",
            "themeCode": "20260920004",
            "name": "雾川雪镜",
            "description": "雪山冰河",
            "isDefault": false
          }
        }
        """.trimIndent()

        val meta = VehiclePictureMeta(
            pictureKey = "key_test",
            shareBindUrl = "https://cdn.leapmotor.com/car.png",
            rawData = JSONObject(jsonStr)
        )

        assertEquals("3D-a6d2ca11-8388-4371-a308-72b2d86482e4", meta.h5Key)
        assertEquals("3D-d3e0fbce-0755-441e-8381-7512fd49bc70", meta.srcKey)
        assertEquals("BG5gS2SQwHKC/5v2yDk/Ew==", meta.h5Whole)
        assertEquals("pqxd6vFtMpktN6IfOpw9wA==", meta.srcWhole)
        assertNotNull(meta.currentTheme)
        assertEquals("7", meta.currentTheme?.themeId)
        assertEquals("雾川雪境", meta.currentTheme?.name)
    }

    @Test
    fun `theme tone correctly classifies light and dark themes`() {
        assertEquals(ThemeManager.ThemeTone.LIGHT, ThemeManager.getThemeTone("7"))
        assertEquals(ThemeManager.ThemeTone.LIGHT, ThemeManager.getThemeTone("8"))
        assertEquals(ThemeManager.ThemeTone.LIGHT, ThemeManager.getThemeTone("5"))
        assertEquals(ThemeManager.ThemeTone.AUTO, ThemeManager.getThemeTone("4"))
        assertEquals(ThemeManager.ThemeTone.AUTO, ThemeManager.getThemeTone(""))
    }

    @Test
    fun `isHeroBackgroundDark correctly determines background luminosity for all themes`() {
        // 雾川雪镜（7）、晴空广场（8）与余晖石筑（5）：背景均为高亮浅色/暖砂背景
        assertFalse(ThemeManager.isHeroBackgroundDark("7", isSystemDark = true))
        assertFalse(ThemeManager.isHeroBackgroundDark("7", isSystemDark = false))
        assertFalse(ThemeManager.isHeroBackgroundDark("8", isSystemDark = true))
        assertFalse(ThemeManager.isHeroBackgroundDark("8", isSystemDark = false))
        assertFalse(ThemeManager.isHeroBackgroundDark("5", isSystemDark = true))
        assertFalse(ThemeManager.isHeroBackgroundDark("5", isSystemDark = false))

        // 默认主题（4）或空：跟随系统
        assertTrue(ThemeManager.isHeroBackgroundDark("4", isSystemDark = true))
        assertFalse(ThemeManager.isHeroBackgroundDark("4", isSystemDark = false))
        assertTrue(ThemeManager.isHeroBackgroundDark("", isSystemDark = true))
        assertFalse(ThemeManager.isHeroBackgroundDark("", isSystemDark = false))
    }

    @Test
    fun `shouldApplyNightMoodFilter applies only to light themes in dark mode`() {
        // 在深色模式下，浅色主题（7、8、5）需要夜巡微暗滤镜
        assertTrue(ThemeManager.shouldApplyNightMoodFilter("7", isSystemDark = true))
        assertTrue(ThemeManager.shouldApplyNightMoodFilter("8", isSystemDark = true))
        assertTrue(ThemeManager.shouldApplyNightMoodFilter("5", isSystemDark = true))

        // 在浅色模式下，不应叠加夜巡微暗滤镜
        assertFalse(ThemeManager.shouldApplyNightMoodFilter("7", isSystemDark = false))
        assertFalse(ThemeManager.shouldApplyNightMoodFilter("8", isSystemDark = false))
        assertFalse(ThemeManager.shouldApplyNightMoodFilter("5", isSystemDark = false))

        assertFalse(ThemeManager.shouldApplyNightMoodFilter("4", isSystemDark = true))
        assertFalse(ThemeManager.shouldApplyNightMoodFilter("4", isSystemDark = false))
    }

    @Test
    fun `login and vehicle switch flows trigger theme sync and ensure active default theme`() {
        val workingDirectory = requireNotNull(System.getProperty("user.dir"))
        val projectDir = generateSequence(File(workingDirectory)) { it.parentFile }
            .first { File(it, "app").isDirectory }
        val mainActivitySource = File(projectDir, "app/src/main/java/com/leapauto/app/MainActivity.kt").readText()

        // 验证冷启动、短信登录、凭据登录、切换车辆四处全覆盖 syncThemesAndEnsureActive
        val matches = "syncThemesAndEnsureActive\\(\\)".toRegex().findAll(mainActivitySource).count()
        assertTrue("syncThemesAndEnsureActive must be invoked across all session and vehicle lifecycle entry points", matches >= 4)
    }

    @Test
    fun `isCurrentVehicle3DSupported global variable and fallback tone routing`() {
        try {
            // 1. 默认为 true
            ThemeManager.isCurrentVehicle3DSupported = true
            assertTrue(ThemeManager.isCurrentVehicle3DSupported)
            assertEquals(ThemeManager.ThemeTone.AUTO, ThemeManager.getThemeTone("4"))

            // 2. 当某个车型不支持时，置为 false
            ThemeManager.isCurrentVehicle3DSupported = false
            assertFalse(ThemeManager.isCurrentVehicle3DSupported)

            // 原厂默认跟随外观，其余旧偏好兼容为内置雪景。
            assertEquals(ThemeManager.ThemeTone.AUTO, ThemeManager.getThemeTone("4"))
            assertEquals(ThemeManager.ThemeTone.AUTO, ThemeManager.getThemeTone(""))
            assertEquals(ThemeManager.ThemeTone.LIGHT, ThemeManager.getThemeTone("5"))
            assertEquals(ThemeManager.ThemeTone.LIGHT, ThemeManager.getThemeTone("7"))
            assertEquals(ThemeManager.ThemeTone.LIGHT, ThemeManager.getThemeTone("8"))

            assertTrue(ThemeManager.isHeroBackgroundDark("4", isSystemDark = true))
            assertFalse(ThemeManager.isHeroBackgroundDark("4", isSystemDark = false))
            assertFalse(ThemeManager.isHeroBackgroundDark("7", isSystemDark = true))
            assertFalse(ThemeManager.shouldApplyNightMoodFilter("4", isSystemDark = true))
            assertTrue(ThemeManager.shouldApplyNightMoodFilter("7", isSystemDark = true))

            // 3. 支持车型切回 true 时，恢复原有逻辑
            ThemeManager.isCurrentVehicle3DSupported = true
            assertTrue(ThemeManager.isCurrentVehicle3DSupported)
            assertEquals(ThemeManager.ThemeTone.AUTO, ThemeManager.getThemeTone("4"))
            assertTrue(ThemeManager.isHeroBackgroundDark("4", isSystemDark = true))
        } finally {
            ThemeManager.isCurrentVehicle3DSupported = true
        }
    }

    @Test
    fun `fallback theme webp assets exist in project assets`() {
        val workingDirectory = requireNotNull(System.getProperty("user.dir"))
        val projectDir = generateSequence(File(workingDirectory)) { it.parentFile }
            .first { File(it, "app").isDirectory }
        val fallbackDir = File(projectDir, "app/src/main/assets/fallback_theme")

        val bg3D = File(fallbackDir, "lpcar_pic_3d_bg.webp")
        val widget2x2 = File(fallbackDir, "lpcar_pic_widget_2x2.webp")
        val widget4x2 = File(fallbackDir, "lpcar_pic_widget_4x2.webp")

        assertTrue("lpcar_pic_3d_bg.webp must exist", bg3D.exists())
        assertTrue("lpcar_pic_3d_bg.webp must be > 50KB", bg3D.length() > 50_000L)

        assertTrue("lpcar_pic_widget_2x2.webp must exist", widget2x2.exists())
        assertTrue("lpcar_pic_widget_2x2.webp must be > 10KB", widget2x2.length() > 10_000L)

        assertTrue("lpcar_pic_widget_4x2.webp must exist", widget4x2.exists())
        assertTrue("lpcar_pic_widget_4x2.webp must be > 10KB", widget4x2.length() > 10_000L)
    }

    @Test
    fun `control widget and compact widget integrate fallback background when 3d not supported`() {
        val workingDirectory = requireNotNull(System.getProperty("user.dir"))
        val projectDir = generateSequence(File(workingDirectory)) { it.parentFile }
            .first { File(it, "app").isDirectory }

        val widgetSource = File(projectDir, "app/src/main/java/com/leapauto/app/ControlWidget.kt").readText()
        assertTrue("ControlWidget must check isVehicle3DSupported", widgetSource.contains("ThemeManager.isVehicle3DSupported"))

        val compactWidgetSource = File(projectDir, "app/src/main/java/com/leapauto/app/CompactControlWidget.kt").readText()
        assertTrue("CompactControlWidget must check isVehicle3DSupported", compactWidgetSource.contains("ThemeManager.isVehicle3DSupported"))

        val screenSource = File(projectDir, "app/src/main/java/com/leapauto/app/ui/LeapAutoScreen.kt").readText()
        assertTrue("LeapAutoScreen must check isVehicle3DSupported", screenSource.contains("ThemeManager.isVehicle3DSupported"))
    }

    @Test
    fun `vehicle 3d support lookup is read only and must not persist or decrypt secrets`() {
        val workingDirectory = requireNotNull(System.getProperty("user.dir"))
        val projectDir = generateSequence(File(workingDirectory)) { it.parentFile }
            .first { File(it, "app").isDirectory }

        val managerSource = File(projectDir, "app/src/main/java/com/leapauto/app/ThemeManager.kt").readText()
        val lookupBody = managerSource
            .substringAfter("fun isVehicle3DSupported(")
            .substringBefore("\n    }")

        assertFalse(
            "the 3D support lookup must not write to SharedPreferences",
            lookupBody.contains(".edit()")
        )
        assertFalse(
            "the 3D support lookup must not load the full session (Keystore decryption)",
            lookupBody.contains("SessionStore(context).load()")
        )
        assertTrue(
            "unknown capability must use metadata policy rather than guessing from model names",
            lookupBody.contains("VehicleThemePolicy.supports3d(null, meta != null, meta?.h5Key)")
        )
        assertFalse(
            "the 3D support lookup must not mutate the global flag",
            lookupBody.contains("isCurrentVehicle3DSupported =")
        )
        assertTrue(
            "switching vehicles must explicitly sync the global flag",
            managerSource.contains("fun updateCurrentVehicle3DSupported(")
        )
    }

    @Test
    fun `hero background tone must be resolved per vehicle instead of the global flag`() {
        val workingDirectory = requireNotNull(System.getProperty("user.dir"))
        val projectDir = generateSequence(File(workingDirectory)) { it.parentFile }
            .first { File(it, "app").isDirectory }

        val screenSource = File(projectDir, "app/src/main/java/com/leapauto/app/ui/LeapAutoScreen.kt").readText()
        val hero = screenSource.substringAfter("fun VehicleHero(").substringBefore("private fun HeroControlHudPill(")
        assertTrue(hero.contains("ThemeManager.isVehicle3DSupported(context, vehicleVin)"))
        assertTrue(hero.contains("ThemeManager.load3dBgBitmap(context, selectedThemeId, vehicleVin)"))
        assertTrue(hero.contains("VehicleThemePolicy.background(is3dSupported, selectedThemeId)"))
        assertTrue(hero.contains("NightThemePolicy.resolve("))
        assertTrue(hero.contains("VehicleThemePolicy.activeThemeId(false, selectedThemeId)"))
        assertTrue(hero.contains("themeId = renderedThemeId"))
        assertTrue(hero.contains("hasThemeBackground = hasThemeBg"))
        assertFalse(hero.contains("ThemeManager.isCurrentVehicle3DSupported"))
    }

    @Test
    fun `session store exposes a lightweight carType reader without keystore decryption`() {
        val workingDirectory = requireNotNull(System.getProperty("user.dir"))
        val projectDir = generateSequence(File(workingDirectory)) { it.parentFile }
            .first { File(it, "app").isDirectory }

        val storeSource = File(projectDir, "app/src/main/java/com/leapauto/app/SessionStore.kt").readText()
        assertTrue("SessionStore must expose loadSelectedCarType", storeSource.contains("fun loadSelectedCarType(): String"))
        val readerBody = storeSource
            .substringAfter("fun loadSelectedCarType(): String")
            .substringBefore("\n")
        assertFalse(
            "loadSelectedCarType must not decrypt secure values",
            readerBody.contains("secureValues")
        )
    }

    @Test
    fun `hero 3d support memoization must refresh after a successful image sync`() {
        val workingDirectory = requireNotNull(System.getProperty("user.dir"))
        val projectDir = generateSequence(File(workingDirectory)) { it.parentFile }
            .first { File(it, "app").isDirectory }

        val screenSource = File(projectDir, "app/src/main/java/com/leapauto/app/ui/LeapAutoScreen.kt").readText()
        val is3dMemo = screenSource
            .substringAfter("val is3dSupported =")
            .substringBefore("\n    }")

        assertTrue(
            "is3dSupported must be remembered per vehicle to avoid repeated preference reads",
            is3dMemo.contains("remember(vehicleVin")
        )
        assertTrue(
            "is3dSupported must also key on vehicleImageVersion so a successful sync can promote 2D to 3D",
            is3dMemo.contains("vehicleImageVersion")
        )
    }

    @Test
    fun `theme switch success prompts dialog with cancel and confirm navigation`() {
        val workingDirectory = requireNotNull(System.getProperty("user.dir"))
        val projectDir = generateSequence(File(workingDirectory)) { it.parentFile }
            .first { File(it, "app").isDirectory }

        val sheetSource = File(projectDir, "app/src/main/java/com/leapauto/app/ui/ThemeSelectorBottomSheet.kt").readText()
        assertTrue("ThemeSelectorBottomSheet must declare showSwitchSuccessDialog", sheetSource.contains("var showSwitchSuccessDialog by remember { mutableStateOf(false) }"))
        assertTrue("ThemeSelectorBottomSheet must show confirmation dialog text", sheetSource.contains("切换成功，是否返回主页查看？"))
        assertTrue("ThemeSelectorBottomSheet must have cancel button", sheetSource.contains("\"取消\""))
        assertTrue("ThemeSelectorBottomSheet must have confirm button", sheetSource.contains("\"确定\""))
        assertTrue("Confirm button must trigger onNavigateToHome", sheetSource.contains("onNavigateToHome()"))

        val screenSource = File(projectDir, "app/src/main/java/com/leapauto/app/ui/LeapAutoScreen.kt").readText()
        assertTrue("LeapAutoScreen must route onNavigateToHome to vehicle tab", screenSource.contains("selectedTab = MainNavigationTabs.VEHICLE"))
    }

    @Test
    fun `local themes share policy across selector home widgets and background sync`() {
        val projectDir = generateSequence(File(requireNotNull(System.getProperty("user.dir")))) { it.parentFile }
            .first { File(it, "app").isDirectory }
        fun source(name: String) = File(projectDir, "app/src/main/java/com/leapauto/app/$name.kt").readText()

        val manager = source("ThemeManager")
        val homeLoader = manager.substringAfter("fun load3dBgBitmap(").substringBefore("/** 安全清理")
        val widgetLoader = manager.substringAfter("fun loadWidgetBgBitmap(").substringBefore("fun downloadAndUnzipPackage(")
        listOf(homeLoader, widgetLoader).forEach {
            assertTrue(it.contains("VehicleThemePolicy.background(supported, themeId)"))
            assertTrue(it.contains("VehicleThemePolicy.Background.ORIGINAL -> return null"))
            assertTrue(it.contains("VehicleThemePolicy.Background.BUILT_IN_SNOW -> return loadFallback"))
        }
        listOf("ControlWidget", "CompactControlWidget").forEach {
            assertTrue(source(it).contains("VehicleThemePolicy.background(is3dSupported, activeThemeId)"))
        }

        val sheet = source("ui/ThemeSelectorBottomSheet")
        assertTrue(sheet.contains("VehicleThemePolicy.availableThemes(is3dSupported, themes)"))
        assertTrue(sheet.contains("items(availableThemes, key = { it.themeId })"))
        assertTrue(sheet.contains("val isReady = !is3dSupported || ThemeManager.isThemeReady"))
        assertTrue(sheet.contains("if (!is3dSupported) return@LaunchedEffect"))
        assertTrue(sheet.contains("VehicleThemePolicy.canApplyDownloadResult("))
        assertFalse(sheet.contains("api.apply"))

        val activity = source("MainActivity")
        val sync = activity.substringAfter("private fun syncThemesAndEnsureActive()").substringBefore("private fun refreshDownloadedTheme(")
        assertTrue(sync.indexOf("return@runCatching") in 0 until sync.indexOf("api.listThemeList(vin)"))
        assertTrue(sync.contains("refreshDownloadedTheme(snapshot, activeThemeId)"))
        assertFalse(sync.contains("selectedThemeId = activeThemeId"))
        assertTrue(activity.contains("if (updated || capabilityChanged || currentSupportChanged)"))
        assertTrue(activity.contains("if (!isCurrentBackground(snapshot)) return@runOnUiThread"))

        val screen = source("ui/LeapAutoScreen")
        assertTrue(screen.contains("vehicleVin.isNotBlank() && supports3dThemes"))
        assertTrue(screen.contains("VehicleThemePolicy.displayName(supports3dThemes, selectedThemeId, cachedThemes)"))
    }
}
