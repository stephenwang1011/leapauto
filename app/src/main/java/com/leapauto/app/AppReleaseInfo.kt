package com.leapauto.app

object AppReleaseInfo {
    val currentVersion: String
        get() = BuildConfig.VERSION_NAME

    // Update this text together with apkVersionName before each delivery package.
    const val currentReleaseNotes =
        "1. 主卡片视觉升级方案1【私家展厅·极简光影地台】：车模背部融入柔和轮廓泛光，车轮稳稳立足科技哑光地平线\n" +
        "2. 纯代码GPU矢量即时渲染，0字节包体积增加，昼夜双模自适应"
}
