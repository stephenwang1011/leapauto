# 零跑智控 iOS 原生移植工程

这是从 [zhihanhe/leapauto](https://github.com/zhihanhe/leapauto) 的 Android 版本移植出的 **SwiftUI 开发预览工程**。基线提交：`106b633bc4dbd0c816dd70e3160c22135e0cec6a`。Android 原目录保留。

> **状态：核心 Swift 包已在 Linux 实际编译并运行离线测试；Xcode 编译、模拟器 UI、iPhone 和实车尚未验证。不是可直接安装的 IPA，也不是功能完整的发布版。**
>
> 普通短信登录、BLE 钥匙、后台常驻、小组件及部分高级车控尚未移植。详见[功能与平台门槛](docs/PLATFORM-GATES.md)、[实现范围](docs/FEATURE-MATRIX.md)和[验证记录](docs/VALIDATION.md)。

## 1. 在 Mac 上打开

需要 Xcode 16 或更高版本、iOS 17 或更高版本。首次只运行离线演示，无需账号、开发者会员或车钥匙。

1. 打开 `LeapAuto.xcodeproj`，选择 `LeapAuto` scheme。
2. 选择一个 iPhone 模拟器，点击 Run。
3. 首页点击「体验离线演示」。所有数据都来自本机样例，确认控车仅展示流程。
4. 真机运行时在 Signing & Capabilities 选择自己的 Team，必要时修改 bundle identifier。工程未包含签名证书或 provisioning profile。

工程使用本地 Swift Package，没有 CocoaPods、XcodeGen 或外部 Swift 包依赖。直接打开即可，无需先生成项目。

### 一键离线验证

在 Mac 的 `ios/` 目录运行：

```sh
./scripts/validate_macos.sh
```

该脚本执行结构检查、LeapCore 全量测试、未签名模拟器构建和离线 App/UI 测试。若没有可用 iPhone 模拟器，请先在 Xcode 安装 iOS runtime。可用 `SIMULATOR_ID=<已有模拟器UDID>` 指定设备。脚本不会调用真实车辆接口。

仅验证平台无关核心：

```sh
swift test --package-path Packages/LeapCore
```

Linux 会明确跳过 Apple CryptoKit/AES/RSA 测试；这不构成这些路径已经通过的证据。

## 2. 目前能做什么

- 原生 SwiftUI 首页、车况、续航充电、空调、车辆位置状态和设置
- 清晰标识的离线演示；未知信号不伪装成正常值
- 开发者本人有效会话 JSON 导入、网关交换、车辆列表/切换、路由与车况请求
- 按上游协议构造基础车身、空调和充电指令；结果查询和超时状态明确区分
- iPhone 本机钥匙串保存会话；每次控车要求四位操作密码和本机设备密码/生物识别
- 仅允许最新五分钟内、速度为零且 P 挡的明确车况发出指令；缺少状态、源时间或未知挡位会阻止控制
- 单次命令只发一次 POST，不因网络失败、认证失败或超时自动重发

上述网络/车控路径已有代码和离线测试，但**未用真实账号或车辆验证**，不能保证服务端接受 iOS 请求。

## 3. 登录为什么仍有限制

Android 登录依赖数美设备安全 SDK 和极验风控。仓库只有 Android 平台实现；不能在 iPhone 执行 Android DEX/原生库，也不能用随机标识冒充已验证设备。

正式短信登录需要获准使用的 iOS SDK、合法有效的厂商配置、用户隐私同意流程、服务端适配及真机验收。目前短信请求和短信登录入口都明确失败，不收集设备指纹，也不会发送验证码。

保留上游已有的开发者会话恢复思路：用户在自己设备上通过文件选择器导入本人已有会话。这个入口不是普通登录的替代完成证明，也不绕过服务端验证。

### 开发者会话文件要求

仅在自己的开发设备准备 JSON，不要发到聊天、邮件、Git 或公共附件。

- 顶层 `clientPlatform`：原会话身份 `ios` 或 `android`，不得伪造
- 顶层 `deviceID`：与该会话绑定的原设备标识
- `accountId` 和 `token`：本人有效的旧服务登录凭据，可位于上游 `data.appLoginVO` 等对象内
- 可选 `refreshToken` 和 `phone`：旧服务续期需要原手机号及对应刷新凭据
- 可选原始 `tokenObtainedAt`（毫秒）及 `tokenExpired`（有效秒数）：不人为延长已过期会话
- 文件上限 128 KB；不是任意文本/抓包全文导入器

导入按钮会把账号会话用于零跑 HTTPS 接口验证，并把成功得到的会话存入本机 Keychain。请先阅读页面提示。未提供或无权获得原会话时，继续使用离线演示，等待正式 iOS 登录接入。

## 4. 安全边界

- Keychain 为 `WhenUnlockedThisDeviceOnly`，关闭同步；不把凭据/PIN写入 UserDefaults、日志或源码
- 操作密码仅本次使用，不保存；离开确认页或进入后台时清除输入框
- 车辆位置仅保留内存；当前没有外部地图导航、地址上传或位置后台跟踪
- 请求只允许已验证的零跑 HTTPS 域名，全部重定向拒绝，禁用 HTTP 缓存和 cookie
- 退出登录使未完成响应失效；本机删除失败会明确提示并提供重试按钮。仅在本机偏好中保留非敏感“待清理”标记，重启也不会恢复该会话
- 服务端受理不等于车辆完成。未提供查询 ID、超时或网络错误都不会显示“车辆已执行”
- 不含 OTA 安装、自动泊车、支付、发布、Apple Wallet/CarKey、自动近车解锁或离车上锁
- 当前没有后台蓝牙、位置或通知权限，也没有未实现的后台模式声明

任何实车验收都应由车辆所有者明确执行，确保周围安全。不要以测试为由发送真实解锁、开箱或充电指令。

## 5. 目录

```text
ios/
  LeapAuto.xcodeproj/           已生成的 Xcode 工程与共享 scheme
  LeapAuto/App/                状态管理、演示数据与 App 入口
  LeapAuto/Views/              SwiftUI 页面
  LeapAuto/Security/           Keychain 与本机身份认证
  Packages/LeapCore/           协议、网络、模型、指令和离线 XCTest
  LeapAutoTests/               App 状态离线测试（需 Xcode）
  LeapAutoUITests/             离线演示和确认取消测试（需 Xcode）
  scripts/                    结构验证、工程生成、Mac 验证脚本
  docs/                       范围、限制、验收与归属
```

新增或删除 Swift 文件后运行 `python3 scripts/generate_project.py`，再验证。生成器不创建账号或证书。

## 6. 后续验收顺序

1. Mac 上完整运行 `validate_macos.sh`，处理所有真实 Apple SDK 类型/链接/UI 问题
2. 审查系统加密已知向量、Keychain 持久化/删除与 Face ID/设备密码失败路径
3. 确定正式 iOS 登录 SDK 的使用权、配置、隐私和服务端支持
4. 用户在本人开发设备验证登录、续期、车况和车辆切换
5. 用户明确授权后，在安全场地逐项验证真实命令与超时，不自动重试
6. BLE、WidgetKit、通知和高级功能分别实施，不宣称 Android 常驻行为在 iOS 等价可用

未经正式验收，不用于依赖自动解锁/锁车或安全告警的实际场景。

## 归属与许可

上游为非官方零跑客户端，原作者及来源见 [ATTRIBUTION.md](docs/ATTRIBUTION.md)。所检查提交未包含明确的 LICENSE 文件。此源码交付不构成重新授权或授予商业发布权；对外分发、再许可或商用前需要确认原作者和第三方权利。不代表零跑或 Apple 官方产品。
