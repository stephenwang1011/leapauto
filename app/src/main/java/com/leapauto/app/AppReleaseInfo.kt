package com.leapauto.app

object AppReleaseInfo {
    val currentVersion: String
        get() = BuildConfig.VERSION_NAME

    // Update this text together with apkVersionName before each delivery package.
    const val currentReleaseNotes =
        "1. 恢复纯电车型WLTC/CLTC模式标签的半透明微边框胶囊包裹质感\n" +
        "2. 彻底消除纯电指标子群百分比底部裁切，紧凑行高自然贴齐大数字\n" +
        "3. 保持续航条与百分比长度严格对齐km与WLTC，双车型完美适配"
}
