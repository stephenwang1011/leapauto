package com.leapauto.app

object AppReleaseInfo {
    val currentVersion: String
        get() = BuildConfig.VERSION_NAME

    // Update this text together with apkVersionName before each delivery package.
    const val currentReleaseNotes =
        "1. 恢复车图 100% 饱满大气原始比例，消除车身缩小与比例失调\n" +
        "2. 状态胶囊重构至四周开阔留白区域（左上前挡/机盖、右上尾门、左下门锁、右下充电口），彻底杜绝遮挡车身\n" +
        "3. 优化异常状态指示：精确定位异常小卡片变红，大卡片维持中性微边框"
}
