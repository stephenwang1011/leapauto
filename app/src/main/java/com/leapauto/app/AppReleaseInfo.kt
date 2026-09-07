package com.leapauto.app

object AppReleaseInfo {
    val currentVersion: String
        get() = BuildConfig.VERSION_NAME

    // Update this text together with apkVersionName before each delivery package.
    const val currentReleaseNotes =
        "1. 优化车顶状态提示布局：增加车顶净空，消除胶囊与车窗前挡重叠\n" +
        "2. 优化异常状态指示：精确定位异常小卡片变红，大卡片维持中性微边框\n" +
        "3. 车模新增轻量化分层状态叠加与可点击部件快速控车热点"
}
