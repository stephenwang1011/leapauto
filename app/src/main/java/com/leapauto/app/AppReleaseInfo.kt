package com.leapauto.app

object AppReleaseInfo {
    val currentVersion: String
        get() = BuildConfig.VERSION_NAME

    // Update this text together with apkVersionName before each delivery package.
    const val currentReleaseNotes =
        "1. 车况动力轮播小格增加微型翻页指示器\n" +
        "2. 门锁已锁状态增加安心安全绿语义，车况数值半粗体强化\n" +
        "3. 优化车模接地暗影、空调微氛围底色与周能耗表盘排版"
}
