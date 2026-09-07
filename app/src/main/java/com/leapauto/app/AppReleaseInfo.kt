package com.leapauto.app

object AppReleaseInfo {
    val currentVersion: String
        get() = BuildConfig.VERSION_NAME

    // Update this text together with apkVersionName before each delivery package.
    const val currentReleaseNotes =
        "1. 主页增程双能源全新重构方案A【双段一体化能量条】：彻底消灭彩色药丸边框补丁，呈现理想/问界豪华电车标杆质感\n" +
        "2. 顶部微高光一体化双段能量槽（纯电呼吸绿/油量橙黄），下方搭配极简纯净字符排版，浑然天成"
}
