package com.leapauto.app

object AppReleaseInfo {
    val currentVersion: String
        get() = BuildConfig.VERSION_NAME

    // Update this text together with apkVersionName before each delivery package.
    const val currentReleaseNotes =
        "1. 直进直出快捷入口提升至首页核心首屏位置，确保全系车型秒级直达\n" +
        "2. 彻底解除任何车型与 VIN 判定条件，直进直出入口 100% 永不隐藏\n" +
        "3. 打开直进直出时若本地未就绪数字钥匙凭证，自动发起静默同步闭环\n" +
        "4. 完善全链路状态流转与 Dead-man 刹停安全机制"
}
