package com.leapauto.app

object AppReleaseInfo {
    val currentVersion: String
        get() = BuildConfig.VERSION_NAME

    // Update this text together with apkVersionName before each delivery package.
    const val currentReleaseNotes =
        "1. 新增空调运行态灵动雪花旋转动效：开启空调时雪花图标优雅旋转，停用时静止复位\n" +
        "2. 排版架构零变动，保持全部现有组件位置、尺寸与对齐标准"
}
