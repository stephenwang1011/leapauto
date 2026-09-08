package com.leapauto.app

object AppReleaseInfo {
    val currentVersion: String
        get() = BuildConfig.VERSION_NAME

    // Update this text together with apkVersionName before each delivery package.
    const val currentReleaseNotes =
        "1. 修复预约充电与预约电池预热操作密码鉴权：携带加密oppwd及完整起止时间规则，彻底解决参数不合法错误\n" +
        "2. 桌面2x2小组件崩溃彻底修复，全新车模展厅与纯净双能源正常呈现"
}
