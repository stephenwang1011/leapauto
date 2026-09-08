package com.leapauto.app

object AppReleaseInfo {
    val currentVersion: String
        get() = BuildConfig.VERSION_NAME

    // Update this text together with apkVersionName before each delivery package.
    const val currentReleaseNotes =
        "1. 全应用卡片样式全面升级：将全App所有卡片(主页/能耗/空调/设置)全面对齐45°液态玻璃柔润高光微倒角\n" +
        "2. 充电中心卡片同步接入玻璃折射质感，全应用视觉呈现浑然天成的高级统一美感"
}
