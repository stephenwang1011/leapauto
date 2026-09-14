package com.leapauto.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VersionUpdateTest {
    @Test
    fun comparesNumericSegmentsInsteadOfLexicalText() {
        assertTrue(VersionComparator.compare("1.5.10", "1.5.9") > 0)
        assertTrue(VersionComparator.compare("1.5.6", "1.5.7") < 0)
        assertEquals(0, VersionComparator.compare("1.5", "1.5.0"))
    }

    @Test
    fun parsesPgyerVersionAndUpdateDescription() {
        val html = """
            <div class="app-info">版本：1.5.6 (build 7)</div>
            <div class="update-description">
                1.5.6 更新内容：<br />完善车况展示；<br />优化小组件。
            </div>
        """.trimIndent()

        val release = PgyerPageParser.parse(html)

        assertNotNull(release)
        assertEquals("1.5.6", release?.versionName)
        assertEquals(7, release?.buildNumber)
        assertEquals(
            "1.5.6 更新内容：\n完善车况展示；\n优化小组件。",
            release?.updateDescription
        )
    }

    @Test
    fun preservesEscapedLineBreaksInPgyerUpdateDescription() {
        val html = """
            <div class="app-info">版本：1.5.20</div>
            <div class="update-description">1. 修复显示；\\n2. 保留换行。</div>
        """.trimIndent()

        val release = PgyerPageParser.parse(html)

        assertEquals("1. 修复显示；\n2. 保留换行。", release?.updateDescription)
    }

    @Test
    fun detectsUpdateOnlyWhenLatestVersionIsHigher() {
        val newer = PgyerRelease("1.5.7", 8, "修复问题")
        val current = "1.5.6"

        val state = if (VersionComparator.compare(current, newer.versionName) < 0) {
            VersionUpdateState.UpdateAvailable(current, newer)
        } else {
            VersionUpdateState.UpToDate(current, newer)
        }

        assertTrue(state is VersionUpdateState.UpdateAvailable)
    }

    @Test
    fun showsPromptForUnhandledNewVersionAcrossScreens() {
        val state = VersionUpdateState.UpdateAvailable(
            currentVersion = "1.5.6",
            latestRelease = PgyerRelease("1.5.7", 8, "修复问题")
        )

        assertTrue(VersionUpdatePromptPolicy.shouldShow(state, null, loggedIn = true, onVehicleTab = true))
        assertFalse(VersionUpdatePromptPolicy.shouldShow(state, "1.5.7", loggedIn = true, onVehicleTab = true))
        assertTrue(VersionUpdatePromptPolicy.shouldShow(state, null, loggedIn = false, onVehicleTab = true))
        assertTrue(VersionUpdatePromptPolicy.shouldShow(state, null, loggedIn = true, onVehicleTab = false))
    }

    @Test
    fun showsPromptAgainWhenPgyerPublishesAnotherVersion() {
        val state = VersionUpdateState.UpdateAvailable(
            currentVersion = "1.5.6",
            latestRelease = PgyerRelease("1.5.8", 9, "继续优化")
        )

        assertTrue(VersionUpdatePromptPolicy.shouldShow(state, "1.5.7", loggedIn = true, onVehicleTab = true))
        assertFalse(
            VersionUpdatePromptPolicy.shouldShow(
                VersionUpdateState.UpToDate("1.5.6", PgyerRelease("1.5.6", 7, null)),
                "1.5.7",
                loggedIn = true,
                onVehicleTab = true
            )
        )
    }
}
