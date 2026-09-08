package com.leapauto.app

object AppReleaseInfo {
    val currentVersion: String
        get() = BuildConfig.VERSION_NAME

    // Update this text together with apkVersionName before each delivery package.
    const val currentReleaseNotes =
        "1. 充电上限滑块与官方设计像素级对齐：浅薄荷绿微轨内置白色垂直细柱，立体白旋钮比例协调\n" +
        "2. 最佳限值90%紧凑上移，插枪按键严格对齐解锁充电枪(白线框)+结束充电(深色实心)官方样式"
}
