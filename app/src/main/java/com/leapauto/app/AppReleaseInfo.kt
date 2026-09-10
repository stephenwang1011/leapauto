package com.leapauto.app

object AppReleaseInfo {
    val currentVersion: String
        get() = BuildConfig.VERSION_NAME

    // Update this text together with apkVersionName before each delivery package.
    const val currentReleaseNotes =
        "1. 4x2 桌面插件纯电车型参数与首页卡片 1:1 对齐（26sp 续航、12sp km、13sp 百分比及 110dp×4.5dp 能量槽）\n" +
        "2. 全车健康体检扩充至 21 项全维深度检测（三电充放电、四轮温感、天幕遮阳帘、双区温控等）\n" +
        "3. 体检列表 UI 重构：微晶面板胶囊对齐排版，各系统检测结果层次清晰井然有序\n" +
        "4. 实装 1.0dp 晶锐全息雷达激光与粒子尾迹动效"
}
