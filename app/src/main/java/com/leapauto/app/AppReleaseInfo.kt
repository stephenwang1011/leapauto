package com.leapauto.app

object AppReleaseInfo {
    val currentVersion: String
        get() = BuildConfig.VERSION_NAME

    // Update this text together with apkVersionName before each delivery package.
    const val currentReleaseNotes =
        "1. 桌面插件(4x2与2x2)适配空调运行态灵动雪花旋转动效：开启空调时雪花图标持续自转，关闭时静止复位\n" +
        "2. 保持全部组件排版结构与功能逻辑一致"
}
