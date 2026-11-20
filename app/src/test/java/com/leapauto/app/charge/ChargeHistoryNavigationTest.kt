package com.leapauto.app.charge

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ChargeHistoryNavigationTest {

    private fun projectDirectory(): File {
        var current: File? = File(".").canonicalFile
        while (current != null) {
            if (File(current, "app/src/main/AndroidManifest.xml").exists()) {
                return current
            }
            current = current.parentFile
        }
        return File(".").canonicalFile
    }

    @Test
    fun `healthy charging bottom sheet has charge history entry`() {
        val projectDir = projectDirectory()
        val sheetFile = File(projectDir, "app/src/main/java/com/leapauto/app/ui/HealthyChargingBottomSheet.kt")
        assertTrue("HealthyChargingBottomSheet.kt must exist", sheetFile.exists())

        val content = sheetFile.readText()
        assertTrue("Must declare onOpenChargeHistory parameter", content.contains("onOpenChargeHistory: () -> Unit"))
        assertTrue("Must render 充电记录 card", content.contains("\"充电记录\""))
        assertTrue("Must render 轻松查看充电详情 subtitle", content.contains("\"轻松查看充电详情\""))
        assertTrue("Must render ic_phosphor_chart_bar icon", content.contains("R.drawable.ic_phosphor_chart_bar"))
    }

    @Test
    fun `leap auto screen wires charge history screen destination`() {
        val projectDir = projectDirectory()
        val screenFile = File(projectDir, "app/src/main/java/com/leapauto/app/ui/LeapAutoScreen.kt")
        assertTrue("LeapAutoScreen.kt must exist", screenFile.exists())

        val content = screenFile.readText()
        assertTrue("Must include CHARGE_HISTORY in ScreenDestination", content.contains("CHARGE_HISTORY("))
        assertTrue("Must include showChargeHistoryScreen state", content.contains("showChargeHistoryScreen"))
        assertTrue("Must set appBarTitle to 充电记录", content.contains("loggedIn && showChargeHistoryScreen -> \"充电记录\""))
        assertTrue("Must route to ChargeHistoryScreen composable", content.contains("ScreenDestination.CHARGE_HISTORY -> {"))
        assertTrue("Must call ChargeHistoryScreen", content.contains("ChargeHistoryScreen("))
    }
}
