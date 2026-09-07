package com.leapauto.app

object AppReleaseInfo {
    val currentVersion: String
        get() = BuildConfig.VERSION_NAME

    // Update this text together with apkVersionName before each delivery package.
    const val currentReleaseNotes =
        "1. 状态告警胶囊彻底移至HUD右侧空白区，消灭机盖上方贴纸感\n" +
        "2. 续航参数紧凑自然并列，彻底消除km与WLTC之间的拉扯空洞\n" +
        "3. 车身高点上方100%纯净留白，左右对称平衡，豪车气场完全舒展"
}
