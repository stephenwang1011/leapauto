package com.leapauto.app

object AppReleaseInfo {
    val currentVersion: String
        get() = BuildConfig.VERSION_NAME

    // Update this text together with apkVersionName before each delivery package.
    const val currentReleaseNotes =
        "1. 修复部分纯电车型主界面有续航、桌面插件不显示公里数的问题\n" +
        "2. 统一主界面与 4x2、2x2 插件的动力类型判定，修复后台刷新和旧缓存导致的显示异常\n" +
        "3. 保留增程车型零油量显示，修复 REEV 车型识别"
}
