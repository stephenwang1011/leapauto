package com.leapauto.app

object AppReleaseInfo {
    val currentVersion: String
        get() = BuildConfig.VERSION_NAME

    // Update this text together with apkVersionName before each delivery package.
    const val currentReleaseNotes = "1. 首页充电胶囊精简优化，待机与解锁后统一显示微晶“充电中心”\n2. 4x2 桌面插件全面重构为差量无感静默刷新，彻底杜绝日常闪烁\n3. 优化 4x2 插件全透微晶模式下 km 单位与更新时间为高对比度黑色\n4. 优化 4x2 插件续航进度条底槽与微晶切边对比度，清晰展现已消耗续航\n5. 优化 4x2 插件“行驶中...”为科技极速青高辨识度显示，设置页卡片增加主题微晕"
}
