package com.leapauto.app

object AppReleaseInfo {
    val currentVersion: String
        get() = BuildConfig.VERSION_NAME

    // Update this text together with apkVersionName before each delivery package.
    const val currentReleaseNotes =
        "1. 精确校准最佳限值90%间距：向上偏移16dp消除系统触控盒空隙，小三角精准贴合滑块旋钮正下方\n" +
        "2. 纯几何矢量绘制三角形消除字体行高虚空，保持2dp车规级黄金排版"
}
