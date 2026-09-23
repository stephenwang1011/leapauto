package com.leapauto.app.weather

import androidx.compose.ui.graphics.Color

data class CarWashAdvice(
    val levelLabel: String,      // 极佳适宜、较适宜、暂缓洗车、不宜洗车
    val reason: String,          // 简明洗车建议
    val tagColor: Color,
    val isFavorable: Boolean
)

object CarWashRecommendationPolicy {
    fun evaluate(weather: LiveWeather?): CarWashAdvice? {
        if (weather == null || weather.weather.isBlank()) return null
        val w = weather.weather
        val humidity = weather.humidityInt ?: 50
        val isRainOrSnow = weather.isRainy || weather.isSnowy

        return when {
            // 1. 降水天气（雨/雪）
            isRainOrSnow -> CarWashAdvice(
                levelLabel = "不宜洗车",
                reason = "当前有${w}天气，洗车后极易被泥水雨水重新冲刷污染，建议晴好后再洗。",
                tagColor = Color(0xFFFF5252),
                isFavorable = false
            )
            // 2. 浓雾/高湿环境 (湿度 >= 85% 或 雾霾)
            w.contains("雾") || w.contains("霾") || humidity >= 85 -> CarWashAdvice(
                levelLabel = "暂缓洗车",
                reason = if (w.contains("雾") || w.contains("霾")) {
                    "当前为${w}天气，空气高湿易结露，洗车后极易吸附尘埃，建议暂缓。"
                } else {
                    "空气湿度高达 ${humidity}%，水分挥发缓慢且极易粘附浮尘，建议暂缓洗车。"
                },
                tagColor = Color(0xFFFF9500),
                isFavorable = false
            )
            // 3. 强风天气 (风力 >= 5 级)
            (weather.windPower.toIntOrNull() ?: 0) >= 5 -> CarWashAdvice(
                levelLabel = "暂缓洗车",
                reason = "风力达到 ${weather.windPower} 级，室外风沙与扬尘较重，易刮伤或弄脏新洗车漆。",
                tagColor = Color(0xFFFF9500),
                isFavorable = false
            )
            // 4. 阴天或湿度中等偏高 (70% ~ 84%)
            w.contains("阴") || humidity in 70..84 -> CarWashAdvice(
                levelLabel = "较适宜",
                reason = "无降水天气，湿度适中，洗车后建议及时擦干水渍以防留痕。",
                tagColor = Color(0xFF0066FF),
                isFavorable = true
            )
            // 5. 晴朗干燥 (晴/多云且湿度 < 70%)
            else -> CarWashAdvice(
                levelLabel = "极佳适宜",
                reason = "气象晴好干燥，日照充沛，洗车后能长久保持车漆光洁清爽。",
                tagColor = Color(0xFF00C853),
                isFavorable = true
            )
        }
    }
}
