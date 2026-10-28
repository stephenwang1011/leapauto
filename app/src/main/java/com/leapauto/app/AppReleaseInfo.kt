package com.leapauto.app

object AppReleaseInfo {
    val currentVersion: String
        get() = BuildConfig.VERSION_NAME

    // Update this text together with apkVersionName before each delivery package.
    const val currentReleaseNotes = "1. 雾川雪境深色模式下，首页昵称下方的更新时间改为黑色，提升辨识度\n2. 首页深色模式告警文字与图标使用更鲜明的红色，其他页面配色保持不变"
}
