package com.leapauto.app

import android.content.SharedPreferences
import java.lang.reflect.Proxy
import org.junit.Assert.*
import org.junit.Test

class NightBrightnessStoreTest {
    @Test
    fun `save reset and observer use local theme keys and unregister cleanly`() {
        val fake = PreferencesFake()
        val store = NightBrightnessStore(fake.preferences)
        var notifications = 0
        val unsubscribe = store.observe { notifications++ }
        store.save("7", true, 81f)
        store.save("4", true, 130f)
        store.save("4", false, 25f)
        assertEquals(81f, store.load("7", false), 0f)
        assertEquals(45f, store.load("8", true), 0f)
        assertEquals(130f, store.load("4", true), 0f)
        assertEquals(25f, store.load("4", false), 0f)
        store.reset("4", false)
        assertEquals(50f, store.load("4", false), 0f)
        assertEquals(130f, store.load("4", true), 0f)
        assertEquals(4, notifications)
        unsubscribe()
        store.save("8", true, 70f)
        assertEquals(4, notifications)
    }

    @Test
    fun `wrong preference types and old unrelated keys cannot crash or override defaults`() {
        val fake = PreferencesFake()
        val store = NightBrightnessStore(fake.preferences)
        fake.values["night_brightness"] = 90f
        for (bad in listOf("0.9", 55, true, Float.NaN)) {
            fake.values[NightBrightnessPolicy.key("7", true)] = bad
            assertEquals(45f, store.load("7", true), 0f)
        }
        store.save("7", true, Float.POSITIVE_INFINITY)
        assertEquals(45f, store.load("7", true), 0f)
        store.reset("7", true)
        assertEquals(90f, fake.values["night_brightness"])
    }

    @Test
    fun `new defaults apply only to unset and reset themes preserving existing preferences`() {
        val fake = PreferencesFake()
        val defaults = mapOf("7" to 45f, "5" to 50f, "8" to 45f, "4" to 50f)
        val existing = mapOf("7" to 44f, "5" to 100f, "8" to 46f, "4" to 100f)
        val store = NightBrightnessStore(fake.preferences)
        for ((theme, percent) in defaults) {
            assertEquals(percent, store.load(theme, true), 0f)
            fake.values[NightBrightnessPolicy.key(theme, true)] = existing.getValue(theme)
        }
        store.save("4", false, 35f)
        for ((theme, percent) in existing) {
            assertEquals(percent, store.load(theme, true), 0f)
        }
        for ((theme, percent) in defaults) {
            val beforeReset = fake.values.toMap()
            val resetKey = NightBrightnessPolicy.key(theme, true)
            store.reset(theme, true)
            assertEquals(percent, store.load(theme, true), 0f)
            assertEquals(beforeReset - resetKey, fake.values)
            assertEquals(35f, store.load("4", false), 0f)
        }
        store.reset("4", false)
        assertEquals(50f, store.load("4", false), 0f)
    }

    /** In-memory SharedPreferences contract; no Android runtime or disk access. */
    private class PreferencesFake {
        val values = mutableMapOf<String, Any>()
        private val listeners = mutableListOf<SharedPreferences.OnSharedPreferenceChangeListener>()
        val preferences: SharedPreferences = Proxy.newProxyInstance(
            SharedPreferences::class.java.classLoader, arrayOf(SharedPreferences::class.java)
        ) { _, method, args ->
            when (method.name) {
                "getAll" -> values.toMap()
                "edit" -> editor()
                "registerOnSharedPreferenceChangeListener" -> { listeners.add(args[0] as SharedPreferences.OnSharedPreferenceChangeListener); null }
                "unregisterOnSharedPreferenceChangeListener" -> { listeners.remove(args[0] as SharedPreferences.OnSharedPreferenceChangeListener); null }
                else -> error("Unexpected preference call: ${method.name}")
            }
        } as SharedPreferences

        private fun editor(): SharedPreferences.Editor {
            val pending = mutableMapOf<String, Any?>()
            return Proxy.newProxyInstance(
                SharedPreferences.Editor::class.java.classLoader, arrayOf(SharedPreferences.Editor::class.java)
            ) { proxy, method, args ->
                when (method.name) {
                    "putFloat" -> { pending[args[0] as String] = args[1]; proxy }
                    "remove" -> { pending[args[0] as String] = null; proxy }
                    "apply" -> {
                        pending.forEach { (key, value) ->
                            if (value == null) values.remove(key) else values[key] = value
                            listeners.toList().forEach { it.onSharedPreferenceChanged(preferences, key) }
                        }
                        null
                    }
                    else -> error("Unexpected editor call: ${method.name}")
                }
            } as SharedPreferences.Editor
        }
    }
}
