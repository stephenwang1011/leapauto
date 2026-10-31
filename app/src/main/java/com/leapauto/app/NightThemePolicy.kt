package com.leapauto.app

/** Home-only presentation policy. Widget assets retain their own, unfiltered tone. */
object NightThemePolicy {
    enum class BackgroundSource {
        IMAGE, ORIGINAL_SKYBOX, NATIVE;

        fun transitionPlacement(isDark: Boolean): TransitionPlacement = when {
            !isDark -> TransitionPlacement.FOREGROUND
            this == ORIGINAL_SKYBOX -> TransitionPlacement.FOREGROUND
            else -> TransitionPlacement.BEHIND_VEHICLE
        }

        fun transitionBehindVehicle(isDark: Boolean): Boolean =
            transitionPlacement(isDark) == TransitionPlacement.BEHIND_VEHICLE
    }

    enum class TransitionPlacement {
        BEHIND_VEHICLE,
        FOREGROUND
    }

    /** Select the rendered backdrop, not merely whether a 3D vehicle is present. */
    fun selectBackground(themeId: String, show3D: Boolean, hasThemeBackground: Boolean): BackgroundSource = when {
        show3D && themeId == "4" -> BackgroundSource.ORIGINAL_SKYBOX
        hasThemeBackground -> BackgroundSource.IMAGE
        else -> BackgroundSource.NATIVE
    }

    fun resolve(
        themeId: String,
        isDark: Boolean,
        source: BackgroundSource,
        originalHeroDark: Boolean = isDark,
        brightnessPercent: Float = NightBrightnessPolicy.defaultPercent(themeId)
    ): Presentation = resolve(
        themeId, isDark, source == BackgroundSource.IMAGE, source == BackgroundSource.ORIGINAL_SKYBOX,
        originalHeroDark, brightnessPercent
    )

    data class BackgroundTreatment(
        val saturation: Float = 1f,
        val gain: Float = 1f,
        val lift: Float = 0f
    ) {
        val isIdentity: Boolean get() = saturation == 1f && gain == 1f && lift == 0f

        /** An affine RGB transform; alpha and the separately rendered vehicle are untouched. */
        fun colorMatrix(): FloatArray {
            val r = (1f - saturation) * 0.2126f
            val g = (1f - saturation) * 0.7152f
            val b = (1f - saturation) * 0.0722f
            return floatArrayOf(
                (r + saturation) * gain, g * gain, b * gain, 0f, lift * 255f,
                r * gain, (g + saturation) * gain, b * gain, 0f, lift * 255f,
                r * gain, g * gain, (b + saturation) * gain, 0f, lift * 255f,
                0f, 0f, 0f, 1f, 0f
            )
        }
    }

    data class Presentation(
        val source: BackgroundSource,
        val background: BackgroundTreatment,
        val isHeroDark: Boolean,
        val useNightSkybox: Boolean,
        val transitionHeightDp: Int,
        val transitionPlacement: TransitionPlacement
    )

    fun resolve(
        themeId: String,
        isDark: Boolean,
        hasThemeBackground: Boolean = true,
        showsOriginalSkybox: Boolean = false,
        originalHeroDark: Boolean = isDark,
        brightnessPercent: Float = NightBrightnessPolicy.defaultPercent(themeId)
    ): Presentation {
        val source = when {
            showsOriginalSkybox -> BackgroundSource.ORIGINAL_SKYBOX
            hasThemeBackground -> BackgroundSource.IMAGE
            else -> BackgroundSource.NATIVE
        }
        val treatment = when {
            !isDark -> BackgroundTreatment()
            else -> NightBrightnessPolicy.treatment(
                themeId = themeId,
                percent = brightnessPercent,
                image = source == BackgroundSource.IMAGE
            )
        }
        return Presentation(
            source = source,
            background = treatment,
            isHeroDark = when {
                isDark && source == BackgroundSource.IMAGE -> treatment.gain + treatment.lift <= 0.47f ||
                    ((themeId == "5" || themeId == "7") && treatment.gain <= 1f)
                isDark && source == BackgroundSource.ORIGINAL_SKYBOX -> treatment.gain <= 1f &&
                    treatment.gain * 0.22f + treatment.lift <= 0.47f
                isDark -> true
                source == BackgroundSource.ORIGINAL_SKYBOX -> originalHeroDark
                // Stone artwork is dark even when the app is light; widgets keep their own tone.
                source == BackgroundSource.IMAGE && themeId == "5" -> true
                source == BackgroundSource.IMAGE -> originalHeroDark
                else -> false
            },
            useNightSkybox = isDark && source == BackgroundSource.ORIGINAL_SKYBOX,
            transitionHeightDp = if (isDark) 120 else 24,
            transitionPlacement = source.transitionPlacement(isDark)
        )
    }
}
