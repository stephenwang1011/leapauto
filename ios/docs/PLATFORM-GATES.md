# iOS 平台可行性与验收门槛

核查日期：2026-10-15。Android 源码基线：`106b633bc4dbd0c816dd70e3160c22135e0cec6a`。

本文区分“平台允许实现”“已有 Android 实现”和“iOS 已通过验收”。**可迁移不等于已经完成，更不等于已经连接真实车辆验证。** 本次核查仅阅读源码、协议文档和官方公开资料；没有使用真实账号、请求短信、访问车辆接口、扫描蓝牙或发送控车指令。iOS 实际编译/测试结果以随交付提供的验证记录为准。

## 1. 能力矩阵

| 能力 | 已核实的基础 | iOS 可行性 / 当前证据边界 | 开放真实功能前的验收门槛 |
| --- | --- | --- | --- |
| 原生中文界面、车辆概览、车况、空调、设置 | Android 有对应页面与状态模型 | 可使用 SwiftUI 重建；界面存在不证明后端连通 | Xcode 编译、真机/模拟器布局与交互检查、错误/空值/退出后状态测试 |
| 离线预览 | 可用明确标注的测试夹具演示 UI | 可独立实现，不需要真实账号 | 必须醒目标注演示；演示动作不发送网络请求，不能显示为真实车辆执行成功 |
| 短信发送 | `sendSms` 构造 RSA 加密手机号请求 | 请求本身没有调用数美 SDK；不能据此宣称完整短信登录可用 | 先完成整个登录集成，再由用户明确触发；不能在初始化、预览或自动化测试中请求短信 |
| 短信登录 | `loginWithSms` 先获取数美设备标识，再提交验证码 | **外部集成阻塞**：当前仓库只有 Android 数美二进制；iOS SDK、允许使用的配置及服务端接受情况未验证 | 合规 SDK/配置、隐私审查、真实 iPhone 标识、正确 iOS 协议身份、必要的极验挑战流程全部验证后才开放 |
| 用户主动导入自己的有效会话 | Android 已有 `loginWithRawAuth`，并非新增绕过认证的设想 | 可以迁移解析及网关换票；仍须服务器接受有效凭据，不能替代或伪造认证 | 用户在本机明确输入/导入；不记录凭据；解析错误安全；持久化仅 Keychain；到期/撤销/登出清理；明确是否发生网关请求 |
| 车辆列表、实时车况、选车与区域路由 | Android HTTP 客户端和已记录协议存在 | 网络/模型层可迁移；本次无真实后端验证 | 正确签名、合法服务器地址、会话过期处理、选车隔离、未知/缺失信号显示、刷新失败后保留时间戳而非伪造新鲜值 |
| 远程锁车/解锁/后备厢/空调等 | Android 有 PIN 加密、指令与结果查询 | 可迁移协议，但属于有实际后果的功能 | 每次明确用户动作；防重复提交；不因超时/刷新而重发命令；PIN 安全；仅车辆回执或可信后续状态支持“完成” |
| 车辆位置与外部地图 | Android 对坐标、时间和异常跳变设门槛 | 可迁移；不能擅自假定坐标系转换 | 保留 GCJ-02 既有证据边界；缺失/过期位置不能当当前位置；不落盘原始坐标；用户明确导航后才传给地图 |
| 前台 BLE 钥匙 | Android 有 FFFE/FFF2、证书、P-256 和 SM2 路径 | CoreBluetooth 支持 GATT；**需要完整平台适配，未验证实车** | iOS 设备发现/身份绑定、通知订阅、写入分片、证书作用域、协议向量、超时与确认回执、真机车辆测试 |
| 无感 BLE / 后台钥匙 | Android 使用前台 Service、开机广播和重连状态机 | **不能等价移植 Android 常驻行为**；iOS 事件驱动后台及状态恢复需要独立设计 | 后台权限/状态恢复、锁屏/系统终止/蓝牙关闭/重启等场景测试；不能承诺永远在线或离开即锁 |
| Apple Wallet / CarKey / Express Key | 上游普通 BLE 协议不是 Apple CarKey | **资格门槛**：CarKey 面向已加入 MFi 的汽车制造商并需专用 entitlement | 无制造商合作、Apple 批准和受支持车辆时，不提供或宣传此能力 |
| 桌面状态小组件 | Android 用 AppWidget / WorkManager | WidgetKit 可重做；刷新由系统预算管理 | 独立 extension、最小化共享快照、更新时间/过期状态、登出清空；不得承诺固定分钟级实时刷新 |
| 小组件控车 / 快捷指令 | iOS 提供 App Intents | 可以设计受保护的动作；不是移植 Android 前台服务 | 显式认证策略、PIN/敏感动作确认、防重复请求；不能凭系统解锁就跳过产品安全门槛 |
| 后台车况刷新与充电通知 | Android 可轮询并比较状态变化 | iOS 刷新时机由系统决定；不能保证及时发现每次变化 | 展示最后观察时间；测试通知权限拒绝与延迟；没有可靠服务端推送时不能宣称实时告警 |
| 可安装签名应用 / App Store | Linux 可以验证部分纯 Swift 逻辑 | **环境/发布门槛**：Linux 测试不覆盖 Apple SDK、签名与实机行为 | macOS/Xcode 构建、签名配置、真实设备验证；发布另需授权、隐私和 SDK/素材权利审核 |

