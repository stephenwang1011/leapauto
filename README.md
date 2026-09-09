# 零跑智控

零跑智控是一款基于 Kotlin、Jetpack Compose 的非官方零跑汽车 Android 车况与远程控车应用，面向个人学习和自用场景。它直接实现零跑中国 App 的登录、车辆路由、车况读取、信号解码和远程控制流程，不在 Android 端运行 MCP 服务。

> 本项目只适合在安全、合规并确认车辆环境允许的情况下使用。零跑接口、字段、车型能力和服务端权限都可能变化；接口接受控车指令也不等于车辆已经执行。真实登录、控车、外部地图导航、支付和发布操作都需要用户明确授权。

## 当前基线

本 README 按当前源码核对，当前应用配置为：

| 项目 | 值 |
| --- | --- |
| 应用名称 | 零跑智控 |
| applicationId | `com.leapauto.app` |
| 当前版本 | `1.5.50` |
| versionCode | `1005050` |
| Android SDK | `compileSdk 35` / `targetSdk 35` / `minSdk 26` |
| JVM | JDK 17 |
| UI | Jetpack Compose + Material 3 |
| 当前分发页 | [蒲公英](https://www.pgyer.com/lingpaozhikong) |

## 已实现能力

### 登录、会话与安全

- 使用手机号和短信验证码登录。
- 登录后获取旧链路 Token，并换取新网关 `accessToken` 与签名密钥。
- 读取账号下车辆列表，默认选择第一辆车；根据 VIN 获取并缓存 `appRegion` 路由。
- 旧 Token 和新网关 Token 在接近过期时自动续期；鉴权失效时提示重新登录。
- 4 位数字操作密码保存在本机，敏感值使用 Android Keystore + AES-GCM 加密。
- App 内控车使用操作密码构造协议请求；桌面组件的解锁、开后备箱默认还会经过指纹/强生物识别或操作密码确认。
- 退出登录会清理本地会话、操作密码、当前车辆状态和内存中的位置快照。

### 爱车首页

登录后底部导航默认是 `爱车`、`我的`。用户可在我的页面主动开启“支持作者”入口；开启后它会显示在底部导航中。

爱车首页包含：

- 按车型切换的车辆示意图。当前映射支持 C11、C10、D19、A10、B10、B01、Lafa、D99、A05，其他车型回退到 C16 图。
- 剩余续航、当前电量和电量进度条。普通 SOC 展示为向上取整的整数百分比，缺失或无效值显示 `--`。
- 快捷控车分页。首页首屏有解锁、上锁、车窗通风、后备箱；其他页提供车窗半开、车窗全关、鸣笛寻车和电池预热。
- 空调摘要与车辆位置摘要，分别进入空调详情和车辆位置详情。
- 下拉刷新车况；车辆确认处于行驶状态且爱车页在前台时，每 5 秒自动刷新一次。

### 详情页

| 页面 | 当前内容和入口 |
| --- | --- |
| `车况` | 点击首页车辆图片进入。显示门锁、空调、总里程、车内温度、车速/行驶状态、挡位和四轮胎压及异常状态。 |
| `充电与续航` | 点击首页剩余续航区域进入。显示剩余续航、SOC、满电续航、续航模式、充电状态/类型、估算功率、预计剩余时间、电压、电流、电池最低温度、健康充电和充满状态。 |
| `空调` | 显示当前空调和温度，可调 16-32 C 整数温度，并提供极速降温、极速升温、前挡除霜和关闭等操作。预设操作有显式确认，车况刷新仍是最终状态来源。 |
| `车辆位置` | 显示可信度、更新时间和新鲜度；可将当前可信坐标交给用户选择的已安装地图应用导航，不内置地图或逆地理地址。 |
| `我的` | 操作密码、外观模式、小组件透明度、小组件敏感操作验证、车型信号调试、充电提醒、支持作者入口、版本更新和退出登录。 |
| `支持作者` | 显示微信/支付宝支持二维码，支持本地的每日提醒与永久关闭；不包含自动支付或订单接口。 |

### 远程控车命令

协议命令由 `Models.kt` 的 `Commands` 统一构建，当前包括：

| 类别 | 命令 |
| --- | --- |
| 车门/尾门 | 上锁、解锁、后备箱开关、前备箱开关 |
| 车窗/遮阳帘 | 车窗半开、车窗通风、车窗全关、遮阳帘开关 |
| 寻车/电池 | 鸣笛寻车、电池预热开关 |
| 空调 | 开关空调、自定义温度、极速降温、极速升温、前挡除霜 |

普通控车请求成功后，如果服务端返回 `msgID`，App 会以 2 秒间隔最多轮询 12 次；没有消息 ID 时只报告“已发送，暂未收到车辆响应”。`cmdid=170` 的空调 POST 按现有协议策略在接口接受后结束请求，不宣称车辆遥测已经确认。

### 桌面小组件

- 使用 Android AppWidget 显示车型、剩余续航、电量进度、车辆状态和更新时间；同步层同时保存充电功率字段，但当前布局只展示充电状态文案，不展示功率数值。
- 提供解锁、上锁、空调和后备箱入口；未知空调状态时点击会回到 App，不会猜测执行开关。
- 添加小组件后立即尝试同步；联网时使用 WorkManager 约 30 分钟周期同步。
- 车辆确认未上锁时切换为约 5 秒一次的短期同步，回到已上锁或未知状态后恢复周期任务。
- 网络失败时显示最近一次成功的本地快照；控车由前台服务执行，并通过小组件和通知反馈结果。
- 我的页面支持不透明、轻透、半透、高透四档背景，以及解锁/开后备箱的敏感操作验证开关。

### 充电提醒

我的页面可以开启本地充电状态提醒。首次读取只建立基线，不立即提醒；后续状态变化时提示充电完成、充电故障或充电中断。Android 13 及以上还受系统通知权限影响，拒绝权限时 App 内车况仍可查看。

## 车辆位置和隐私边界

当前实现只处理一次车况刷新中返回的可信位置快照：

1. `SignalTable` 保留原始信号 ID `2` 和 `3`，`VehicleLocationDomain` 将 `2` 解释为经度、`3` 解释为纬度，`sts` 作为位置更新时间来源。
2. 坐标经过数字格式、经纬度范围、零坐标和短时间明显跳点校验；位置按 5 分钟新鲜、30 分钟过期前为旧、60 分钟后过期的默认策略展示。
3. 最后一次可信坐标只保存在 `MainActivity` 的内存快照中，不写入会话、组件快照、日志或文档。
4. App 不加载地图瓦片、不做逆地理地址解析，也不缓存坐标或地址；旧版 `vehicle_location_cache` 会在启动时清理。
5. 用户主动选择导航后，当前可信坐标才会交给已安装的外部地图应用。当前没有后台追踪、历史轨迹、停车位置持久化或位置分享功能。

位置坐标的当前代码契约标记为 `GCJ02`。不要根据字段名称自行替换坐标系；任何变化都必须同时更新 `VehicleLocation.kt`、测试、`API/API.md` 和产品验收记录。

## 当前限制

- 登录后默认选第一辆车，当前没有完整的车辆切换 UI。
- 充电看板、通知和组件状态已实现，但 WorkManager 不是实时机制，不能承诺秒级后台提醒。
- 当前没有经过验证的远程开始/停止充电、充电计划、OTA、保养预约或官方行程历史命令。
- 座椅、方向盘、哨兵、后视镜加热等目前只有部分状态字段，没有已验证的远程控制命令。
- 前备箱、遮阳帘、电池预热等命令的可用性受车型硬件和服务端权限影响，必须逐车实测。
- C16 胎温、综合电耗等字段没有稳定的实车数字信号契约时，不作为确定功能承诺。
- 当前没有 `app/src/androidTest/`，地图、通知、指纹、真机登录、真实车况和小组件仍需设备或模拟器验证。

## 技术架构

```text
SMS 登录
  -> LeapmotorApi 旧链路认证
  -> 新网关 accessToken/signKey
  -> 车辆列表与 appRegion 路由
  -> signal/info/query
  -> SignalTable.decode
  -> VehicleStatusMapper / ChargeStatus / VehicleLocationDomain
  -> MainActivity 状态
  -> LeapAutoScreen Compose 页面

控车入口（App 或 Widget）
  -> 操作密码 / Widget 生物识别确认
  -> Commands.build / buildAc
  -> LeapmotorApi.sendControl
  -> msgID 轮询或 cmdid=170 POST 完成策略
  -> UI、小组件、通知反馈

Widget
  -> WorkManager WidgetSyncWorker
  -> 车况刷新与充电提醒
  -> SessionStore 本地快照
  -> RemoteViews 渲染
```

主要代码入口：

```text
app/src/main/java/com/leapauto/app/
  MainActivity.kt              Activity 与业务状态编排
  LeapmotorApi.kt              接口、签名、登录、车况和控车
  Models.kt                    数据模型、控车命令、空调策略
  SignalTable.kt               signalMap 数字 ID 解码
  VehicleStatusMapper.kt       车型图、续航、电量映射
  ChargeStatus.kt              充电状态、功率、时间和类型
  VehicleLocation.kt           坐标校验、新鲜度和外部导航状态门控
  SessionStore.kt              会话、偏好、组件快照
  SecureValueStore.kt          Android Keystore 加密存储
  ControlWidget.kt              AppWidget 与点击入口
  WidgetSyncWorker.kt           后台小组件同步
  ControlService.kt             前台控车服务
  ui/LeapAutoScreen.kt          Compose 页面
  ui/theme/                     主题、颜色、字体
```

## 环境要求与首次配置

- Windows PowerShell、Android Studio 或 JDK 17。
- Android SDK 35。
- 首次用 Android Studio 打开项目后，确认 `local.properties` 中的 `sdk.dir` 指向本机 Android SDK。
- 车辆位置不依赖地图 SDK；导航时调用设备上已安装的高德、百度或其他地图应用。
- Release 签名和 PGYER 发布使用的密码/密钥只放在本地 `local.properties` 和被忽略的 `keystore/` 中。

`local.properties` 只记录键名，不要把真实值写入 README、AGENTS、源码或日志。当前构建识别的本地配置包括：

```text
sdk.dir=...
RELEASE_STORE_FILE=...
RELEASE_KEY_ALIAS=...
RELEASE_STORE_PASSWORD=...
RELEASE_KEY_PASSWORD=...
PGYER_API_KEY=...
PGYER_USER_KEY=...
```

## 构建、测试与安装

在仓库根目录执行：

```powershell
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:lintDebug
.\gradlew.bat :app:installDebug
```

产物位置：

- Debug 原始 APK：`app/build/outputs/apk/debug/app-debug.apk`，仅供本地编译、安装和验证。
- 签名 Release APK：`app/build/distributions/release/零跑智控-<versionName>.apk`。

当前仓库有 40 个 JVM 单元测试文件，覆盖命令构建、车况/续航/充电格式化、位置验证、版本比较和页面解析、导航、外观、会话过期、支持作者提醒及小组件映射/安全。涉及 Compose、资源、Manifest、Widget、通知或配置时，至少运行单元测试、Debug 构建和 lint；有设备时再做浅色/深色和交互检查。

## 版本与 Release 交付

每次正式打包前必须同时更新：

1. `app/build.gradle.kts` 的 `apkVersionName`。
2. 同文件中的 `versionCode`，计算式为 `major * 1,000,000 + minor * 1,000 + patch`。
3. `app/src/main/java/com/leapauto/app/AppReleaseInfo.kt` 的 `currentReleaseNotes`。

版本名使用三段式。历史上的 `1.0` 按 `1.0.0` 处理，每次打包递增修订号，并按项目约定将 `1.0.10` 滚动为 `1.1.0`。不要在未更新版本号时重复打包正式交付包。

构建签名 Release：

```powershell
.\scripts\package-release.ps1
```

底层任务仍可用于构建验证：

```powershell
.\gradlew.bat :app:packageReleaseApk
```

交付前检查实际签名：

```powershell
apksigner verify --verbose app/build/distributions/release/零跑智控-<version>.apk
jarsigner -verify -certs app/build/distributions/release/零跑智控-<version>.apk
```

只交付签名 Release APK，Debug 不作为交付物。V1/V2 是否实际存在以 `apksigner` 输出为准，不以 Gradle 配置意图代替结果。

## 蒲公英发布

仓库已集成 `scripts/publish-pgyer.ps1`：

```powershell
.\scripts\publish-pgyer.ps1
.\scripts\publish-pgyer.ps1 -UpdateDescription "本次更新内容"
.\scripts\publish-pgyer.ps1 -SkipBuild -UpdateDescription "已验证的更新内容"
```

脚本使用 3D AAR 执行 `:app:packageReleaseApk`。脚本读取 `local.properties` 中的 `PGYER_API_KEY`，检查对应的签名 Release APK，然后调用本机 `pgyer` CLI 上传。首次使用需要：

```powershell
npm install -g @pgyer/cli
```

发布只在用户明确授权后执行。上传后必须核对公开页 [https://www.pgyer.com/lingpaozhikong](https://www.pgyer.com/lingpaozhikong) 的版本号和更新说明。不要在命令输出、文档或截图中暴露 API Key、User Key、签名密码、Token、VIN、操作 PIN、原始接口响应或车辆位置。

## 设计与协作约定

新增或优化用户界面前，先阅读：

- [设计规范](design/leap-design.md)
- [首页视觉设计提示词](design/零跑智控-首页视觉设计提示词.md)

实现时保持暖浅灰背景、白色信息表面、Leap Blue `#0066FF`、Energy Green `#00C853` 和 Alert Red `#FF3B30` 的语义边界；不使用橙色主色、饱和渐变、霓虹效果、玻璃拟态或未授权车型图片。安全状态必须同时有文字或图标，不能只依赖颜色。

新增信号时同步更新 `SignalTable.kt`、消费模型/格式化逻辑、对应测试和 `API/API.md`；新增控车命令时同步更新 `Models.kt`、确认/反馈流程、测试和 `API/API.md`。未经验证的 signal ID 不得直接用于用户可见状态或控车判断。

项目协作角色、测试门禁、位置隐私、Release-only 规则和交付记录要求见 [AGENTS.md](AGENTS.md)。完整接口规范、协议分析与第三方参考库统一收录在 `API/` 目录（详见 [API/API.md](API/API.md)），功能规划见 [功能规划.md](功能规划.md)，位置后续候选功能见 [车辆位置未完成功能.md](车辆位置未完成功能.md)。

## 许可与责任边界

本项目不代表零跑官方，不保证接口长期可用，也不对车型差异、服务端策略、车辆损害或因误操作产生的后果负责。使用远程控车前确认车辆周边安全，并遵守车辆厂商条款、当地法律和个人数据保护要求。
