package com.leapauto.app

object AppReleaseInfo {
    val currentVersion: String
        get() = BuildConfig.VERSION_NAME

    // Update this text together with apkVersionName before each delivery package.
    const val currentReleaseNotes =
        "1. 限制健康充电模式下充电上限不可拉至 100%，并在滑动到 100% 时提供友好保护提示\n" +
        "2. 开启健康充电时自动将 100% 档位下调至官方推荐的最佳限值 90%"
}
