package com.leapauto.app

object AppReleaseInfo {
    val currentVersion: String
        get() = BuildConfig.VERSION_NAME

    // Update this text together with apkVersionName before each delivery package.
    const val currentReleaseNotes =
        "1. 续航模式文案本地化：WLTC直观显示为“动态续航”，CLTC显示为“标准续航”\n" +
        "2. 模式标签端正置于km正上方，形成美观工整的复合单位指示\n" +
        "3. 更新时间置于昵称正下方，位置信息置于设置按钮正下方，双列完美对齐"
}
