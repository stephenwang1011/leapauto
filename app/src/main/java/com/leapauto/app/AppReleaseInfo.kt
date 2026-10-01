package com.leapauto.app

object AppReleaseInfo {
    val currentVersion: String
        get() = BuildConfig.VERSION_NAME

    // Update this text together with apkVersionName before each delivery package.
    const val currentReleaseNotes = "1. 增加直进直出分片下发 25ms 物理节流间隔，防止车端蓝牙接收缓冲区溢出"
}
