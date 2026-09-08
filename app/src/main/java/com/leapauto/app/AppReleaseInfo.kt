package com.leapauto.app

object AppReleaseInfo {
    val currentVersion: String
        get() = BuildConfig.VERSION_NAME

    // Update this text together with apkVersionName before each delivery package.
    const val currentReleaseNotes =
        "1. 充电中心视觉质感全面精细化：采用纯白悬浮卡片搭配柔和浅灰背景，对齐官方细腻质感\n" +
        "2. 优化滑块与指标排版：移除滑块粗糙点阵并定制专属白滑块，精致呈现最低温度与充电倒计时"
}
