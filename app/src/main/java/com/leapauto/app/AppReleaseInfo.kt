package com.leapauto.app

object AppReleaseInfo {
    val currentVersion: String
        get() = BuildConfig.VERSION_NAME

    // Update this text together with apkVersionName before each delivery package.
    const val currentReleaseNotes =
        "1. 状态告警胶囊全面采用警示红（尾门、车门、车窗、门锁）与生动图标（🚨⚠️🪟🔓⚡）\n" +
        "2. 彻底移除静止时的已停车冗余标签，只在真实行驶时展示\n" +
        "3. 状态告警胶囊统一右置填补留白，车身高点上方100%纯净留白"
}
