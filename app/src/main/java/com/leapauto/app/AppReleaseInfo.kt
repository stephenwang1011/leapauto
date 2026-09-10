package com.leapauto.app

object AppReleaseInfo {
    val currentVersion: String
        get() = BuildConfig.VERSION_NAME

    // Update this text together with apkVersionName before each delivery package.
    const val currentReleaseNotes =
        "1. 支持账号绑定多车辆一键切换座驾\n" +
        "2. 首页座驾名称支持下拉切换与设置页切换卡片\n" +
        "3. 优化近6周百公里能耗与周能耗分布界面"
}
