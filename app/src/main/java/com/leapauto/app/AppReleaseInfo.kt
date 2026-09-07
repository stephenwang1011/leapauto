package com.leapauto.app

object AppReleaseInfo {
    val currentVersion: String
        get() = BuildConfig.VERSION_NAME

    // Update this text together with apkVersionName before each delivery package.
    const val currentReleaseNotes =
        "1. 充电抽屉新增【谷电预约充电】：支持开关与起止时间调节，默认 23:00~次日07:00 谷电时段自动充\n" +
        "2. 快捷控车/车况新增【预约电池预热】专属抽屉：支持开关与开始时间调节，默认 23:00 自动唤醒恒温预热\n" +
        "3. 全程遵循业务分散归属原则，主页排版绝对零挤压，附带专业时间微调选择器"
}
