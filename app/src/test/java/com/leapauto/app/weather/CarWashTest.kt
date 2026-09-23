package com.leapauto.app.weather

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CarWashTest {

    @Test
    fun `foggy and high humidity weather suggests delaying car wash`() {
        val weather = LiveWeather(
            city = "襄阳市",
            weather = "雾",
            temperature = "20",
            humidity = "95",
            windPower = "≤3"
        )
        val advice = CarWashRecommendationPolicy.evaluate(weather)
        assertNotNull(advice)
        assertEquals("暂缓洗车", advice!!.levelLabel)
        assertFalse(advice.isFavorable)
        assertTrue(advice.reason.contains("雾"))
    }

    @Test
    fun `rainy weather advises against car wash`() {
        val weather = LiveWeather(
            city = "杭州市",
            weather = "中雨",
            temperature = "21",
            humidity = "88"
        )
        val advice = CarWashRecommendationPolicy.evaluate(weather)
        assertNotNull(advice)
        assertEquals("不宜洗车", advice!!.levelLabel)
        assertFalse(advice.isFavorable)
        assertTrue(advice.reason.contains("雨"))
    }

    @Test
    fun `sunny dry weather is highly favorable for car wash`() {
        val weather = LiveWeather(
            city = "上海市",
            weather = "晴",
            temperature = "24",
            humidity = "45"
        )
        val advice = CarWashRecommendationPolicy.evaluate(weather)
        assertNotNull(advice)
        assertEquals("极佳适宜", advice!!.levelLabel)
        assertTrue(advice.isFavorable)
        assertTrue(advice.reason.contains("晴好"))
    }

    @Test
    fun `overcast weather is moderately favorable`() {
        val weather = LiveWeather(
            city = "成都市",
            weather = "阴",
            temperature = "20",
            humidity = "75"
        )
        val advice = CarWashRecommendationPolicy.evaluate(weather)
        assertNotNull(advice)
        assertEquals("较适宜", advice!!.levelLabel)
        assertTrue(advice.isFavorable)
    }

    @Test
    fun `null or blank weather returns null advice`() {
        assertNull(CarWashRecommendationPolicy.evaluate(null))
        assertNull(CarWashRecommendationPolicy.evaluate(LiveWeather()))
    }
}
