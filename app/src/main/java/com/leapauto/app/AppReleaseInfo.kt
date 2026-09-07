package com.leapauto.app

object AppReleaseInfo {
    val currentVersion: String
        get() = BuildConfig.VERSION_NAME

    // Update this text together with apkVersionName before each delivery package.
    const val currentReleaseNotes =
        "1. 快捷控车异常告警描线精修：红圈描边保持0.8dp精细标准，不额外加粗，视觉精致协调\n" +
        "2. 修复快捷控车激活按钮圆圈背景与边框跟随图标动态着色，告别蓝绿冲突\n" +
        "3. 车况卡片标题统一为“充电功率”，轮播指示器移至底部居中并做微型胶囊处理"
}
