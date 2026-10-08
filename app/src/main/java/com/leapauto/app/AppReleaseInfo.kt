package com.leapauto.app

object AppReleaseInfo {
    val currentVersion: String
        get() = BuildConfig.VERSION_NAME

    // Update this text together with apkVersionName before each delivery package.
    const val currentReleaseNotes = "1. 最终交付版 不再维护\n2. app源码下载地址：https://pan.quark.cn/s/a1df64a4212e"
}
