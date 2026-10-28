package com.leapauto.app

import android.content.Context
import android.content.SharedPreferences

class NightBrightnessStore internal constructor(private val preferences: SharedPreferences) {
    constructor(context: Context) : this(context.applicationContext.getSharedPreferences("night_background", Context.MODE_PRIVATE))

    fun load(themeId: String, supports3d: Boolean): Float = NightBrightnessPolicy.decode(
        preferences.all[NightBrightnessPolicy.key(themeId, supports3d)], themeId
    )

    fun save(themeId: String, supports3d: Boolean, percent: Float) {
        preferences.edit().putFloat(
            NightBrightnessPolicy.key(themeId, supports3d), NightBrightnessPolicy.decode(percent, themeId)
        ).apply()
    }

    fun reset(themeId: String, supports3d: Boolean) {
        preferences.edit().remove(NightBrightnessPolicy.key(themeId, supports3d)).apply()
    }

    fun observe(onChange: () -> Unit): () -> Unit {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> onChange() }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        return { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }
}
