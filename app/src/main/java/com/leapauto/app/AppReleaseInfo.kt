package com.leapauto.app

object AppReleaseInfo {
    val currentVersion: String
        get() = BuildConfig.VERSION_NAME

    // Update this text together with apkVersionName before each delivery package.
    const val currentReleaseNotes =
        "1. 移除锁屏常驻通知相关模块与设置入口，精简后台服务保持纯粹轻量\n" +
        "2. 优化主页车窗状态弹窗：支持四门车窗开度网格呈现与一键全关快捷控制\n" +
        "3. 完善车况数据展示逻辑，通过全量单元测试与稳定性回归验证"
}
