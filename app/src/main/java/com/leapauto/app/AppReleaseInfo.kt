package com.leapauto.app

object AppReleaseInfo {
    val currentVersion: String
        get() = BuildConfig.VERSION_NAME

    // Update this text together with apkVersionName before each delivery package.
    const val currentReleaseNotes =
        "1. 新增首页点击车身主图唤起「全车健康体检」功能\n" +
        "2. 沉浸式激光雷达全息扫描动效与整车健康打分\n" +
        "3. 动力三电/底盘制动/车身密闭/环控电气18项全维体检与一键修复"
}
