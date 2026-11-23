package com.leapauto.app.charge

import android.content.Context
import android.content.SharedPreferences
import kotlin.math.roundToInt

class ChargePriceStore(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    fun loadDefaultSlowPrice(): Double {
        return prefs.getFloat(KEY_DEFAULT_SLOW_PRICE, 0.60f).toDouble()
    }

    fun saveDefaultSlowPrice(price: Double) {
        prefs.edit().putFloat(KEY_DEFAULT_SLOW_PRICE, price.toFloat()).apply()
    }

    fun loadDefaultFastPrice(): Double {
        return prefs.getFloat(KEY_DEFAULT_FAST_PRICE, 1.50f).toDouble()
    }

    fun saveDefaultFastPrice(price: Double) {
        prefs.edit().putFloat(KEY_DEFAULT_FAST_PRICE, price.toFloat()).apply()
    }

    fun loadCustomPrice(startTs: Long): Double? {
        if (startTs <= 0L) return null
        val key = "$PREFIX_CUSTOM_PRICE$startTs"
        return if (prefs.contains(key)) {
            prefs.getFloat(key, 0f).toDouble()
        } else {
            null
        }
    }

    fun saveCustomPrice(startTs: Long, price: Double) {
        if (startTs <= 0L) return
        val key = "$PREFIX_CUSTOM_PRICE$startTs"
        prefs.edit().putFloat(key, price.toFloat()).apply()
    }

    fun resolvePrice(startTs: Long, isFastCharge: Boolean): Double {
        val custom = loadCustomPrice(startTs)
        if (custom != null && custom > 0.0) {
            return custom
        }
        return if (isFastCharge) loadDefaultFastPrice() else loadDefaultSlowPrice()
    }

    companion object {
        private const val PREF_NAME = "charge_price_store"
        private const val KEY_DEFAULT_SLOW_PRICE = "default_slow_price"
        private const val KEY_DEFAULT_FAST_PRICE = "default_fast_price"
        private const val PREFIX_CUSTOM_PRICE = "custom_price_"

        fun computeCost(energyKwh: Double, pricePerKwh: Double): Double {
            if (energyKwh <= 0.0 || pricePerKwh <= 0.0) return 0.0
            val total = energyKwh * pricePerKwh
            return (total * 100.0).roundToInt() / 100.0
        }

        fun computePeriodCost(
            totalEnergyKwh: Double,
            fastChargePercent: Double,
            fastPrice: Double,
            slowPrice: Double
        ): Double {
            if (totalEnergyKwh <= 0.0) return 0.0
            val clampedPercent = fastChargePercent.coerceIn(0.0, 100.0)
            val fastEnergy = totalEnergyKwh * (clampedPercent / 100.0)
            val slowEnergy = maxOf(0.0, totalEnergyKwh - fastEnergy)
            val fastCost = computeCost(fastEnergy, fastPrice)
            val slowCost = computeCost(slowEnergy, slowPrice)
            return ((fastCost + slowCost) * 100.0).roundToInt() / 100.0
        }

        fun formatEnergyDisplay(energyKwh: Double): String {
            if (energyKwh <= 0.0) return "0"
            return if (energyKwh >= 10_000.0) {
                String.format(java.util.Locale.US, "%.0f", energyKwh)
            } else {
                String.format(java.util.Locale.US, "%.1f", energyKwh)
            }
        }

        fun formatCostDisplay(costYuan: Double): String {
            if (costYuan <= 0.0) return "0"
            return when {
                costYuan >= 10_000.0 -> String.format(java.util.Locale.US, "%.0f", costYuan)
                costYuan >= 1_000.0 -> String.format(java.util.Locale.US, "%.1f", costYuan)
                else -> String.format(java.util.Locale.US, "%.2f", costYuan)
            }
        }
    }
}
