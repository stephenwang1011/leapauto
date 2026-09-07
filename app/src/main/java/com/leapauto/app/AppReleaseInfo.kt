package com.leapauto.app

object AppReleaseInfo {
    val currentVersion: String
        get() = BuildConfig.VERSION_NAME

    // Update this text together with apkVersionName before each delivery package.
    const val currentReleaseNotes =
        "1. 注入北欧冰川微渐变氛围背景：沉稳浅天青灰底色大幅凸显白色车身高光轮廓与磨砂玻璃质感\n" +
        "2. 排版架构零变动，100%保持全部现有组件位置、尺寸、对齐与交互\n" +
        "3. 快捷控车异常告警描边恢复0.8dp精细标准，不额外加粗，精致协调"
}
