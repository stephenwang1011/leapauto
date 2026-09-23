package com.leapauto.app.weather

import androidx.compose.ui.graphics.Color
import com.leapauto.app.BuildConfig
import com.leapauto.app.ObfuscatedSecrets
import com.leapauto.app.R
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

data class LiveWeather(
    val province: String = "",
    val city: String = "",
    val adcode: String = "",
    val weather: String = "",       // 晴、多云、阴、小雨、雷阵雨等
    val temperature: String = "",   // 24 (℃)
    val windDirection: String = "", // 东南
    val windPower: String = "",     // ≤3
    val humidity: String = "",      // 56 (%)
    val reportTime: String = "",    // 2026-09-22 11:00:00
    val fetchedAtEpochMs: Long = System.currentTimeMillis()
) {
    val tempDouble: Double? get() = temperature.toDoubleOrNull()
    val humidityInt: Int? get() = humidity.toIntOrNull()
    val isRainy: Boolean get() = weather.contains("雨")
    val isSnowy: Boolean get() = weather.contains("雪")
    val isSunny: Boolean get() = weather.contains("晴")

    val summaryText: String get() = when {
        weather.isNotBlank() && temperature.isNotBlank() ->
            "$weather ${temperature}℃"
        weather.isNotBlank() -> weather
        else -> ""
    }

    val detailText: String get() = when {
        weather.isNotBlank() && temperature.isNotBlank() -> "$weather ${temperature}℃"
        weather.isNotBlank() -> weather
        else -> ""
    }
}

object WeatherVisualResolver {
    val windIconRes: Int = R.drawable.ic_weather_wind
    val humidityIconRes: Int = R.drawable.ic_weather_humidity
    val fogIconRes: Int = R.drawable.ic_weather_fog

    fun iconRes(weather: String?): Int {
        val w = weather.orEmpty()
        return when {
            w.contains("雷") -> R.drawable.ic_weather_thunder
            w.contains("雪") -> R.drawable.ic_weather_snow
            w.contains("雨") -> R.drawable.ic_weather_rain
            w.contains("风") -> R.drawable.ic_weather_wind
            w.contains("雾") || w.contains("霾") -> R.drawable.ic_weather_fog
            w.contains("多云") || (w.contains("晴") && w.contains("云")) -> R.drawable.ic_weather_cloud_sun
            w.contains("晴") -> R.drawable.ic_weather_sun
            else -> R.drawable.ic_weather_cloud
        }
    }

    fun iconTint(weather: String?, isDark: Boolean): Color {
        val w = weather.orEmpty()
        return when {
            w.contains("雷") -> Color(0xFFFFD600) // 闪电黄
            w.contains("雪") -> Color(0xFF80D8FF) // 浅霜雪蓝
            w.contains("雨") -> Color(0xFF00B0FF) // 冰蓝
            w.contains("风") -> Color(0xFF40C4FF) // 微风浅蓝
            w.contains("晴") && !w.contains("云") -> Color(0xFFFFB300) // 暖金
            w.contains("多云") || w.contains("晴") -> Color(0xFFFFCA28) // 暖阳微光
            else -> if (isDark) Color(0xFFB0BEC5) else Color(0xFF78909C) // 柔灰云
        }
    }
}

object AmapWeatherService {
    private const val WEATHER_URL = "https://restapi.amap.com/v3/weather/weatherInfo"
    private const val CACHE_TTL_MS = 45 * 60 * 1000L // 45分钟有效，保护配额与省电

    private val cache = ConcurrentHashMap<String, LiveWeather>()

    fun clearCache() {
        cache.clear()
    }

    fun getCached(adcode: String): LiveWeather? {
        val clean = adcode.trim()
        val cached = cache[clean] ?: return null
        if (System.currentTimeMillis() - cached.fetchedAtEpochMs < CACHE_TTL_MS) {
            return cached
        }
        return null
    }

    fun parseWeatherJson(jsonString: String, nowEpochMs: Long = System.currentTimeMillis()): LiveWeather? {
        return try {
            val json = JSONObject(jsonString)
            if (json.optString("status") != "1") return null
            val lives = json.optJSONArray("lives") ?: return null
            if (lives.length() == 0) return null
            val item = lives.getJSONObject(0)
            LiveWeather(
                province = item.optString("province"),
                city = item.optString("city"),
                adcode = item.optString("adcode"),
                weather = item.optString("weather"),
                temperature = item.optString("temperature"),
                windDirection = item.optString("winddirection"),
                windPower = item.optString("windpower"),
                humidity = item.optString("humidity"),
                reportTime = item.optString("reporttime"),
                fetchedAtEpochMs = nowEpochMs
            )
        } catch (_: Exception) {
            null
        }
    }

    fun fetchLiveWeather(
        adcode: String,
        apiKey: String = ObfuscatedSecrets.getAmapWebKey()
    ): LiveWeather? {
        val cleanAdcode = adcode.trim()
        if (cleanAdcode.isBlank() || apiKey.isBlank()) return null

        getCached(cleanAdcode)?.let { return it }

        val urlString = "$WEATHER_URL?key=$apiKey&city=$cleanAdcode&extensions=base"
        val connection = try {
            (URL(urlString).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 3_500
                readTimeout = 3_500
                setRequestProperty("User-Agent", "LeapAuto/${BuildConfig.VERSION_NAME}")
            }
        } catch (_: Exception) {
            return null
        }

        return try {
            if (connection.responseCode !in 200..299) return null
            val responseText = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val parsed = parseWeatherJson(responseText)
            if (parsed != null) {
                cache[cleanAdcode] = parsed
            }
            parsed
        } catch (_: Exception) {
            null
        } finally {
            connection.disconnect()
        }
    }
}
