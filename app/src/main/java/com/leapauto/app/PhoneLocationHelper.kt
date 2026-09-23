package com.leapauto.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import androidx.core.content.ContextCompat
import java.util.Locale

object PhoneLocationHelper {

    fun hasLocationPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun getPhoneLocation(context: Context): Pair<Double, Double>? {
        if (!hasLocationPermission(context)) return null
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
        return try {
            val providers = listOf(
                LocationManager.GPS_PROVIDER,
                LocationManager.NETWORK_PROVIDER,
                LocationManager.PASSIVE_PROVIDER
            )
            var bestLocation: Location? = null
            for (provider in providers) {
                if (locationManager.isProviderEnabled(provider)) {
                    val loc = locationManager.getLastKnownLocation(provider) ?: continue
                    if (bestLocation == null || loc.time > bestLocation.time) {
                        bestLocation = loc
                    }
                }
            }
            bestLocation?.let { it.latitude to it.longitude }
        } catch (_: SecurityException) {
            null
        }
    }

    fun requestCurrentLocation(
        context: Context,
        onLocation: (Double, Double) -> Unit
    ) {
        if (!hasLocationPermission(context)) return
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return

        // 1. 先尝试获取最近缓存位置立即返回
        getPhoneLocation(context)?.let { (lat, lng) ->
            onLocation(lat, lng)
        }

        // 2. 注册单次监听获取鲜活位置
        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                onLocation(location.latitude, location.longitude)
                try {
                    locationManager.removeUpdates(this)
                } catch (_: Exception) {}
            }
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
            override fun onProviderEnabled(provider: String) {}
            override fun onProviderDisabled(provider: String) {}
        }

        try {
            if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                locationManager.requestSingleUpdate(LocationManager.NETWORK_PROVIDER, listener, Looper.getMainLooper())
            } else if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                locationManager.requestSingleUpdate(LocationManager.GPS_PROVIDER, listener, Looper.getMainLooper())
            }
        } catch (_: SecurityException) {}
    }

    fun calculateDistanceMeters(
        lat1: Double,
        lon1: Double,
        lat2: Double,
        lon2: Double
    ): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLon / 2) * Math.sin(dLon / 2)
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        return 6371000.0 * c
    }

    fun formatDistance(meters: Double): String {
        return when {
            meters < 0 -> ""
            meters < 1000 -> "${meters.toInt()}m"
            meters < 100_000 -> String.format(Locale.CHINA, "%.1fkm", meters / 1000.0)
            else -> "${(meters / 1000.0).toInt()}km"
        }
    }
}
