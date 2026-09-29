package com.leapauto.app

object AppReleaseInfo {
    val currentVersion: String
        get() = BuildConfig.VERSION_NAME

    // Update this text together with apkVersionName before each delivery package.
    const val currentReleaseNotes =
        "1. 重构设置中心视觉与信息架构，划分为座驾互联、个性化与系统服务三大微晶分组\n" +
        "2. 底部新增微晶安全登出卡片，全面优化页面层次与交互呼吸感"
}
