package com.leapauto.app

/** Device-local background preference, independent of accounts and vehicle commands. */
object NightBrightnessPolicy {
    const val MIN = 10f
    const val MAX = 150f

    fun key(themeId: String, supports3d: Boolean): String =
        "background_v1_${themeId.length}_${themeId}_${if (themeId == "4" && !supports3d) "native" else "scene"}"

    fun defaultPercent(themeId: String): Float = when (themeId) {
        "7", "8" -> 45f
        "5", "4" -> 50f
        else -> 100f
    }

    // Native backgrounds already include their original night tone; keep saved percentages stable.
    private fun nativeReferencePercent(themeId: String): Float = when (themeId) {
        "7" -> 44f
        "8" -> 46f
        else -> 100f
    }

    fun decode(raw: Any?, themeId: String): Float =
        (raw as? Float)?.takeIf { it.isFinite() }?.coerceIn(MIN, MAX) ?: defaultPercent(themeId)

    fun treatment(themeId: String, percent: Float, image: Boolean): NightThemePolicy.BackgroundTreatment {
        val stored = decode(percent, themeId)
        val value = if (image) stored else stored / nativeReferencePercent(themeId) * 100f
        val gain = value / 100f
        return NightThemePolicy.BackgroundTreatment(
            saturation = if (!image) 1f else when (themeId) { "7" -> 0.78f; "8" -> 0.88f; else -> 1f },
            gain = gain,
            lift = if (!image) ((value - 100f).coerceAtLeast(0f) / 100f) else when (themeId) {
                "7" -> 0.025f
                "8" -> 0.015f
                else -> 0f
            }
        )
    }
}
