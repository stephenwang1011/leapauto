package com.leapauto.app.weather

import androidx.compose.ui.graphics.Color
import com.leapauto.app.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AmapWeatherTest {

    @Test
    fun `parse real amap live weather json response`() {
        val json = """
            {
                "status": "1",
                "count": "1",
                "info": "OK",
                "infocode": "10000",
                "lives": [
                    {
                        "province": "浙江",
                        "city": "杭州市",
                        "adcode": "330100",
                        "weather": "多云",
                        "temperature": "26",
                        "winddirection": "东南",
                        "windpower": "≤3",
                        "humidity": "58",
                        "reporttime": "2026-09-22 14:00:00"
                    }
                ]
            }
        """.trimIndent()

        val weather = AmapWeatherService.parseWeatherJson(json)
        assertNotNull(weather)
        assertEquals("杭州市", weather!!.city)
        assertEquals("330100", weather.adcode)
        assertEquals("多云", weather.weather)
        assertEquals("26", weather.temperature)
        assertEquals(26.0, weather.tempDouble!!, 0.01)
        assertEquals("58", weather.humidity)
        assertEquals(58, weather.humidityInt)
        assertEquals("东南", weather.windDirection)
        assertEquals("≤3", weather.windPower)
        assertFalse(weather.isRainy)
        assertFalse(weather.isSnowy)
        assertEquals("多云 26℃", weather.summaryText)
    }

    @Test
    fun `weather visual resolver selects appropriate icons and tints`() {
        assertEquals(R.drawable.ic_weather_sun, WeatherVisualResolver.iconRes("晴"))
        assertEquals(R.drawable.ic_weather_cloud_sun, WeatherVisualResolver.iconRes("多云"))
        assertEquals(R.drawable.ic_weather_cloud_sun, WeatherVisualResolver.iconRes("晴间多云"))
        assertEquals(R.drawable.ic_weather_cloud, WeatherVisualResolver.iconRes("阴"))
        assertEquals(R.drawable.ic_weather_rain, WeatherVisualResolver.iconRes("小雨"))
        assertEquals(R.drawable.ic_weather_rain, WeatherVisualResolver.iconRes("暴雨到大暴雨"))
        assertEquals(R.drawable.ic_weather_thunder, WeatherVisualResolver.iconRes("雷阵雨"))
        assertEquals(R.drawable.ic_weather_snow, WeatherVisualResolver.iconRes("中雪"))
        assertEquals(R.drawable.ic_weather_wind, WeatherVisualResolver.iconRes("大风"))
        assertEquals(R.drawable.ic_weather_fog, WeatherVisualResolver.iconRes("大雾"))
        assertEquals(R.drawable.ic_weather_fog, WeatherVisualResolver.iconRes("重度霾"))

        assertEquals(R.drawable.ic_weather_wind, WeatherVisualResolver.windIconRes)
        assertEquals(R.drawable.ic_weather_humidity, WeatherVisualResolver.humidityIconRes)

        assertEquals(Color(0xFFFFB300), WeatherVisualResolver.iconTint("晴", isDark = true))
        assertEquals(Color(0xFFFFCA28), WeatherVisualResolver.iconTint("多云", isDark = true))
        assertEquals(Color(0xFF00B0FF), WeatherVisualResolver.iconTint("阵雨", isDark = true))
        assertEquals(Color(0xFFFFD600), WeatherVisualResolver.iconTint("雷阵雨", isDark = true))
    }

    @Test
    fun `smart climate recommendation suggests rapid cooling during scorching sun or high temp`() {
        val hotWeather = LiveWeather(
            city = "杭州市",
            weather = "晴",
            temperature = "33",
            humidity = "45"
        )
        // 车内 35℃，炎热暴晒
        val suggestion = ClimateSmartRecommendationPolicy.resolve(
            indoorTempStr = "35°C",
            weather = hotWeather,
            isAcOn = false
        )
        assertNotNull(suggestion)
        assertEquals("rapidCooling", suggestion!!.command)
        assertEquals("极速降温", suggestion.actionLabel)
        assertTrue(suggestion.title.contains("烈日暴晒"))
    }

    @Test
    fun `smart climate recommendation suggests defrosting during rain or high humidity`() {
        val rainyWeather = LiveWeather(
            city = "上海市",
            weather = "中雨",
            temperature = "22",
            humidity = "88"
        )
        val suggestion = ClimateSmartRecommendationPolicy.resolve(
            indoorTempStr = "23°C",
            weather = rainyWeather,
            isAcOn = true
        )
        assertNotNull(suggestion)
        assertEquals("windshieldDefrost", suggestion!!.command)
        assertEquals("一键除雾", suggestion.actionLabel)
        assertTrue(suggestion.title.contains("雨雪天气"))
    }

    @Test
    fun `smart climate recommendation suggests rapid heating during cold winter`() {
        val coldWeather = LiveWeather(
            city = "北京市",
            weather = "晴",
            temperature = "4",
            humidity = "30"
        )
        val suggestion = ClimateSmartRecommendationPolicy.resolve(
            indoorTempStr = "6°C",
            weather = coldWeather,
            isAcOn = false
        )
        assertNotNull(suggestion)
        assertEquals("rapidHeating", suggestion!!.command)
        assertEquals("舒适升温", suggestion.actionLabel)
        assertTrue(suggestion.title.contains("室外寒冷"))
    }

    @Test
    fun `smart climate recommendation returns null when already comfortable`() {
        val mildWeather = LiveWeather(
            city = "昆明市",
            weather = "多云",
            temperature = "22",
            humidity = "55"
        )
        val suggestion = ClimateSmartRecommendationPolicy.resolve(
            indoorTempStr = "23°C",
            weather = mildWeather,
            isAcOn = true
        )
        assertNull(suggestion)
    }
}
