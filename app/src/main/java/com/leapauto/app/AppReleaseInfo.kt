package com.leapauto.app

object AppReleaseInfo {
    val currentVersion: String
        get() = BuildConfig.VERSION_NAME

    // Update this text together with apkVersionName before each delivery package.
    const val currentReleaseNotes =
        "1. 界面完整对应充电计划底层协议全部7项参数(chargeEnable, chargesoc, circulation, cycles, starttime, endtime, recharge)\n" +
        "2. 新增循环模式(单次/周期)与周一至周日7天独立勾选+快捷预设，并增加协议参数实时预览"
}
