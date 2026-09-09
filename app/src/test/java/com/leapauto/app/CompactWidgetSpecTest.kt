package com.leapauto.app

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class CompactWidgetSpecTest {

    @Test
    fun `compact widget is registered as a fixed two by two provider`() {
        val projectDir = projectDirectory()
        val manifest = File(projectDir, "app/src/main/AndroidManifest.xml").readText()
        val provider = File(
            projectDir,
            "app/src/main/res/xml/compact_appwidget_provider_info.xml"
        ).readText()
        val provider31 = File(
            projectDir,
            "app/src/main/res/xml-v31/compact_appwidget_provider_info.xml"
        ).readText()

        assertTrue(manifest.contains("android:name=\".CompactControlWidget\""))
        assertTrue(manifest.contains("android:resource=\"@xml/compact_appwidget_provider_info\""))
        assertTrue(manifest.contains("android:name=\"android.intent.action.CONFIGURATION_CHANGED\""))
        assertTrue(provider.contains("android:resizeMode=\"none\""))
        assertTrue(provider.contains("android:initialLayout=\"@layout/widget_compact_layout\""))
        assertTrue(provider31.contains("android:targetCellWidth=\"2\""))
        assertTrue(provider31.contains("android:targetCellHeight=\"2\""))
    }

    @Test
    fun `compact layout exposes range lock and air conditioner controls without manual refresh`() {
        val layout = File(
            projectDirectory(),
            "app/src/main/res/layout/widget_compact_layout.xml"
        ).readText()
        val provider = File(
            projectDirectory(),
            "app/src/main/java/com/leapauto/app/CompactControlWidget.kt"
        ).readText()

        assertTrue(!layout.contains("android:id=\"@+id/btnWCRefresh\""))
        assertTrue(!provider.contains("ACTION_REFRESH"))
        assertTrue(!provider.contains("refreshPendingIntent"))
        assertTrue(layout.contains("android:id=\"@+id/imgWCCar\""))
        assertTrue(layout.contains("android:id=\"@+id/txtWCTitle\""))
        assertTrue(layout.contains("android:id=\"@+id/compactHybridRange\""))
        assertTrue(layout.contains("android:id=\"@+id/txtWCElectricRange\""))
        assertTrue(layout.contains("android:id=\"@+id/txtWCElectricSoc\""))
        assertTrue(layout.contains("android:id=\"@+id/txtWCFuelRange\""))
        assertTrue(layout.contains("android:id=\"@+id/txtWCFuelSoc\""))
        assertTrue(!layout.contains("android:id=\"@+id/progressWCElectric\""))
        assertTrue(!layout.contains("android:id=\"@+id/progressWCFuel\""))
        assertTrue(!layout.contains("android:id=\"@+id/progressWCNormal\""))
        assertTrue(layout.contains("android:id=\"@+id/compactRangeContainer\""))
        assertTrue(layout.contains("android:id=\"@+id/txtWCRange\""))
        assertTrue(layout.contains("android:id=\"@+id/txtWCSocValue\""))
        assertTrue(layout.contains("android:id=\"@+id/btnWCLock\""))
        assertTrue(layout.contains("android:id=\"@+id/btnWCAc\""))
        assertTrue(layout.contains("android:layout_height=\"44dp\""))
        assertTrue(layout.contains("android:maxLines=\"1\""))
    }

    @Test
    fun `compact action surfaces are visually inset while keeping their full click containers`() {
        val projectDir = projectDirectory()
        val layout = File(projectDir, "app/src/main/res/layout/widget_compact_layout.xml").readText()
        val neutral = File(projectDir, "app/src/main/res/drawable/widget_compact_action_neutral.xml").readText()
        val success = File(projectDir, "app/src/main/res/drawable/widget_compact_action_success.xml").readText()
        val primary = File(projectDir, "app/src/main/res/drawable/widget_compact_action_primary.xml").readText()

        assertTrue(layout.contains("android:layout_height=\"44dp\""))
        listOf(neutral, success, primary).forEach { drawable ->
            assertTrue(drawable.contains("android:insetLeft=\"4dp\""))
            assertTrue(drawable.contains("android:insetTop=\"4dp\""))
            assertTrue(drawable.contains("android:insetRight=\"4dp\""))
            assertTrue(drawable.contains("android:insetBottom=\"4dp\""))
        }
    }

    @Test
    fun `opaque and translucent widget backgrounds share the same rounded corners`() {
        val drawableDirectory = File(projectDirectory(), "app/src/main/res/drawable")
        listOf(
            "widget_card_background.xml",
            "widget_card_background_25.xml",
            "widget_card_background_50.xml",
            "widget_card_background_75.xml"
        ).forEach { fileName ->
            val drawable = File(drawableDirectory, fileName).readText()
            assertTrue(drawable.contains("<corners android:radius=\"20dp\" />"))
        }
    }

    @Test
    fun `wide widget no longer reserves or renders a brand logo`() {
        val projectDir = projectDirectory()
        val layout = File(projectDir, "app/src/main/res/layout/widget_layout.xml").readText()
        val controlWidget = File(projectDir, "app/src/main/java/com/leapauto/app/ControlWidget.kt").readText()

        assertTrue(!layout.contains("imgWBrandLogo"))
        assertTrue(!controlWidget.contains("imgWBrandLogo"))
        assertTrue(!controlWidget.contains("setVehicleBrandLogo"))
    }

    @Test
    fun `both widget providers share one worker and preserve cadence while either remains`() {
        val projectDir = projectDirectory()
        val worker = File(
            projectDir,
            "app/src/main/java/com/leapauto/app/WidgetSyncWorker.kt"
        ).readText()
        val controlWidget = File(
            projectDir,
            "app/src/main/java/com/leapauto/app/ControlWidget.kt"
        ).readText()
        val compactWidget = File(
            projectDir,
            "app/src/main/java/com/leapauto/app/CompactControlWidget.kt"
        ).readText()

        assertTrue(worker.contains("ComponentName(context, ControlWidget::class.java)"))
        assertTrue(worker.contains("ComponentName(context, CompactControlWidget::class.java)"))
        assertTrue(worker.contains("CompactControlWidget.baseViews(applicationContext)"))
        assertTrue(controlWidget.contains("if (!CompactControlWidget.hasInstances(context))"))
        assertTrue(compactWidget.contains("if (!ControlWidget.hasInstances(context))"))
        assertTrue(compactWidget.contains("ControlWidget.click(context, it)"))
    }

    @Test
    fun `app appearance changes immediately rebind both widgets with explicit theme resources`() {
        val projectDir = projectDirectory()
        val activity = File(projectDir, "app/src/main/java/com/leapauto/app/MainActivity.kt").readText()
        val wideWidget = File(projectDir, "app/src/main/java/com/leapauto/app/ControlWidget.kt").readText()
        val compactWidget = File(projectDir, "app/src/main/java/com/leapauto/app/CompactControlWidget.kt").readText()

        assertTrue(activity.contains("sessionStore.saveAppearanceMode(mode)\n        ControlWidget.refreshAppearance(this)"))
        assertTrue(wideWidget.contains("SessionStore(context).loadAppearanceMode().resolvesToDark(systemDark)"))
        assertTrue(wideWidget.contains("CompactControlWidget.refreshAppearance(context)"))
        assertTrue(wideWidget.contains("widget_card_background_light"))
        assertTrue(wideWidget.contains("widget_card_background_dark"))
        assertTrue(wideWidget.contains("widget_action_background_light"))
        assertTrue(wideWidget.contains("widget_action_background_dark"))
        assertTrue(compactWidget.contains("ControlWidget.widgetThemeContext(context)"))
        assertTrue(compactWidget.contains("widget_compact_action_neutral_light"))
        assertTrue(compactWidget.contains("widget_compact_action_neutral_dark"))
        assertTrue(wideWidget.contains("views.setInt(id, \"setColorFilter\", actionIcon)"))
        assertTrue(wideWidget.contains("views.setInt(id, \"setBackgroundResource\", actionBackground)"))
        assertTrue(compactWidget.contains("views.setInt(R.id.imgWCAcOff, \"setColorFilter\", actionColor)"))
    }

    @Test
    fun `high transparency keeps range text and progress bars readable`() {
        val projectDir = projectDirectory()
        val wideLayout = File(projectDir, "app/src/main/res/layout/widget_layout.xml").readText()
        val compactLayout = File(projectDir, "app/src/main/res/layout/widget_compact_layout.xml").readText()
        val wideWidget = File(projectDir, "app/src/main/java/com/leapauto/app/ControlWidget.kt").readText()
        val compactWidget = File(projectDir, "app/src/main/java/com/leapauto/app/CompactControlWidget.kt").readText()
        val lightColors = File(projectDir, "app/src/main/res/values/colors.xml").readText()
        val darkColors = File(projectDir, "app/src/main/res/values-night/colors.xml").readText()

        assertTrue(wideWidget.contains("applyProgressAppearance(context, views, opacity == 25)"))
        assertTrue(compactWidget.contains("applyProgressAppearance(context, views, opacity == 25)"))
        assertTrue(wideWidget.contains("widget_range_good_high_contrast"))
        assertTrue(compactWidget.contains("widget_range_track_high_contrast"))
        assertTrue(wideLayout.contains("android:shadowColor=\"@color/widget_text_shadow\""))
        assertTrue(compactLayout.contains("android:shadowColor=\"@color/widget_text_shadow\""))
        listOf(lightColors, darkColors).forEach { colors ->
            assertTrue(colors.contains("widget_on_surface_high_contrast"))
            assertTrue(colors.contains("widget_range_track_high_contrast"))
            assertTrue(colors.contains("widget_range_good_high_contrast"))
            assertTrue(colors.contains("widget_text_shadow"))
        }
    }

    @Test
    fun `compact pure electric and fuel progress bars share the same geometry`() {
        val projectDir = projectDirectory()
        val layout = File(projectDir, "app/src/main/res/layout/widget_compact_layout.xml").readText()
        val electric = File(
            projectDir,
            "app/src/main/res/drawable/widget_pure_range_progress.xml"
        ).readText()
        val fuel = File(
            projectDir,
            "app/src/main/res/drawable/widget_compact_fuel_range_progress.xml"
        ).readText()

        assertTrue(layout.contains("android:id=\"@+id/imgWCCar\""))
        assertTrue(layout.contains("android:id=\"@+id/compactHybridRange\""))
        assertTrue(layout.contains("android:id=\"@+id/txtWCElectricRange\""))
        assertTrue(layout.contains("android:id=\"@+id/txtWCFuelRange\""))
        listOf(electric, fuel).forEach { drawable ->
            assertTrue(drawable.contains("android:id=\"@android:id/background\""))
            assertTrue(drawable.contains("android:shape=\"rectangle\""))
            assertTrue(drawable.contains("<corners android:radius=\"2dp\" />"))
        }
    }

    private fun projectDirectory(): File {
        val workingDirectory = requireNotNull(System.getProperty("user.dir"))
        return generateSequence(File(workingDirectory)) { it.parentFile }
            .first { File(it, "app").isDirectory }
    }
}
