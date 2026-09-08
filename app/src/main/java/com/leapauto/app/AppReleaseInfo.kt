package com.leapauto.app

object AppReleaseInfo {
    val currentVersion: String
        get() = BuildConfig.VERSION_NAME

    // Update this text together with apkVersionName before each delivery package.
    const val currentReleaseNotes =
        "1. 修复重复周期协议格式：完全对齐零跑车端T-Box底层7位0/1掩码协议(如每天1,1,1,1,1,1,1、工作日1,1,1,1,1,0,0)\n" +
        "2. 深度打通车端config.3充电计划遥测反显，支持车端实际计划双向同步"
}
