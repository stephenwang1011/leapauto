package com.leapauto.app

object AppReleaseInfo {
    val currentVersion: String
        get() = BuildConfig.VERSION_NAME

    // Update this text together with apkVersionName before each delivery package.
    const val currentReleaseNotes =
        "1. 修正近6周百公里能耗日期展示为周日截止日期\n" +
        "2. 优化周能耗分布各能耗能量槽与百分比徽章展示\n" +
        "3. 优化纯电续航大字与电量百分比融合排版"
}
