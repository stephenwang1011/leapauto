package com.leapauto.app

object AppReleaseInfo {
    val currentVersion: String
        get() = BuildConfig.VERSION_NAME

    // Update this text together with apkVersionName before each delivery package.
    const val currentReleaseNotes =
        "1. 充电中心文案与指标精炼：最低电池温度简化为“电池温度”，未充电功率显示“未充电”，剩余时间精炼为“X时Y分”\n" +
        "2. 优化插枪控充操作：充电中文案简化为纯文字“停止充电”且独占整行，未充电呈现纯文字“开始充电”与“解锁拔枪”"
}
