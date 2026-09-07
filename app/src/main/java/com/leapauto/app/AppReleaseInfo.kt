package com.leapauto.app

object AppReleaseInfo {
    val currentVersion: String
        get() = BuildConfig.VERSION_NAME

    // Update this text together with apkVersionName before each delivery package.
    const val currentReleaseNotes =
        "1. 车况卡片标题统一为“充电功率”，轮播指示器移至底部居中并做微型胶囊处理\n" +
        "2. 车锁已锁移除绿字恢复常态，未锁车时与车窗未关保持统一红框警示样式\n" +
        "3. 续航大数字与下方电量进度条间隙收紧至3~4dp，排版极致紧致一体"
}
