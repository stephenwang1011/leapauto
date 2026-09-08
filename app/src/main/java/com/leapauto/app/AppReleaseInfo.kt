package com.leapauto.app

object AppReleaseInfo {
    val currentVersion: String
        get() = BuildConfig.VERSION_NAME

    // Update this text together with apkVersionName before each delivery package.
    const val currentReleaseNotes =
        "1. 彻底移除车身下方额外绘制的暗色椭圆阴影，恢复零跑官方原版PNG纯净通透光影\n" +
        "2. 消除阴影脏污感，车身与曜石冷钛液态玻璃底板更清爽融合"
}
