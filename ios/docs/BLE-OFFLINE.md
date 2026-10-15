# BLE 离线移植边界

当前包含协议和传输适配源码，**不包含可使用的蓝牙钥匙**。`BLEHardwareFeatureGate.isEnabled` 固定返回 `false`；没有设置项、环境变量或依赖注入开关能够启用它。App 不创建 `BLETransport`，即使创建也不会初始化 `CBCentralManager`；扫描、连接和写入全部拒绝。未声明蓝牙权限或后台模式。

## 已移植且可以离线测试

- `0xAB/AC/AE/EE` 帧长度检查、流式重组、坏输入重新同步和缓冲上限
- 广播 UUID 中的协议 minor、车型默认值、CRC8、已解密文本解析及事件字段
- 两种分片 profile 均按现有 Kotlin/test 的 160 字节硬上限；旧 API.md 的 197 字节描述不作为实现依据
- iOS 使用 `maximumWriteValueLength` 的真实 payload 上限，不再像 Android ATT MTU 一样减 3
- 串行 GATT 写 ACK 队列、一次连接一次手动操作确认、会话/车辆代次变化、超时和未知结果
- SM3、SM4-CBC/PKCS7、SM2身份摘要、单块KDF及密码卡派生的离线参考；详细向量与范围见 `BLECryptoTests`

上游基础：[BleKeyProtocol.kt](https://github.com/zhihanhe/leapauto/blob/106b633bc4dbd0c816dd70e3160c22135e0cec6a/app/src/main/java/com/leapauto/app/bluetooth/BleKeyProtocol.kt)、[BleSm2Crypto.kt](https://github.com/zhihanhe/leapauto/blob/106b633bc4dbd0c816dd70e3160c22135e0cec6a/app/src/main/java/com/leapauto/app/bluetooth/BleSm2Crypto.kt)。测试使用公开合成夹具，不使用真实私钥或车辆凭据。

## 完整 SM2 的缺口

`BLESM2Provider` 明确定义 sm2p256v1 曲线运算、模阶标量运算与严格 X.509 解析边界。默认实现 `BLEUnavailableSM2Provider` 始终失败。Apple P-256 不能替代 SM2；纯编码/坐标范围检查也不能证明曲线成员关系或证书可信。

离线SM3/SM4参考代码不是经审计的生产密码库。SM4使用秘密相关表索引，未提供恒定时间保证；Swift值语义也不保证中间副本安全擦除。向量一致性不等于安全审计。本轮没有下载/绑定外部密码库，没有生成或保存真实密钥。

## CoreBluetooth 适配与后续条件

适配仅定位明确 UUID 的候选设备，不用 MAC、设备名或 RSSI 信任车辆。服务/特征匹配、通知启用或写入成功都不表示已经通过钥匙认证。CoreBluetooth 标识为系统 UUID，而非 Android MAC。[Apple 标识说明](https://developer.apple.com/documentation/corebluetooth/cbpeer/identifier)

未来启用前须完成：

1. 合规且经审计的原生 SM2/SM3/SM4 后端及独立互操作向量
2. 证书获取/到期/撤销和账号-VIN-设备绑定，完整会话加解密与帧生成
3. Xcode类型检查、蓝牙权限、严格GATT回调/断连与设备场景验收
4. 明确用户操作、车辆状态安全门禁、本机认证和操作结果回执
5. 车辆所有者在安全场地明确授权后的逐项测试

当前未启用后台恢复或无感靠近动作。iOS后台BLE是系统管理的事件唤醒，不保证Android前台Service式常驻；不能承诺靠近必解锁或离车必锁。[Apple 后台说明](https://developer.apple.com/library/archive/documentation/NetworkingInternetWeb/Conceptual/CoreBluetooth_concepts/CoreBluetoothBackgroundProcessingForIOSApps/PerformingTasksWhileYourAppIsInTheBackground.html)
