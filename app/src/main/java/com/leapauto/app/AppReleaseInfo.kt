package com.leapauto.app

object AppReleaseInfo {
    val currentVersion: String
        get() = BuildConfig.VERSION_NAME

    // Update this text together with apkVersionName before each delivery package.
    const val currentReleaseNotes =
        "1. 优化异常状态指示：精确定位异常小卡片变红，大卡片维持中性微边框\n" +
        "2. 胎压异常红框精确定位到故障轮胎，不再导致整块面板大面积变红\n" +
        "3. 车模新增轻量化分层状态叠加与可点击部件快速控车热点"
}
