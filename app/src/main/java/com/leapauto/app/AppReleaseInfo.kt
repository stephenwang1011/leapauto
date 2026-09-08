package com.leapauto.app

object AppReleaseInfo {
    val currentVersion: String
        get() = BuildConfig.VERSION_NAME

    // Update this text together with apkVersionName before each delivery package.
    const val currentReleaseNotes =
        "1. 移除充电中心底部的开发者调试参数面板，保持界面纯净清爽\n" +
        "2. 深度优化预约充电与预约电池预热指令下发时序，规避车机系统繁忙冲突"
}
