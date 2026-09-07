package com.leapauto.app

object AppReleaseInfo {
    val currentVersion: String
        get() = BuildConfig.VERSION_NAME

    // Update this text together with apkVersionName before each delivery package.
    const val currentReleaseNotes =
        "1. 续航单位升级：WLTC置于km正上方并统一12sp字号，形成紧凑美观的垂直复合指标\n" +
        "2. 更新时间置于昵称正下方，位置信息置于设置按钮正下方，双列完美对齐\n" +
        "3. 彻底移除座驾主图上方所有状态告警胶囊，还车身与HUD百分之百纯净舒展"
}
