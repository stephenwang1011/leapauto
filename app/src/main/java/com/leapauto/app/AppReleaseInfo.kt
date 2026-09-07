package com.leapauto.app

object AppReleaseInfo {
    val currentVersion: String
        get() = BuildConfig.VERSION_NAME

    // Update this text together with apkVersionName before each delivery package.
    const val currentReleaseNotes =
        "1. 修复快捷控车按钮激活态配色：外层圆圈与边框跟随图标颜色（绿/红/中性），告别蓝绿冲突\n" +
        "2. 车况卡片标题统一为“充电功率”，轮播指示器移至底部居中并做微型胶囊处理\n" +
        "3. 车锁已锁移除绿字恢复常态，未锁车时与车窗未关保持统一红框警示样式"
}
