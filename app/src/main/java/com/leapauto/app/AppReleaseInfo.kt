package com.leapauto.app

object AppReleaseInfo {
    val currentVersion: String
        get() = BuildConfig.VERSION_NAME

    // Update this text together with apkVersionName before each delivery package.
    const val currentReleaseNotes =
        "1. 优化充电中心头部布局：移除电量百分比下的汽车图案，替换为双列动力指标卡片\n" +
        "2. 增加最低电池温度与格式化充电剩余时间展示（如“剩余1小时20分钟”，不足1小时显示“剩余X分钟”）"
}
