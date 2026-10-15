# 验证记录

日期：2026-10-15 UTC。源码基线：`106b633bc4dbd0c816dd70e3160c22135e0cec6a`。

**结论：已交付可独立检查的 iOS 原生移植源码及实际编译测试的 Swift 核心。尚不能证明 iOS App 编译成功、可安装、可登录或可控车。**

## 已实际执行

环境为助手云端 Linux x86_64，官方 Swift 6.4.1（Debian 13 发行包）。下载的工具链使用官方 detached GPG signature 验证，签名来自 Swift 6.x Release Signing Key，指纹 `52BB 7E3D E28A 71BE 22EC 05FF EF80 A866 B47A 981F`。临时钥匙环不修改系统信任设置。

| 检查 | 结果 | 证据边界 |
|---|---|---|
| LeapCore 全部生产源码编译及 XCTest | 54 项：51 通过、3 跳过、0 失败 | 真正编译执行；使用合成数据与注入的离线 transport |
| SessionLifecycleStore 生产源码编译执行 | 通过 | 隔离 UserDefaults suite 中测试退出标记的设置、跨实例读取、清除；测试后删除 suite |
| 全部 App/AppTests/UITests Swift 语法解析 | 通过 | `swiftc -frontend -parse`，不等于 Apple SDK 类型检查或链接 |
| Xcode 工程、Info.plist、隐私清单 | `plutil -lint` 通过 | 校验文件格式；无签名、无模拟器运行 |
| 工程源文件引用、共享 scheme、资源和权限边界 | 结构检查和独立源码审阅通过 | App 16 个 Swift 文件、2 个资源；两个测试 target；本地 LeapCore 包 |
| Shell 脚本语法、Git 空白错误 | 通过 | 不代表 Mac 脚本已在 Mac 运行 |

执行入口为 `./scripts/validate.sh`，完整输出见 [linux-tests.log](validation/linux-tests.log)。Swift 测试日志可能采用环境本地时区；本报告日期统一使用 UTC。日志末尾的 Swift Testing “0 tests” 是另一个未使用的测试运行器，实际 XCTest 汇总在其上方，为 54 项。

### 核心测试分布

- APIClientTests：14 项，覆盖会话导入事务、设备身份、认证失败、只读重试、凭据轮换、车况安全门禁和控制 POST 不重放
- CommandTests：9 项，覆盖基础指令、温度边界、上游 payload 一致性、轮询状态与时限
- ModelTests：15 项，覆盖信号映射、未知/非法值、源时间、驾驶状态、位置新鲜度和坐标门禁
- RequestSigningTests：6 项，覆盖排序规则、签名输入、头部/路径和候选主机规则；注入测试加密实现，不冒充生产加密已通过
- SessionCryptoTests：10 项，其中 7 通过、3 跳过；覆盖导入限制、会话序列化、JWT 元数据、XOR 派生和不可用加密安全失败

明确跳过的三项：

1. `testAppleHashKnownAnswerVectors`：需要 Apple CryptoKit
2. `testAppleAESKnownAnswerAndLegacyTokenDoubling`：需要 CommonCrypto 与 CryptoKit
3. `testAppleRSAProducesUnpaddedURLSafeRandomizedCiphertext`：需要 Apple Security

Linux 生产加密不使用未经验证的替代算法，而是直接报不可用。Apple 环境必须运行上述三项；不能把 Linux 的跳过视为通过。

## 独立审查后修复

- 退出或更换会话期间的旧异步任务，不能重新写回旧凭据或覆盖新状态
- 网关刷新产生的新凭据即使在后续读请求失败、车辆列表为空时也正确持久化
- Keychain 删除失败时保留非敏感待清理标记，重启不会静默恢复已退出会话，界面提供重试
- 缺少源时间的车况不能控制车辆；缺少位置源时间不能把接收时间当作更新时间
- 非法 SOC 保持未知；胎压按上游 bar 单位显示
- hosted App 测试通过专用环境标记禁用真实会话恢复，UI 测试强制演示模式

## 尚未执行

- Xcode 的完整 SwiftUI/Security/LocalAuthentication 类型检查、链接、模拟器构建
- 6 项 App 状态测试和 1 项 UI 测试的 Apple 环境执行
- UI 截图、动态字体、无障碍、深浅色和真机交互验收
- Apple 生产加密、Keychain 读写/锁屏/删除失败、Face ID/设备密码行为
- 真实短信、登录、会话交换/续期、车辆列表、车况与任何真实控车请求
- BLE/SM2、后台运行、WidgetKit、快捷指令；这些未实现
- 代码签名、IPA、TestFlight、App Store

## Mac 一键验证

在完整 Xcode 16+ 的 Mac 安装 iOS 17+ iPhone 模拟器，进入本源码的 `ios/` 目录运行：

```sh
./scripts/validate_macos.sh
```

脚本使用本地 package，执行核心 XCTest、无签名模拟器构建及隔离的 App/UI 测试。可通过 `SIMULATOR_ID=<已安装模拟器UDID>` 指定目标。结果保存在 `build/OfflineTests-*.xcresult`。模拟器构建不需要开发者会员；真机签名另需配置 Team。脚本不会提交短信或真实车控。

首次离线运行应确认：演示标签始终可见；模拟操作不改变“真实执行”状态；取消后没有密码残留；无登录凭据时不访问真实接口。完成这些验收后，再按 [功能矩阵](FEATURE-MATRIX.md) 与 [登录/平台门槛](PLATFORM-GATES.md) 逐项推进，不能用演示运行替代实车验收。
