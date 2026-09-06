package com.leapauto.app

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class WidgetRangeProgressSpecTest {

    @Test
    fun `widget range bars use three dp height and confirmed action row spacing`() {
        val workingDirectory = requireNotNull(System.getProperty("user.dir"))
        val projectDir = generateSequence(File(workingDirectory)) { it.parentFile ?: return@generateSequence null }
            .first { File(it, "app").isDirectory }
        val layout = File(projectDir, "app/src/main/res/layout/widget_layout.xml").readText()
        val drawable = File(projectDir, "app/src/main/res/drawable/widget_charge_progress.xml").readText()
        val pureGoodDrawable = File(projectDir, "app/src/main/res/drawable/widget_pure_range_progress.xml").readText()
        val pureWarningDrawable = File(projectDir, "app/src/main/res/drawable/widget_pure_range_warning_progress.xml").readText()
        val pureCriticalDrawable = File(projectDir, "app/src/main/res/drawable/widget_pure_range_critical_progress.xml").readText()

        assertTrue(layout.contains("android:id=\"@+id/progressWCharge\""))
        assertTrue(layout.contains("android:id=\"@+id/progressWChargeWarning\""))
        assertTrue(layout.contains("android:id=\"@+id/progressWChargeCritical\""))
        assertTrue(layout.contains("android:id=\"@+id/widgetHybridRange\""))
        assertTrue(layout.contains("android:id=\"@+id/imgWElectricRangeIcon\""))
        assertTrue(layout.contains("android:id=\"@+id/imgWFuelRangeIcon\""))
        assertTrue(layout.contains("android:src=\"@drawable/ic_hybrid_electric\""))
        assertTrue(layout.contains("android:src=\"@drawable/ic_hybrid_fuel\""))
        assertTrue(layout.split("android:layout_width=\"16dp\"").size - 1 >= 2)
        assertTrue(layout.split("android:layout_height=\"16dp\"").size - 1 >= 2)
        assertTrue(layout.contains("android:id=\"@+id/progressWElectric\""))
        assertTrue(layout.contains("android:id=\"@+id/progressWFuel\""))
        assertTrue(layout.split("android:layout_height=\"3dp\"").size - 1 == 3)
        assertTrue(!layout.contains("android:layout_height=\"2dp\""))
        assertTrue(!layout.contains("android:layout_height=\"4dp\""))
        assertTrue(layout.contains("android:paddingTop=\"8dp\""))
        assertTrue(layout.contains("android:paddingBottom=\"4dp\""))
        assertTrue(layout.contains("android:layout_height=\"28dp\""))
        assertTrue(layout.contains("android:layout_marginTop=\"12dp\""))
        assertTrue(layout.contains("android:layout_marginBottom=\"12dp\""))
        assertTrue(layout.contains("android:id=\"@+id/widgetStatusContainer\""))
        assertTrue(layout.contains("android:id=\"@+id/imgWChargingStatus\""))
        assertTrue(layout.contains("android:src=\"@drawable/ic_widget_charging_bolt\""))
        assertTrue(layout.contains("android:id=\"@+id/btnWUnlock\""))
        assertTrue(layout.contains("android:id=\"@+id/btnWLock\""))
        assertTrue(layout.contains("android:id=\"@+id/btnWSentry\""))
        assertTrue(layout.contains("android:id=\"@+id/imgWSentryOn\""))
        assertTrue(layout.contains("android:tint=\"@color/energy_green\""))
        assertTrue(layout.contains("android:id=\"@+id/btnWTrunk\""))
        assertTrue(layout.split("android:padding=\"0dp\"").size - 1 >= 5)
        assertTrue(drawable.split("android:radius=\"2dp\"").size - 1 == 2)
        assertTrue(drawable.contains("@color/widget_range_track"))
        assertTrue(drawable.contains("@color/energy_green"))
        assertTrue(!pureGoodDrawable.contains("<level-list"))
        assertTrue(pureGoodDrawable.contains("@color/widget_range_good"))
        assertTrue(pureWarningDrawable.contains("@color/widget_range_warning"))
        assertTrue(pureCriticalDrawable.contains("@color/widget_range_critical"))
        assertTrue(pureGoodDrawable.contains("@color/widget_range_track"))
        assertTrue(pureWarningDrawable.contains("@color/widget_range_track"))
        assertTrue(pureCriticalDrawable.contains("@color/widget_range_track"))
    }
}
