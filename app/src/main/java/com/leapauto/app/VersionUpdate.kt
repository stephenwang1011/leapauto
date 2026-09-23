package com.leapauto.app

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

data class PgyerRelease(
    val versionName: String,
    val buildNumber: Int?,
    val updateDescription: String?,
    val buildKey: String? = null
)

sealed interface VersionUpdateState {
    object Idle : VersionUpdateState

    data class Checking(val currentVersion: String) : VersionUpdateState

    data class UpToDate(
        val currentVersion: String,
        val latestRelease: PgyerRelease
    ) : VersionUpdateState

    data class UpdateAvailable(
        val currentVersion: String,
        val latestRelease: PgyerRelease
    ) : VersionUpdateState

    data class Failed(
        val currentVersion: String,
        val message: String
    ) : VersionUpdateState
}

object VersionUpdatePromptPolicy {
    fun shouldShow(
        state: VersionUpdateState,
        handledVersion: String?,
        loggedIn: Boolean = true,
        onVehicleTab: Boolean = true
    ): Boolean {
        val update = state as? VersionUpdateState.UpdateAvailable ?: return false
        return update.latestRelease.versionName != handledVersion
    }
}

object VersionComparator {
    fun compare(left: String, right: String): Int {
        val leftParts = parse(left)
        val rightParts = parse(right)
        if (leftParts == null || rightParts == null) {
            return left.trim().compareTo(right.trim())
        }
        for (index in 0 until maxOf(leftParts.size, rightParts.size)) {
            val leftPart = leftParts.getOrElse(index) { 0 }
            val rightPart = rightParts.getOrElse(index) { 0 }
            if (leftPart != rightPart) return leftPart.compareTo(rightPart)
        }
        return 0
    }

    private fun parse(version: String): List<Int>? {
        val normalized = version.trim()
        if (!normalized.matches(Regex("\\d+(?:\\.\\d+){0,2}"))) return null
        return normalized.split('.').map { it.toInt() }
    }
}

object PgyerPageParser {
    private const val lineBreakMarker = "\u0000LEAPAUTO_LINE_BREAK\u0000"

    private val versionPattern = Regex(
        "版本\\s*[:：]\\s*(\\d+(?:\\.\\d+){1,2})\\s*(?:\\(\\s*build\\s*(\\d+)\\s*\\))?",
        RegexOption.IGNORE_CASE
    )
    private val descriptionPattern = Regex(
        "<div\\s+class=\\\"update-description\\\"[^>]*>(.*?)</div>",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )
    private val buildKeyPattern = Regex(
        "(?:/app/build/|/app/install/|(?:aKey|appKey|buildKey)\\s*=\\s*['\"])([a-f0-9]{32})",
        RegexOption.IGNORE_CASE
    )

    fun parse(html: String): PgyerRelease? {
        val plainText = htmlToText(html)
        val versionMatch = versionPattern.find(plainText) ?: return null
        val description = descriptionPattern.find(html)?.groupValues?.get(1)
            ?.let(::normalizeUpdateDescription)
        val buildKey = buildKeyPattern.find(html)?.groupValues?.get(1)
        return PgyerRelease(
            versionName = versionMatch.groupValues[1],
            buildNumber = versionMatch.groupValues.getOrNull(2)?.toIntOrNull(),
            updateDescription = description,
            buildKey = buildKey
        )
    }

    private fun htmlToText(value: String): String = value
        .replace(Regex("<[^>]+>"), " ")
        .replace("&nbsp;", " ", ignoreCase = true)
        .replace("&amp;", "&", ignoreCase = true)
        .replace("&lt;", "<", ignoreCase = true)
        .replace("&gt;", ">", ignoreCase = true)
        .replace(Regex("\\s+"), " ")
        .trim()

    private fun normalizeUpdateDescription(value: String): String? = value
        .replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), lineBreakMarker)
        .let(::htmlToText)
        .replace(lineBreakMarker, "\n")
        .replace("\\\\n", "\n")
        .replace("\\n", "\n")
        .lineSequence()
        .map { line -> line.trim().replace(Regex("[\\t ]+"), " ") }
        .filter { it.isNotBlank() }
        .joinToString("\n")
        .trim()
        .takeIf { it.isNotBlank() }
}

object PgyerUpdateChecker {
    const val DOWNLOAD_URL = "https://www.pgyer.com/lingpaozhikong"

    fun check(currentVersion: String): VersionUpdateState {
        val latestRelease = fetchLatestRelease()
        return if (VersionComparator.compare(currentVersion, latestRelease.versionName) < 0) {
            VersionUpdateState.UpdateAvailable(currentVersion, latestRelease)
        } else {
            VersionUpdateState.UpToDate(currentVersion, latestRelease)
        }
    }

    fun fetchLatestRelease(): PgyerRelease {
        val connection = (URL(DOWNLOAD_URL).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 8_000
            readTimeout = 8_000
            setRequestProperty("User-Agent", "LeapAuto/${BuildConfig.VERSION_NAME}")
            instanceFollowRedirects = true
        }
        try {
            if (connection.responseCode !in 200..299) {
                throw IOException("蒲公英返回 HTTP ${connection.responseCode}")
            }
            val html = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            return PgyerPageParser.parse(html)
                ?: throw IOException("未找到蒲公英最新版本信息")
        } finally {
            connection.disconnect()
        }
    }
}