“导入会话”仍然使用服务端账号 Token。HTTP 车况和远控协议也依赖这些凭据；不存在由本文证明的“无账号 Token 真实云控”路径。不要将 AI 计费 token 和车辆账号凭据混为一谈。

## 2. 登录的实际阻塞

### Android 源码事实

- [`ShumeiSecurityManager.kt`](https://github.com/zhihanhe/leapauto/blob/106b633bc4dbd0c816dd70e3160c22135e0cec6a/app/src/main/java/com/leapauto/app/ShumeiSecurityManager.kt) 用 `DexClassLoader` 加载 `shumei.dex`，依赖兼容 ABI 的 `libsmsdk.so`，初始化组织/应用/公钥配置后异步取得设备标识。这些 Android 文件不能作为 iOS Framework 链接。
- [`DeviceSecurity.kt`](https://github.com/zhihanhe/leapauto/blob/106b633bc4dbd0c816dd70e3160c22135e0cec6a/app/src/main/java/com/leapauto/app/DeviceSecurity.kt) 对加载、完整性、空标识、异常和超时 fail closed；获取标识最多等待 10 秒。
- [`SmsLoginProtocol.kt`](https://github.com/zhihanhe/leapauto/blob/106b633bc4dbd0c816dd70e3160c22135e0cec6a/app/src/main/java/com/leapauto/app/SmsLoginProtocol.kt) 和 [`LeapmotorApi.kt`](https://github.com/zhihanhe/leapauto/blob/106b633bc4dbd0c816dd70e3160c22135e0cec6a/app/src/main/java/com/leapauto/app/LeapmotorApi.kt) 表明：短信下发与短信登录是两个步骤。登录缺少设备标识时，在提交验证码请求前失败；服务器还可能返回极验挑战。
- 已验证的是 Android 协议：登录参数 `os=android`，网关签名字段含 `deviceType=android`。**仅改为 `ios`，或在 iOS 上继续假称 Android，均不是已验证的 iOS 集成证据。** 真实 iOS 接入合同需要另行确认。
- `loginWithRawAuth` 支持带 `accountId/token` 的 JSON、嵌套登录响应、部分别名及文本格式，随后调用 `exchangeNewGateway`。可选刷新凭据缺失会限制续期；导入解析成功不等于服务端认证成功。
- [`API/API.md`](https://github.com/zhihanhe/leapauto/blob/106b633bc4dbd0c816dd70e3160c22135e0cec6a/API/API.md) 是协议参考，但挑战字段表与当前源码存在命名差异：文档列 `lot_number` 等，当前请求代码写 `lotNumber` 等及 `requestId`。不能混合拼出未经验证的新请求。

### 官方 iOS SDK 与隐私条件

数美公开提供 `SmAntiFraud.xcframework` 接入说明，包含 `IOKit.framework` 依赖、`organization/appId/publicKey`、`create:` 与设备标识 API。供应商说明 SDK 包及资料在注册审核后提供；存在公开文档不等于本项目已经取得使用权或适用于零跑后端的配置。初始化会采集信息并请求网络，必须在适当场景与隐私同意之后。应强制 HTTPS、最小化采集并核对隐私清单，不照抄旧版任意 HTTP 放行配置。[数美 iOS 文档](https://help.ishumei.com/docs/tw/sdk/iOS/developDoc/)、[获取与集成说明](https://help.ishumei.com/docs/tw/sdk/integration/developDoc/)

Apple 禁止以设备信号派生唯一标识的指纹行为，并要求开发者对所集成 SDK 负责；专用于反欺诈的数据用途有单独的 ATT 例外，但这不构成指纹行为的普遍许可。**SDK 存在或用户同意，不足以证明能够通过 App Store 审核。** 应要求供应商说明具体版本的数据处理、允许的配置、Apple 合规性，并完成发行方审查。[Apple 隐私与数据使用](https://developer.apple.com/app-store/user-privacy-and-data-use/)

未完成上述验证前，短信登录入口应准确显示未接入原因，不能返回模拟成功、提交随机设备指纹、自动代发短信，或诱导用户将真实凭据发到聊天、日志、仓库或第三方测试站点。

## 3. BLE、后台和系统集成

### CoreBluetooth 不等于 Android 蓝牙适配层

Android [`BleScanDeviceCache.kt`](https://github.com/zhihanhe/leapauto/blob/106b633bc4dbd0c816dd70e3160c22135e0cec6a/app/src/main/java/com/leapauto/app/bluetooth/BleScanDeviceCache.kt) 按 MAC 合并广播；控制器使用该地址连接。Apple 的公开 peer 标识是系统分配的 UUID，不是 Android MAC。iOS 应按 CoreBluetooth 标识管理设备，并通过经过验证的广播/服务与账号-VIN-证书绑定建立信任；不能只凭名称、最强 RSSI 或 MAC 文本选中车辆。[Apple CBPeer.identifier](https://developer.apple.com/documentation/corebluetooth/cbpeer/identifier)

[`BluetoothKeyController.kt`](https://github.com/zhihanhe/leapauto/blob/106b633bc4dbd0c816dd70e3160c22135e0cec6a/app/src/main/java/com/leapauto/app/bluetooth/BluetoothKeyController.kt)、[`BleKeyProtocol.kt`](https://github.com/zhihanhe/leapauto/blob/106b633bc4dbd0c816dd70e3160c22135e0cec6a/app/src/main/java/com/leapauto/app/bluetooth/BleKeyProtocol.kt)、[`BleSm2Crypto.kt`](https://github.com/zhihanhe/leapauto/blob/106b633bc4dbd0c816dd70e3160c22135e0cec6a/app/src/main/java/com/leapauto/app/bluetooth/BleSm2Crypto.kt) 是当前实现依据。`API/BLUETOOTH_PROTOCOL.md` 较早描述不保存快速重连凭据，但当前源码已包含重连凭据存储，故不能只按旧文档判断当前行为。SM2/SM3/SM4 当前依赖 Bouncy Castle JVM 实现，iOS 需要合适的原生实现及独立向量测试；不能把通用 P-256 ECDH 当作 SM2 协商替代品。

CoreBluetooth 提供 GATT 服务/特征读写与单次最大写入长度查询。权限需要 `NSBluetoothAlwaysUsageDescription`；应由明确的蓝牙功能操作触发，而非在普通预览启动时索权。[Apple CBPeripheral](https://developer.apple.com/documentation/corebluetooth/cbperipheral)、[受保护资源](https://developer.apple.com/documentation/bundleresources/protected-resources)

### 后台的承诺必须收窄

`bluetooth-central` 后台模式能让系统为相关蓝牙事件唤醒 App，且可采用状态保存/恢复；它不提供无限运行。后台扫描会合并重复发现，发现速度也可能下降。Android 的 `START_STICKY` Service 与 `BOOT_COMPLETED` 接收器不能直接复用。后台 BLE 需实机验证，不能作为没有认证/证书/用户授权的控车捷径。[Apple CoreBluetooth 后台指南](https://developer.apple.com/library/archive/documentation/NetworkingInternetWeb/Conceptual/CoreBluetooth_concepts/CoreBluetoothBackgroundProcessingForIOSApps/PerformingTasksWhileYourAppIsInTheBackground.html)

普通后台内容刷新由系统选择运行时间并限制时长。充电告警只能基于实际收到的状态变化；没有及时观察就不能保证立即通知。[Apple 后台策略](https://developer.apple.com/documentation/backgroundtasks/choosing-background-strategies-for-your-app)

WidgetKit 使用时间线和系统刷新预算，extension 不会因显示在桌面就持续执行。App Intents 的默认认证策略是 `alwaysAllowed`，敏感车辆动作必须显式设置并保留应用自己的确认机制。更保守的初版可让小组件链接到 App 的确认页面。[WidgetKit 更新](https://developer.apple.com/documentation/widgetkit/keeping-a-widget-up-to-date)、[交互式小组件](https://developer.apple.com/documentation/widgetkit/adding-interactivity-to-widgets-and-live-activities)、[AppIntent.authenticationPolicy](https://developer.apple.com/documentation/appintents/appintent/authenticationpolicy)

Apple CarKey 只控制已配置到 Wallet 的受支持车辆，并要求 `com.apple.developer.carkey.session` entitlement；申请者必须是 MFi 汽车制造商。普通 CoreBluetooth 客户端不因此获得 Wallet、Express Key 或硬件安全钥匙能力。[Apple CarKey](https://developer.apple.com/documentation/carkey)

## 4. 必须保持的安全与验收边界

1. 测试只用合成夹具和注入的网络 transport；CI、预览、截图和启动不得访问真实车辆端点。
2. 缺失值显示“未知/暂无数据”；状态失败、命令受理、车辆完成是不同状态。不要用 `0`、`false` 或旧演示数据补出正常车况。
3. 命令 POST 不自动重试，包括 401、超时和传输中断。凭据刷新可以为下一次明确用户操作准备，但不能默默重放可能已执行的动作。
4. 操作中锁定重复触发；每次结果绑定当前会话、当前 VIN 和操作 ID。切换车辆/账号、退出或取消后，旧异步结果不得覆盖新状态。
5. 不把任意导入 JSON 的服务器 URL 当作可信地址；凭据只发给经过验证的零跑 HTTPS 服务。错误信息、URL 日志及崩溃报告不得包含 Token、PIN、手机号、VIN 或坐标。
6. Keychain、LocalAuthentication、蓝牙权限、WidgetKit、后台恢复和 Apple SDK 编译需要对应 Apple 环境验收。纯 Swift 单元测试通过只能证明覆盖的逻辑，不证明可安装、可登录或可控车。
7. 真实 SMS、指令、外部地图传输和发布必须在用户明确授权范围内执行；本文件不授权任何真实操作。
