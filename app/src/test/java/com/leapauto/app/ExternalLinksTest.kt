package com.leapauto.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExternalLinksTest {
    @Test
    fun feedbackUrlMatchesThePublishedQuestionnaire() {
        assertEquals("https://wj.qq.com/s2/27561301/nwqv/", ExternalLinks.FEEDBACK_URL)
    }

    @Test
    fun feedbackUrlIsAnHttpsExternalDestination() {
        assertTrue(ExternalLinks.FEEDBACK_URL.startsWith("https://"))
    }

    @Test
    fun githubRepoUrlMatchesConfiguredRepository() {
        assertEquals("https://github.com/stephenwang1011/leapauto", ExternalLinks.GITHUB_REPO_URL)
        assertTrue(ExternalLinks.GITHUB_REPO_URL.startsWith("https://"))
    }
}
