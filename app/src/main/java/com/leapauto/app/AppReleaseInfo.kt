package com.leapauto.app

object AppReleaseInfo {
    val currentVersion: String
        get() = BuildConfig.VERSION_NAME

    // Update this text together with apkVersionName before each delivery package.
    const val currentReleaseNotes = "1. 直进直出快捷按键图标升级：采用全新高辨识度双向直进专属图标\n2. 直进直出操控盘视觉精简：去除冗余标题，提升操作区通透度与沉浸感\n3. 遥控挪车安全闭环：按住持续行驶，松手即刹，结合前后台生命周期自动熔断急停\n4. 蓝牙钥匙连接稳定性加固：引入GATT连接假死瞬态自愈机制，自动跳过异常MTU快速直连"
}
