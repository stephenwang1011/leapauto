package com.leapauto.app

object AppReleaseInfo {
    val currentVersion: String
        get() = BuildConfig.VERSION_NAME

    // Update this text together with apkVersionName before each delivery package.
    const val currentReleaseNotes = "1. 直进直出面板实装座舱实时通讯日志控制台，支持自动滚动、一键复制与清除"
}
