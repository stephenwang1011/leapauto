# iOS 只读状态小组件

## 范围与真实状态

支持 iOS 17+ 的 `systemSmall` / `systemMedium`。展示主 App 最近保存的 SOC、纯电续航和充电状态。所有数值都是缓存，不是实时车况；点击整张卡片只打开 `leapauto://status`。未实现网络同步、Keychain 读取、App Intent 控车或任何车辆指令。

- 初始无文件/退出标记：显示“尚未配置”，无示例数值。
- 已建立会话、尚未收到有效车况：显示“等待车况”。
- 有效 sourceTimestamp 的 5 分钟内：显示“最近缓存”与采样时间。
- 从 5 分钟至不足 30 分钟：显示“缓存较旧”与采样时间。
- 达到 30 分钟：显示“缓存已过期”，隐藏所有旧数值。
- 时间缺失、未来时间或本机时间回退：隐藏数值，显示“采样时间未知”。不以 receivedAt/savedAt 冒充车辆采样时间。
- 更新失败：保留上次有效缓存的原始时间，在其原有有效期内明确标记失败；不会延长有效期。
- 演示快照：无论正常、失败、过期，标题均带“演示”。小组件画廊占位不使用真实或伪造数值。

时间线预先放入 5/30 分钟边界，并请求约 30 分钟后再读本地缓存。主 App 变化后调用 WidgetCenter 请求重载。具体刷新由 iOS 预算/调度决定，不能承诺精确时间、常驻后台、实时状态或退出后立刻擦除系统已渲染的画面。[Apple WidgetKit 刷新说明](https://developer.apple.com/documentation/widgetkit/keeping-a-widget-up-to-date)

## Target 配置

- 主 App bundle：`com.leapauto.ios`
- Extension bundle：`com.leapauto.ios.widget`
- Widget kind：`LeapAutoStatusWidget`
- 两个 target 的 App Group：`group.com.leapauto.ios`
- 两个 target 都编译 `Shared/WidgetSnapshotStore.swift` 并链接本地 `LeapCore`。
- **仅主 App**编译 `Shared/WidgetSnapshotPublisher.swift`。
- **仅 extension**编译 `LeapAutoWidget/LeapAutoStatusWidget.swift`。
- Extension 采用本目录 Info.plist、PrivacyInfo.xcprivacy、LeapAutoWidget.entitlements；设置 `APPLICATION_EXTENSION_API_ONLY=YES`、`SKIP_INSTALL=YES`、部署目标 17.0，与主 App 相同版本/构建号，并嵌入主 App 的 PlugIns。
- 主 App 注册 `leapauto` URL scheme；此入口只导航，不接收/执行控车参数。

Bundle/App Group 都是工程占位标识。开发者需在自己的 Apple Developer 团队注册可用标识，为两个 target 启用同一个 App Group 并正确签名；若名称不可用，需同时修改两份 entitlement、共享常量和工程 bundle 配置。不得把签名配置完成或 App Group 实机访问描述成已验证。

## AppModel 接口

`WidgetSnapshotPublisher` 为同步 `@MainActor` 类型，四个方法都会在操作后请求同一个 kind 重载；错误向上传递，不吞掉清理失败：

```swift
let publisher = WidgetSnapshotPublisher()
try publisher.beginSession(generation: generation, source: .vehicle) // 或 .demo
try publisher.publish(status: status, generation: generation)
try publisher.markRefreshFailed(generation: generation)
try publisher.clear()
```

1. `AppModel.isOfflineTestRun` 时，不构造/调用 publisher，不访问真实 App Group。纯模型测试独立运行，不需要共享容器。
2. 建立/恢复会话、切换车辆、进入演示前以新的随机 UUID 调用 `beginSession`；空快照先替换前一会话/车辆数据。
3. 每次成功更新车况发布本次 `status` 与该异步请求捕获的 generation。旧 generation 被拒绝，不能重建已退出/已切换的快照。
4. 读取失败调用 `markRefreshFailed`。它不改源时间或保存时间；真实鉴权失效/退出必须调用 `clear`，不能仅标记失败。
5. `clear` 先使内存 generation 失效，再以无数据 tombstone 覆盖完整文件；写入失败时尝试删除。两条路径都失败则抛错，主 App 应清晰提示“共享小组件缓存未能清除”，允许重试，不能宣称已清理。
6. `beginSession` 写失败仍保留新的、空的内存 generation，下一次有效 publish 可恢复；旧 generation 不能发布。App 应显示首次写失败，且保留后续重试能力。

源数据映射完全复用现有 `VehicleStatus`，不新增/猜测 signal ID；续航明确使用 `electricRange`，没有自行在综合/纯电/动态里程间回退。

## 持久化与隐私

完整 JSON allowlist：schemaVersion、随机 generation、source（vehicle/demo）、batterySOC、electricRangeKM、chargingState、sourceTimestamp、savedAt、refreshFailed。logout tombstone 无 generation、source、数值或原采样时间。无 VIN、车牌、手机、账号、令牌、PIN、精确位置、原始报文。未使用 UserDefaults 存储快照或秘密。

文件位于 App Group 内专用子目录，原子写入，目录/文件使用 complete file protection，目录排除备份。读取限制为 16 KiB+1，超过上限/损坏/版本不支持/越界数值一律拒绝，显示不可用；保护数据锁定、App Group 未配置或其他 I/O 失败也不回退内存旧数值。目录保护与原子保护写的 Apple API 行为仍需真机验证。[Apple 文件保护说明](https://developer.apple.com/documentation/uikit/encrypting-your-app-s-files)

视图使用 `.privacySensitive()`，由系统根据环境进行隐私处理。此属性与文件保护不能保证已缓存的 WidgetKit 渲染图立即销毁，也不能阻止用户截图。不可用时提示解锁设备并打开 App，不伪造联网或成功更新。

## 验证与未验证项

2026-10-15：Linux Swift 6.4.1 已实际执行 19 项 `WidgetSnapshotTests`，0 失败；Widget/Shared 源文件语法解析、3 份 plist 解析及集成工程结构校验通过。

纯 Swift `WidgetSnapshotTests` 覆盖：序列化最小字段、恢复与时间不重置、5/30 分钟边界、错误恢复、错误不延寿、退出 tombstone、旧 generation 拒绝、损坏/过大/错误版本、未知数据、未来时间/时钟回退、演示标记及 timeline 边界。Linux 可实际执行这些模型测试。

Apple 源文件只能在当前 Linux 环境进行语法解析；这不等价于 Xcode 类型检查、链接、安装或 WidgetKit 实际显示成功。需在 Mac/Xcode/模拟器及真机完成：

- 两 target 编译、extension embed、App Group 权限与签名。
- 小/中尺寸、浅/深色、较大字体、VoiceOver 与隐私显示。
- 演示→真实/真实→演示/换车/退出、失败重试、App 重启、拒绝导入。
- 5/30 分钟真实系统时间线与预算；系统低电量/锁定/重启后不可读行为。
- 清除失败提示及重试；退出后旧异步请求不能发布，系统重载后无旧数据。
- 点击任意状态只打开主 App；无自动网络请求/车辆命令。
