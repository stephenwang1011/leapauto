package com.leapauto.app

object AppReleaseInfo {
    val currentVersion: String
        get() = BuildConfig.VERSION_NAME

    // Update this text together with apkVersionName before each delivery package.
    const val currentReleaseNotes =
        "1. 快捷控车异常高亮升级：车窗未关/尾门开启/未锁车时，按钮圆圈一周边框自动变红并加深浅红底色\n" +
        "2. 修复快捷控车激活按钮圆圈背景与边框跟随图标动态着色，告别蓝绿冲突\n" +
        "3. 车况卡片标题统一为“充电功率”，轮播指示器移至底部居中并做微型胶囊处理"
}
