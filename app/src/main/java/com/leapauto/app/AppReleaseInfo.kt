package com.leapauto.app

object AppReleaseInfo {
    val currentVersion: String
        get() = BuildConfig.VERSION_NAME

    // Update this text together with apkVersionName before each delivery package.
    const val currentReleaseNotes =
        "1. 柔化45°液态玻璃倒角边框：描边精细收缩至1.0dp，受光强度柔化至78%，消除生硬白线感\n" +
        "2. 呈现流动润泽的水晶微折射质感，与冷钛底板浑然天成"
}
