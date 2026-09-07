package com.leapauto.app

object AppReleaseInfo {
    val currentVersion: String
        get() = BuildConfig.VERSION_NAME

    // Update this text together with apkVersionName before each delivery package.
    const val currentReleaseNotes =
        "1. 更新时间置于昵称正下方，位置信息置于设置按钮正下方，两列规整对称\n" +
        "2. 彻底移除座驾主图上方所有状态告警胶囊，还车身与HUD百分之百纯净舒展\n" +
        "3. 紧急修复4x2桌面插件车图充满全屏挤掉左侧数据与底部按键的严重布局Bug"
}
