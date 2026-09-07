package com.leapauto.app

object AppReleaseInfo {
    val currentVersion: String
        get() = BuildConfig.VERSION_NAME

    // Update this text together with apkVersionName before each delivery package.
    const val currentReleaseNotes =
        "1. 豪华座驾HUD大瘦身：彻底移除粗笨进度条，重构为通栏单行流线型HUD\n" +
        "2. 增程车型升级为极简双微胶囊（纯电绿底/燃油暖橙），纯电车型整合电量微胶囊\n" +
        "3. 顶栏一体化收敛，车模顶部空间彻底释放，找回通透呼吸感"
}
