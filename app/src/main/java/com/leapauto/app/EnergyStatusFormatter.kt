package com.leapauto.app

/** Converts raw energy enum values into safe user-facing labels. */
object EnergyStatusFormatter {
    fun rangeMode(raw: String?): String = when (raw?.trim()) {
        "0" -> "标准续航"
        "1" -> "动态续航"
        null, "" -> "暂不可用"
        else -> "状态未知"
    }
}
