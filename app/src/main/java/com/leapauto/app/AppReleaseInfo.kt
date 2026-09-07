package com.leapauto.app

object AppReleaseInfo {
    val currentVersion: String
        get() = BuildConfig.VERSION_NAME

    // Update this text together with apkVersionName before each delivery package.
    const val currentReleaseNotes =
        "1. 彻底移除动态续航与标准续航标识，公里数大字紧凑贴齐状态更新文本\n" +
        "2. 进度条与电量紧贴公里数整体上移，极大释放车模上方空间，告别多层堆叠\n" +
        "3. 更新时间在左、位置信息在右，双列规整对齐，卡片气场饱满优雅"
}
