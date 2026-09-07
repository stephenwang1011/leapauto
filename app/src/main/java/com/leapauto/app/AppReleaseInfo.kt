package com.leapauto.app

object AppReleaseInfo {
    val currentVersion: String
        get() = BuildConfig.VERSION_NAME

    // Update this text together with apkVersionName before each delivery package.
    const val currentReleaseNotes =
        "1. 彻底移除行驶状态胶囊中的“已停车”冗余文本，静止驻车时保持纯净\n" +
        "2. 状态告警胶囊统一右置填补留白，彻底解除车模上方与机盖贴纸感\n" +
        "3. 空调与座舱大升级：内外循环切换、出风方向选择与座椅加热通风遥测"
}
