package com.leapauto.app

object AppReleaseInfo {
    val currentVersion: String
        get() = BuildConfig.VERSION_NAME

    // Update this text together with apkVersionName before each delivery package.
    const val currentReleaseNotes =
        "1. 系统设置页面结构化分组重构，加入账号信息栏与全卡片微边框\n" +
        "2. 车型配置卡片升级为多标签展示，退出登录操作语义强化\n" +
        "3. 全面美化重构登录界面，增加品牌徽标与行内验证码按钮"
}
