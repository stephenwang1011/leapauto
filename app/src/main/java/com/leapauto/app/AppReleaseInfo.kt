package com.leapauto.app

object AppReleaseInfo {
    val currentVersion: String
        get() = BuildConfig.VERSION_NAME

    // Update this text together with apkVersionName before each delivery package.
    const val currentReleaseNotes =
        "1. 修复预约充电与预约电池预热'state'参数协议：将纯数字修正为车端要求的预约HashMap对象JSON\n" +
        "2. 增加getappointment云端预约实时反查，下发结果与现有预约状态全闭环展示"
}
