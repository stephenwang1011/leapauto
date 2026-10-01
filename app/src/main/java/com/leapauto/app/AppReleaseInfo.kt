package com.leapauto.app

object AppReleaseInfo {
    val currentVersion: String
        get() = BuildConfig.VERSION_NAME

    // Update this text together with apkVersionName before each delivery package.
    const val currentReleaseNotes = "1. 优化前台蓝牙探查机制，彻底消除走近车旁的15秒连接超时等待盲区"
}
