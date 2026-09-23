package com.leapauto.app

import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

data class GeocodedAddress(
    val shortAddress: String,
    val fullAddress: String,
    val adcode: String = "",
    val city: String = ""
)

object VehicleLocationGeocoder {
    private const val REGEO_URL = "https://restapi.amap.com/v3/geocode/regeo"
    private const val MAX_CACHE_ENTRIES = 20
    private const val CACHE_DISTANCE_THRESHOLD_METERS = 80.0 // 80米范围内直接命中缓存
    private const val CACHE_EXPIRY_MS = 2 * 60 * 60 * 1000L // 2小时有效期

    private data class CachedGeo(
        val latitude: Double,
        val longitude: Double,
        val address: GeocodedAddress,
        val cachedAtEpochMs: Long
    )

    private val cache = mutableListOf<CachedGeo>()

    @Synchronized
    fun clearCache() {
        cache.clear()
    }

    fun reverseGeocode(
        latitude: Double,
        longitude: Double,
        apiKey: String = ObfuscatedSecrets.getAmapWebKey()
    ): GeocodedAddress? {
        val now = System.currentTimeMillis()

        // 1. 优先检查高命中率内存距离缓存 (0ms 瞬间响应)
        synchronized(this) {
            val cached = cache.firstOrNull { entry ->
                now - entry.cachedAtEpochMs < CACHE_EXPIRY_MS &&
                    calculateDistanceMeters(latitude, longitude, entry.latitude, entry.longitude) <= CACHE_DISTANCE_THRESHOLD_METERS
            }
            if (cached != null) {
                return cached.address
            }
        }

        if (apiKey.isBlank()) return null
        val location = String.format(Locale.US, "%.6f,%.6f", longitude, latitude)
        val urlString = "$REGEO_URL?key=$apiKey&location=$location&extensions=all"
        val connection = (URL(urlString).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 3_500
            readTimeout = 3_500
            setRequestProperty("User-Agent", "LeapAuto/${BuildConfig.VERSION_NAME}")
        }
        return try {
            if (connection.responseCode !in 200..299) return null
            val responseText = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val json = JSONObject(responseText)
            if (json.optString("status") != "1") return null
            val regeocode = json.optJSONObject("regeocode") ?: return null
            val addressComponent = regeocode.optJSONObject("addressComponent") ?: JSONObject()
            val adcode = addressComponent.optString("adcode").trim()
            val cityRaw = addressComponent.optString("city").trim().takeUnless { it == "[]" || it.isBlank() }
                ?: addressComponent.optString("province").trim()
            val shortAddr = formatShortAddress(regeocode)
            val fullAddr = regeocode.optString("formatted_address").trim().ifBlank { shortAddr }
            val result = GeocodedAddress(
                shortAddress = shortAddr,
                fullAddress = fullAddr,
                adcode = adcode,
                city = cityRaw
            )

            // 存入内存缓存
            synchronized(this) {
                if (cache.size >= MAX_CACHE_ENTRIES) {
                    cache.removeAt(0)
                }
                cache.add(CachedGeo(latitude, longitude, result, now))
            }
            result
        } catch (_: Exception) {
            null
        } finally {
            connection.disconnect()
        }
    }

    private fun calculateDistanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLon / 2) * Math.sin(dLon / 2)
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        return 6371000.0 * c
    }

    internal fun formatShortAddress(regeocode: JSONObject): String {
        val addressComponent = regeocode.optJSONObject("addressComponent") ?: JSONObject()
        val province = addressComponent.optString("province").trim()
        val cityRaw = addressComponent.optString("city").trim().takeUnless { it == "[]" || it.isBlank() }
            ?: province
        val city = cityRaw.removeSuffix("市").removeSuffix("地区").removeSuffix("特别行政区").trim()

        val formattedAddress = regeocode.optString("formatted_address").trim()
        val district = addressComponent.optString("district").trim().takeUnless { it == "[]" }.orEmpty()
        val township = addressComponent.optString("township").trim().takeUnless { it == "[]" }.orEmpty()

        // 1. Try AOI (Area of Interest, e.g. residential compound, mall, building complex)
        val aois = regeocode.optJSONArray("aois")
        val aoiName = (0 until (aois?.length() ?: 0))
            .mapNotNull { aois?.optJSONObject(it)?.optString("name")?.trim() }
            .firstOrNull { it.isNotBlank() }

        // 2. Try stripping administrative prefixes (province, city, district, township)
        var remainder = formattedAddress
        if (province.isNotBlank()) remainder = remainder.removePrefix(province)
        val cityToRemove = addressComponent.optString("city").trim().takeUnless { it == "[]" }.orEmpty()
        if (cityToRemove.isNotBlank()) remainder = remainder.removePrefix(cityToRemove)
        if (district.isNotBlank()) remainder = remainder.removePrefix(district)
        if (township.isNotBlank()) remainder = remainder.removePrefix(township)
        remainder = remainder.trim()

        // 3. Fallback to POI if remainder is blank
        val poiName = if (aoiName.isNullOrBlank() && remainder.isBlank()) {
            val pois = regeocode.optJSONArray("pois")
            (0 until (pois?.length() ?: 0))
                .mapNotNull { pois?.optJSONObject(it)?.optString("name")?.trim() }
                .firstOrNull { it.isNotBlank() }
        } else null

        val specific = (aoiName ?: remainder.takeIf { it.isNotBlank() } ?: poiName ?: formattedAddress).trim()

        return specific
            .removePrefix(cityRaw).trim()
            .removePrefix(city).trim()
            .ifBlank { specific }
    }
}
