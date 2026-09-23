package com.leapauto.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

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
}
