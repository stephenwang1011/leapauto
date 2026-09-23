package com.leapauto.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdateInstallerTest {

    @Test
    fun buildsDownloadEntryUrlWithBuildKey() {
        val buildKey = "840ca8fb3fa9900c6d9a101f37e40fa3"
        val url = AppUpdateInstaller.buildDownloadEntryUrl(buildKey)
        assertEquals("https://www.pgyer.com/app/install/840ca8fb3fa9900c6d9a101f37e40fa3", url)
    }

    @Test
    fun buildsDownloadEntryUrlFallbackWithoutBuildKey() {
        val urlNull = AppUpdateInstaller.buildDownloadEntryUrl(null)
        assertEquals("https://www.pgyer.com/app/installCheck?key=lingpaozhikong", urlNull)

        val urlBlank = AppUpdateInstaller.buildDownloadEntryUrl("   ")
        assertEquals("https://www.pgyer.com/app/installCheck?key=lingpaozhikong", urlBlank)
    }

    @Test
    fun pgyerReleaseCarriesBuildKeyProperly() {
        val release = PgyerRelease(
            versionName = "3.4.89",
            buildNumber = 3004089,
            updateDescription = "应用内直载新版本",
            buildKey = "abcdef1234567890"
        )
        val entryUrl = AppUpdateInstaller.buildDownloadEntryUrl(release.buildKey)
        assertEquals("https://www.pgyer.com/app/install/abcdef1234567890", entryUrl)
    }

    @Test
    fun resolvesViaApiKeyReturnsNullWhenKeyIsBlank() {
        val resultNull = AppUpdateInstaller.resolveViaApiKey("", "abcdef1234567890")
        assertEquals(null, resultNull)

        val resultBuildKeyNull = AppUpdateInstaller.resolveViaApiKey("test_key", null)
        assertEquals(null, resultBuildKeyNull)
    }
}
