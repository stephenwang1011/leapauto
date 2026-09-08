package com.leapauto.app

object AppReleaseInfo {
    val currentVersion: String
        get() = BuildConfig.VERSION_NAME

    // Update this text together with apkVersionName before each delivery package.
    const val currentReleaseNotes =
        "1. 全应用及桌面插件统一升级液态玻璃微反光晶体描边，呈现通透精致的微倒角光影\n" +
        "2. 充电中心全面升级：支持插枪即时充/停控制(193)及拔枪解锁(192)，优化指标与滑块手感"
}
