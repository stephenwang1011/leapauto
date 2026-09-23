package com.leapauto.app.weather

import androidx.compose.ui.graphics.Color
import com.leapauto.app.R

data class ClimateSmartSuggestion(
    val title: String,
    val description: String,
    val actionLabel: String,
    val command: String,
    val iconRes: Int,
    val accentColor: Color
)

object ClimateSmartRecommendationPolicy {
    fun resolve(
        indoorTempStr: String?,
        weather: LiveWeather?,
        isAcOn: Boolean,
        activePreset: String? = null
    ): ClimateSmartSuggestion? {
        val indoor = indoorTempStr?.replace("°C", "", ignoreCase = true)?.replace("°", "")?.trim()?.toDoubleOrNull()
        val outdoor = weather?.tempDouble
        val humidity = weather?.humidityInt ?: 0
        val isRainOrSnow = weather?.isRainy == true || weather?.isSnowy == true

        // 1. 雨雪潮湿除雾场景 (正在下雨/下雪 或 湿度 >= 80%)
        if (isRainOrSnow || humidity >= 80) {
            if (activePreset != "windshieldDefrost") {
                return ClimateSmartSuggestion(
                    title = if (isRainOrSnow) "雨雪天气 · 易起雾" else "空气潮湿 · 易起雾",
                    description = "开启前风挡快速除雾，保持清晰行车视野",
                    actionLabel = "一键除雾",
                    command = "windshieldDefrost",
                    iconRes = R.drawable.ic_windshield_defrost,
                    accentColor = Color(0xFF00B0FF) // 冰蓝
                )
            }
        }

        // 2. 夏季烈日暴晒高温场景 (车内 >= 32℃ 或 车内高于室外 6℃+)
        if (indoor != null && (indoor >= 32.0 || (outdoor != null && indoor - outdoor >= 6.0))) {
            if (activePreset != "rapidCooling") {
                val subText = if (outdoor != null) "室外 ${outdoor.toInt()}℃ · 车内 ${indoor.toInt()}℃" else "车内已达 ${indoor.toInt()}℃"
                return ClimateSmartSuggestion(
                    title = "烈日暴晒 · 闷热高温",
                    description = "$subText，建议极速降温排热",
                    actionLabel = "极速降温",
                    command = "rapidCooling",
                    iconRes = R.drawable.ic_phosphor_snowflake,
                    accentColor = Color(0xFF0066FF) // 零跑蓝
                )
            }
        }

        // 3. 冬季严寒寒冷场景 (室外 <= 10℃ 或 车内 <= 12℃)
        if ((outdoor != null && outdoor <= 10.0) || (indoor != null && indoor <= 12.0)) {
            if (activePreset != "rapidHeating") {
                val tempText = if (outdoor != null) "室外 ${outdoor.toInt()}℃" else "车内冷"
                return ClimateSmartSuggestion(
                    title = "室外寒冷 · $tempText",
                    description = "开启极速制热升温，温暖座舱随行",
                    actionLabel = "舒适升温",
                    command = "rapidHeating",
                    iconRes = R.drawable.ic_phosphor_sun,
                    accentColor = Color(0xFFFF7A00) // 暖阳橙
                )
            }
        }

        return null
    }
}
