package com.leapauto.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class PostLoginPinSetupPolicyTest {

    @Test
    fun `prompts pin setup when new login succeeds and pin is not saved`() {
        assertTrue(
            PostLoginPinSetupPolicy.shouldPromptPinSetup(
                isNewLogin = true,
                pinSaved = false
            )
        )
    }

    @Test
    fun `does not prompt pin setup when new login succeeds but pin is already saved`() {
        assertFalse(
            PostLoginPinSetupPolicy.shouldPromptPinSetup(
                isNewLogin = true,
                pinSaved = true
            )
        )
    }

    @Test
    fun `does not prompt pin setup on app launch session restore even if pin is not saved`() {
        assertFalse(
            PostLoginPinSetupPolicy.shouldPromptPinSetup(
                isNewLogin = false,
                pinSaved = false
            )
        )
    }

    @Test
    fun `does not prompt pin setup on app launch session restore when pin is saved`() {
        assertFalse(
            PostLoginPinSetupPolicy.shouldPromptPinSetup(
                isNewLogin = false,
                pinSaved = true
            )
        )
    }

    @Test
    fun `power type dialog is blocked while pin setup is in progress`() {
        val projectDir = projectDirectory()
        val mainActivity = File(projectDir, "app/src/main/java/com/leapauto/app/MainActivity.kt").readText()
        val screen = File(projectDir, "app/src/main/java/com/leapauto/app/ui/LeapAutoScreen.kt").readText()

        // Screen gate
        assertTrue(screen.contains("showPowerTypeDialog && !pinSetupInProgress"))

        // checkAndPromptPowerType checks !pinSetupInProgress
        assertTrue(mainActivity.contains("!pinSetupInProgress && !sessionStore.isVehiclePowerTypeConfirmed(vin)"))

        // Serial continuation on pin save and pin cancel
        assertTrue(mainActivity.contains("clearPendingPinProtectedAction(cancel = true)"))
        assertTrue(mainActivity.contains("checkAndPromptPowerType(session.selectedVin)"))
    }

    private fun projectDirectory(): java.io.File {
        val workingDirectory = requireNotNull(System.getProperty("user.dir"))
        return generateSequence(java.io.File(workingDirectory)) { it.parentFile }
            .first { java.io.File(it, "app").isDirectory }
    }
}
