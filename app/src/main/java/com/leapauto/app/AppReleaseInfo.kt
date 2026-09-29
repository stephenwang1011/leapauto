package com.leapauto.app

object AppReleaseInfo {
    val currentVersion: String
        get() = BuildConfig.VERSION_NAME

    // Update this text together with apkVersionName before each delivery package.
    const val currentReleaseNotes =
        "1. 完整接入基于官方 th1.d 规范的流式分片拼装重组器，彻底消除多分包解密丢包风险\n" +
        "2. GATT 建立连接增加参数协商缓冲延时，彻底杜绝服务发现时报 133/129 状态错误\n" +
        "3. 优化直进直出全链路时序调度，保障挪车响应度与车端状态实时同步"
}
