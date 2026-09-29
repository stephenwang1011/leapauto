package com.leapauto.app

object AppReleaseInfo {
    val currentVersion: String
        get() = BuildConfig.VERSION_NAME

    // Update this text together with apkVersionName before each delivery package.
    const val currentReleaseNotes =
        "1. 彻底移除应用内置高德APIKey，全面升级为车主专属独享配置模式\n" +
        "2. 优化高德Web服务配置界面，提供清晰的开放平台申请指引与状态指示"
}
