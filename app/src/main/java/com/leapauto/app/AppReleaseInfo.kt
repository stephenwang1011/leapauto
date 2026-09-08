package com.leapauto.app

object AppReleaseInfo {
    val currentVersion: String
        get() = BuildConfig.VERSION_NAME

    // Update this text together with apkVersionName before each delivery package.
    const val currentReleaseNotes =
        "1. 主界面质感蜕变升级方案1【曜石冷钛·晶透液态玻璃】：背景沉降为科技冷钛灰渐变，彻底解决泛白平光感\n" +
        "2. 引入45°斜向高光折射微倒角与72%晶莹水晶毛玻璃透光率，拉开多级车规级空间景深"
}
