package com.leapauto.app

object AppReleaseInfo {
    val currentVersion: String
        get() = BuildConfig.VERSION_NAME

    // Update this text together with apkVersionName before each delivery package.
    const val currentReleaseNotes =
        "1. 全部弹窗与抽屉去透明化重构：预约充电抽屉、电池预热抽屉、时间选择器及快捷菜单全部升级为100%纯色不透明容器\n" +
        "2. 彻底杜绝底层界面文字与车模透光穿帮，质感扎实纯正，深浅色模式均保持高对比阅读体验"
}
