package com.leapauto.app.weather

import android.content.Context
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

    fun toJson(): JSONObject = JSONObject().apply {
        put("province", province)
        put("city", city)
        put("adcode", adcode)
        put("weather", weather)
        put("temperature", temperature)
        put("windDirection", windDirection)
        put("windPower", windPower)
        put("humidity", humidity)
        put("reportTime", reportTime)
        put("fetchedAtEpochMs", fetchedAtEpochMs)
    }

    companion object {
        fun fromJson(json: JSONObject): LiveWeather = LiveWeather(
            province = json.optString("province"),
            city = json.optString("city"),
            adcode = json.optString("adcode"),
            weather = json.optString("weather"),
            temperature = json.optString("temperature"),
            windDirection = json.optString("windDirection"),
            windPower = json.optString("windPower"),
            humidity = json.optString("humidity"),
            reportTime = json.optString("reportTime"),
            fetchedAtEpochMs = json.optLong("fetchedAtEpochMs", System.currentTimeMillis())
        )
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
    // 天气服务现已面向全系车型与全部车辆开放
    const val AUTHORIZED_VIN = "ALL_VEHICLES"

    /**
     * 天气服务现已面向全系车型开放，所有车辆均可使用。
     */
    fun isWeatherServiceAuthorized(vin: String? = null): Boolean = true

    // 90分钟高能有效期：严密保护高德免费配额（日消耗暴降至个位数），同时保证城市级气象实时度
    const val CACHE_TTL_MS = 90 * 60 * 1000L
    // 12小时兜底回退：当网络故障或接口偶尔超限时，展示历史天气不白屏闪烁
    const val STALE_FALLBACK_TTL_MS = 12 * 60 * 60 * 1000L

    private const val PREFS_NAME = "amap_weather_cache"
    private const val PREF_KEY_LATEST_ADCODE = "latest_adcode"
    private const val PREF_PREFIX_DATA = "weather_data_"

    // L1: 内存缓存
    private val memoryCache = ConcurrentHashMap<String, LiveWeather>()
    // In-Flight 请求并发合并锁
    private val inFlightLocks = ConcurrentHashMap<String, Any>()

    fun clearCache() {
        memoryCache.clear()
    }

    fun getCached(adcode: String, context: Context? = null): LiveWeather? {
        val clean = adcode.trim()
        if (clean.isBlank()) return null
        val now = System.currentTimeMillis()

        // 1. 优先查 L1 内存缓存
        val mem = memoryCache[clean]
        if (mem != null && now - mem.fetchedAtEpochMs < CACHE_TTL_MS) {
            return mem
        }

        // 2. 查 L2 磁盘持久化缓存
        if (context != null) {
            val disk = loadFromDisk(context, clean)
            if (disk != null && now - disk.fetchedAtEpochMs < CACHE_TTL_MS) {
                memoryCache[clean] = disk
                return disk
            }
        }
        return null
    }

    /**
     * 快速获取最近一次有效天气（冷启动预热，瞬间首屏渲染）
     */
    fun getLatestWeather(
        context: Context?,
        vin: String? = null,
        maxAgeMs: Long = STALE_FALLBACK_TTL_MS
    ): LiveWeather? {
        val now = System.currentTimeMillis()
        val mem = memoryCache.values.maxByOrNull { it.fetchedAtEpochMs }
        if (mem != null && now - mem.fetchedAtEpochMs < maxAgeMs) {
            return mem
        }
        if (context != null) {
            val latestAdcode = getLatestAdcode(context)
            if (!latestAdcode.isNullOrBlank()) {
                val disk = loadFromDisk(context, latestAdcode)
                if (disk != null && now - disk.fetchedAtEpochMs < maxAgeMs) {
                    memoryCache[disk.adcode] = disk
                    return disk
                }
            }
        }
        return null
    }

    fun getStaleFallback(cleanAdcode: String, context: Context?): LiveWeather? {
        val now = System.currentTimeMillis()
        val mem = memoryCache[cleanAdcode]
        if (mem != null && now - mem.fetchedAtEpochMs < STALE_FALLBACK_TTL_MS) {
            return mem
        }
        if (context != null) {
            val disk = loadFromDisk(context, cleanAdcode)
            if (disk != null && now - disk.fetchedAtEpochMs < STALE_FALLBACK_TTL_MS) {
                memoryCache[cleanAdcode] = disk
                return disk
            }
        }
        return null
    }

    private fun loadFromDisk(context: Context, adcode: String): LiveWeather? {
        return try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val jsonStr = prefs.getString(PREF_PREFIX_DATA + adcode, null) ?: return null
            LiveWeather.fromJson(JSONObject(jsonStr))
        } catch (_: Exception) {
            null
        }
    }

    fun saveToDisk(context: Context, weather: LiveWeather) {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit()
                .putString(PREF_PREFIX_DATA + weather.adcode, weather.toJson().toString())
                .putString(PREF_KEY_LATEST_ADCODE, weather.adcode)
                .apply()
        } catch (_: Exception) {
            // ignore disk write failure
        }
    }

    private fun getLatestAdcode(context: Context): String? {
        return try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.getString(PREF_KEY_LATEST_ADCODE, null)
        } catch (_: Exception) {
            null
        }
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
        vin: String? = null,
        context: Context? = null,
        apiKey: String = ObfuscatedSecrets.getAmapWebKey()
    ): LiveWeather? {
        val cleanAdcode = adcode.trim()
        if (cleanAdcode.isBlank()) return null

        // 1. 命中 90 分钟有效缓存直接返回（0 网络开销）
        getCached(cleanAdcode, context)?.let { return it }

        // 2. 并发合并锁：同一个 adcode 避免并发重复请求高德
        val lock = inFlightLocks.computeIfAbsent(cleanAdcode) { Any() }
        synchronized(lock) {
            // 双重检查
            getCached(cleanAdcode, context)?.let { return it }

            val candidates = if (apiKey.isNotBlank() && apiKey != ObfuscatedSecrets.getAmapWebKey()) {
                listOf(apiKey)
            } else {
                com.leapauto.app.AmapApiKeyManager.getCandidateKeys(context)
            }

            if (candidates.isEmpty()) return getStaleFallback(cleanAdcode, context)

            for (key in candidates) {
                val urlString = "$WEATHER_URL?key=$key&city=$cleanAdcode&extensions=base"
                val connection = try {
                    (URL(urlString).openConnection() as HttpURLConnection).apply {
                        requestMethod = "GET"
                        connectTimeout = 3_500
                        readTimeout = 3_500
                        setRequestProperty("User-Agent", "LeapAuto/${BuildConfig.VERSION_NAME}")
                    }
                } catch (_: Exception) {
                    continue
                }

                try {
                    if (connection.responseCode !in 200..299) continue
                    val responseText = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                    val json = JSONObject(responseText)
                    val status = json.optString("status")
                    val infocode = json.optString("infocode")

                    if (status != "1") {
                        if (com.leapauto.app.AmapApiKeyManager.isQuotaExhausted(status, infocode)) {
                            com.leapauto.app.AmapApiKeyManager.markQuotaExhausted(key, json.optString("info"))
                            continue // 额度超限，尝试下一个 Key
                        }
                        continue
                    }

                    val parsed = parseWeatherJson(responseText)
                    if (parsed != null) {
                        memoryCache[cleanAdcode] = parsed
                        if (context != null) {
                            saveToDisk(context, parsed)
                        }
                        return parsed
                    }
                } catch (_: Exception) {
                    continue
                } finally {
                    connection.disconnect()
                }
            }

            return getStaleFallback(cleanAdcode, context).also {
                inFlightLocks.remove(cleanAdcode)
            }
        }
    }
}
