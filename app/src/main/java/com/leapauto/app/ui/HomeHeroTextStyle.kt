package com.leapauto.app.ui

import androidx.compose.ui.graphics.Color

internal object HomeHeroTextStyle {
    data class Colors(val primary: Color, val secondary: Color, val mileage: Color, val unit: Color)

    fun resolve(themeId: String, isDark: Boolean, isHeroDark: Boolean): Colors {
        // Stone's light header uses dark text, while range text stays white over the building.
        if (themeId == "5" && !isDark && isHeroDark) {
            return Colors(Color(0xFF202020), Color(0xFF404040), Color.White, Color.White)
        }
        val primary = if (isHeroDark) Color.White else Color(0xFF202020)
        val secondary = if (isHeroDark) Color.White.copy(alpha = 0.90f) else Color(0xFF555555)
        val unit = if (themeId == "8" && !isDark) Color(0xFF00B42A) else secondary
        return Colors(primary, secondary, primary, unit)
    }
}
