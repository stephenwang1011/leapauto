package com.leapauto.app

object AppReleaseInfo {
    val currentVersion: String
        get() = BuildConfig.VERSION_NAME

    // Update this text together with apkVersionName before each delivery package.
    const val currentReleaseNotes = "1. 修复纯电车型误显燃油续航及能量条双重显示的问题\n2. 纯电车型大字回归真实纯电里程，消除虚假综合续航"
}
