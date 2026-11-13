package com.leapauto.app

object AppReleaseInfo {
    val currentVersion: String
        get() = BuildConfig.VERSION_NAME

    // Update this text together with apkVersionName before each delivery package.
    const val currentReleaseNotes = "1. 快捷控车与4x2插件新增【定时空调】：支持配置定时空调按键、预约开启绿标高亮，点击直达秒开设置弹窗\n2. 定时空调新增联动关窗与关遮阳帘：支持预约生效时自动关闭车窗及天幕遮阳帘，隔绝暴晒并防冷暖气外泄\n3. 4x2桌面插件视觉压扁收窄：增加上下透明切边并等比微缩车模，告别臃肿高大，整体呈扁平悬浮质感"
}
