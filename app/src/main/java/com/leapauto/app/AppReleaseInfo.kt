package com.leapauto.app

object AppReleaseInfo {
    val currentVersion: String
        get() = BuildConfig.VERSION_NAME

    // Update this text together with apkVersionName before each delivery package.
    const val currentReleaseNotes =
        "1. 主界面全面换装拟态磨砂透光玻璃：全卡片升级为微透明磨砂质感与物理高光微折射微边框\n" +
        "2. 背景融入柔和透光环境光场，形成晶莹剔透、高级内敛的悬浮通透感\n" +
        "3. 状态告警胶囊统一警示红与表情符号(🚨⚠️🪟🔓⚡)，彻底消除违和遮挡"
}
