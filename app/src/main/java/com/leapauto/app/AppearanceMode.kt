package com.leapauto.app

/** User-selected app appearance. SYSTEM remains the default for existing installs. */
enum class AppearanceMode(val label: String) {
    SYSTEM("跟随系统"),
    LIGHT("浅色模式"),
    DARK("深色模式");

    fun resolvesToDark(systemInDarkTheme: Boolean): Boolean = when (this) {
        SYSTEM -> systemInDarkTheme
        LIGHT -> false
        DARK -> true
    }

    companion object {
        fun fromPreference(value: String?): AppearanceMode =
            entries.firstOrNull { it.name == value } ?: SYSTEM
    }
}
