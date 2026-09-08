package com.leapauto.app

object AppReleaseInfo {
    val currentVersion: String
        get() = BuildConfig.VERSION_NAME

    // Update this text together with apkVersionName before each delivery package.
    const val currentReleaseNotes =
        "1. 谷电预约充电新增【未达上限继续充电】智能开关：到达停止时间若未达预设上限，继续充电直至达到目标电量\n" +
        "2. 深度闭环预约充电多重参数，支持电费优先与续航保障双模式自选"
}
