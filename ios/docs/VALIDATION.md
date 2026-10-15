# 验证记录：第二轮增量

日期：2026-10-15 UTC。Android基线：`106b633bc4dbd0c816dd70e3160c22135e0cec6a`；首版iOS提交：`51e24af63c1f25acbfd5c398578609f75a2ae697`。

**结论：新增只读WidgetKit工程、BLE协议/状态机、离线密码参考及禁用的CoreBluetooth适配已通过当前云环境可执行的验证。未证明iOS App或Widget编译成功、可安装、可登录或可用蓝牙控车。**

## 实际执行结果

使用官方Swift 6.4.1 Debian13 x86_64工具链。工具链签名验证与首版环境记录见 [VALIDATION-V1.md](VALIDATION-V1.md)。未安装第三方密码库，全部Swift Package没有外部依赖。

| 检查 | 真实结果 | 能证明的范围 |
|---|---|---|
| LeapCore全部生产源码编译及XCTest | 136项：133通过、3跳过、0失败 | 合成fixture/注入离线transport下的逻辑 |
| BLEProtocolTests | 36/36通过 | 上游帧向量、所有切分位置、最大长度、2000组确定性噪声、ACK队列与确认门禁 |
| BLECryptoTests | 27/27通过，独立复跑一致 | SM3/SM4标准/OpenSSL/上游合成向量，SM2协议编排与失败关闭；不含真实SM2曲线/X.509后端 |
| WidgetSnapshotTests | 19/19通过 | 最小持久化字段、代次/退出、5/30分钟时间边界、损坏/错误/演示标识 |
| 首版核心其他测试 | 54项：51通过、3跳过 | API、指令、模型、会话与签名输入 |
| SessionLifecycleStore生产源码烟测 | 编译执行通过 | 隔离UserDefaults suite，非敏感退出标记设置/恢复/清除 |
| App/Widget/Shared/AppTests/UITests | Swift语法解析通过 | 不等于Apple SDK类型检查或链接 |
| Xcode工程与配置 | plutil及独立结构审查通过 | 19 App源、2 Widget源，两个测试target、扩展嵌入、包链接、权限与App Group一致性 |
| 脚本、文档、源码ZIP | Shell/Python语法、文档链接、白空格/文件清单检查 | 源码结构及交付完整性 |

聚合入口：`./scripts/validate.sh`。完整本轮日志：[linux-v2-tests.log](validation/linux-v2-tests.log)。首版54项日志仍保留为 [linux-tests.log](validation/linux-tests.log)。日志可能采用环境本地时区，本报告日期为UTC。日志末尾另一个Swift Testing运行器报告“0 tests”不影响其上方实际XCTest的136项汇总。

## 仍然跳过的3项

- `testAppleHashKnownAnswerVectors`：需要Apple CryptoKit
- `testAppleAESKnownAnswerAndLegacyTokenDoubling`：需要CommonCrypto和CryptoKit
- `testAppleRSAProducesUnpaddedURLSafeRandomizedCiphertext`：需要Apple Security

这些是云端HTTP协议生产加密路径，不应与新增离线SM3/SM4参考混同。Linux不使用替代生产算法，Mac必须真正执行这3项。

## 互审结果与修复

- 确认Widget发布在AppModel代次检查之后；旧账号/旧车辆异步结果不能写回新快照，自动化测试完全跳过共享容器
- 修复未登录导入失败遗留“等待车况”快照；失败后要么恢复仍有效旧会话的原时间状态，要么清为空
- 退出先使快照发布代次失效；文件覆盖/删除均失败时明确提示并提供本地重试，不宣称清理成功
- 确认Shared存储编入App和Widget，Publisher只编入App，扩展嵌入PlugIns并具备相同App Group
- 确认CBCentralManager初始化及scan/connect/write都被固定false门禁阻止；无配置开关或自动扫描
- 互审发现一个SM4测试fixture单字符转录错误；通过原Android向量及独立Node/OpenSSL输出核实后仅修正fixture，再独立复跑27项通过，没有改算法来迎合错误期望

独立代码审查未发现阻断当前源码交付的已知缺陷，但不能替代Apple构建或密码安全审计。

## 明确未验证

- Xcode完整类型检查、链接、模拟器安装及运行；7项App状态测试和1项UI测试尚未执行
- WidgetKit小/中尺寸、动态字体、VoiceOver、App Group签名/容器、文件保护、锁屏和系统缓存刷新
- CoreBluetooth delegate在Apple SDK下的并发/类型兼容、权限、实际GATT、分片、断连或任何车辆操作
- 完整SM2曲线运算、严格X.509验证、证书信任/获取/撤销、账号-VIN-设备绑定；默认provider始终不可用
- 自编离线SM3/SM4的独立密码审计、恒定时间和内存擦除保证；当前禁止接入真实控制
- 真实短信、登录、会话交换/续期、车辆接口、签名发布或付费服务

## Mac下一步

安装完整Xcode16+和iOS17+ iPhone模拟器，确保Python3可用，在`ios/`运行：

```sh
./scripts/validate_macos.sh
```

脚本执行离线核心测试、无签名模拟器构建及隔离App/UI测试。可用`SIMULATOR_ID=<已有UDID>`指定设备；结果在`build/OfflineTests-*.xcresult`。没有在本云环境运行过该Mac流程。

小组件还需按 [INTEGRATION.md](../LeapAutoWidget/INTEGRATION.md) 配置两target相同App Group，真机另需用户自己的Team/签名。未配置时不会伪造共享成功。WidgetKit调度由系统决定，不能保证定时实时刷新或退出后立即擦除已渲染卡片。[Apple刷新说明](https://developer.apple.com/documentation/widgetkit/keeping-a-widget-up-to-date)、[App Group配置](https://developer.apple.com/documentation/xcode/configuring-app-groups)

登录SDK授权和完整BLE后端是独立阻塞；可先在Mac完成App/Widget离线验收，再按 [功能矩阵](FEATURE-MATRIX.md) 和 [BLE边界](BLE-OFFLINE.md) 继续。不得为了测试而自动发送真实短信、蓝牙认证或车辆命令。
