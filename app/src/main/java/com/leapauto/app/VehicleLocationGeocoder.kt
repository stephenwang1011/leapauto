package com.leapauto.app

import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

data class GeocodedAddress(
    val shortAddress: String,
    val fullAddress: String
)

object VehicleLocationGeocoder {
    private const val REGEO_URL = "https://restapi.amap.com/v3/geocode/regeo"

    fun reverseGeocode(
        latitude: Double,
        longitude: Double,
        apiKey: String = BuildConfig.AMAP_WEB_KEY
    ): GeocodedAddress? {
        if (apiKey.isBlank()) return null
        val location = String.format(Locale.US, "%.6f,%.6f", longitude, latitude)
        val urlString = "$REGEO_URL?key=$apiKey&location=$location&extensions=all"
        val connection = (URL(urlString).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 6_000
            readTimeout = 6_000
            setRequestProperty("User-Agent", "LeapAuto/${BuildConfig.VERSION_NAME}")
        }
        return try {
            if (connection.responseCode !in 200..299) return null
            val responseText = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val json = JSONObject(responseText)
            if (json.optString("status") != "1") return null
            val regeocode = json.optJSONObject("regeocode") ?: return null
            val shortAddr = formatShortAddress(regeocode)
            val fullAddr = regeocode.optString("formatted_address").trim().ifBlank { shortAddr }
            GeocodedAddress(shortAddress = shortAddr, fullAddress = fullAddr)
        } catch (_: Exception) {
            null
        } finally {
            connection.disconnect()
        }
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
