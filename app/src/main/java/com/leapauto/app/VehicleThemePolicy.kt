package com.leapauto.app

/** Client-only theme choices; model rendering readiness is not a capability signal. */
internal object VehicleThemePolicy {
    const val ORIGINAL_THEME_ID = "4"
    const val SNOW_THEME_ID = "7"

    enum class Background { ORIGINAL, BUILT_IN_SNOW, DOWNLOADED }

    fun supports3d(persisted: Boolean?, hasMetadata: Boolean, h5Key: String?): Boolean =
        persisted ?: if (hasMetadata) !h5Key.isNullOrBlank() else true

    fun activeThemeId(supported: Boolean, localId: String?, remoteId: String? = null): String {
        val local = localId?.takeIf { it.isNotBlank() }
        if (supported) return local ?: remoteId?.takeIf { it.isNotBlank() } ?: SNOW_THEME_ID
        // Preserve the snow appearance of legacy unsupported choices without rewriting preferences.
        return when (local) {
            null, ORIGINAL_THEME_ID -> ORIGINAL_THEME_ID
            else -> SNOW_THEME_ID
        }
    }

    fun background(supported: Boolean, themeId: String): Background {
        val effectiveId = activeThemeId(supported, themeId)
        return when {
            effectiveId == ORIGINAL_THEME_ID || (themeId.isBlank() && supported) -> Background.ORIGINAL
            !supported -> Background.BUILT_IN_SNOW
            else -> Background.DOWNLOADED
        }
    }

    fun availableThemes(supported: Boolean, themes: List<VehicleTheme>): List<VehicleTheme> {
        if (supported) return themes
        return listOf(
            VehicleTheme(
                themeId = ORIGINAL_THEME_ID,
                themeCode = "",
                name = "原厂默认",
                description = "",
                isDefault = true
            ),
            VehicleTheme(
                themeId = SNOW_THEME_ID,
                themeCode = "",
                name = "雾川雪境",
                description = "",
                isDefault = false
            )
        )
    }

    fun displayName(supported: Boolean, themeId: String, themes: List<VehicleTheme>): String {
        return when (val effectiveId = activeThemeId(supported, themeId)) {
            ORIGINAL_THEME_ID -> if (supported) "层峦叠嶂" else "原厂默认"
            SNOW_THEME_ID -> "雾川雪境"
            else -> themes.firstOrNull { it.themeId == effectiveId }?.name?.let {
                when (it) {
                    "默认主题", "原厂天幕" -> "层峦叠嶂"
                    "雾川雪镜" -> "雾川雪境"
                    else -> it
                }
            } ?: "雾川雪境"
        }
    }

    fun canApplyDownloadResult(
        requestedVin: String,
        currentVin: String,
        requestedThemeId: String,
        activeThemeId: String,
        supported: Boolean
    ): Boolean = supported && requestedVin.isNotBlank() && requestedVin == currentVin &&
        requestedThemeId == activeThemeId
}
