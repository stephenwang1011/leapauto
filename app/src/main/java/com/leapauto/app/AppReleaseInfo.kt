package com.leapauto.app

object AppReleaseInfo {
    val currentVersion: String
        get() = BuildConfig.VERSION_NAME

    // Update this text together with apkVersionName before each delivery package.
    const val currentReleaseNotes =
        "1. 快捷控车与底部能耗卡片指示器重构为超纤巧微型胶囊\n" +
        "2. 大幅缩减指示器高度与纵向占用空间，界面排版更紧凑耐看\n" +
        "3. 车况卡片支持全闭锁安全绿与异常隐患红边框指示"
}
