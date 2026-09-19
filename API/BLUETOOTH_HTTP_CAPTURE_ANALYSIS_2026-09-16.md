# 官方蓝牙 HTTP 抓包对照分析

分析日期：2026-09-16。

输入目录：`C:/Users/Administrator/Desktop/lbe-蓝牙相关`。
对照来源：当前 LeapAuto 源码，以及 `D:/young/work/hackapp/base.apk` 已恢复的 Java/DEX/smali。
本文仅记录脱敏结论；请求中的身份、令牌、车辆地址、VIN、证书和密钥材料不复制到文档。

## 结论与证据边界

这批文件有实际价值。42 个文件组成 21 组 HTTP 请求/响应；其中两份二进制 `pointData` 请求还能解析出共 40 条官方蓝牙事件记录。

目前最值得核对的是连接目标：官方日志里的地址与云端车辆配置一致，但与此前 LeapAuto 失败截图中已知的地址后缀不同。其次是官方实际标定参数与当前 App 固定参数不同。

这些差异尚不能单独解释 `AAAC / result=9`。本批资料没有识别到完整的车辆 GATT 认证收发记录，也没有 `syncBluetoothKeys` HTTP 请求，不能确认成功连接使用完整认证 `AAAE` 还是快速重连 `AAEE`。云端返回成功不是车辆认证成功的证据。

本轮未修改 App 实现，未发出云端请求或车辆命令，未运行扫描、连接、打包或安装。

## 1. 请求目录

编号对应原始文件名中的 `[编号]`，无需打开原始正文即可定位来源。

| 请求编号 | 方法与路径 | 数量 | 已观察结果 | 用途 |
| --- | --- | ---: | --- | --- |
| 1104、1116、1157、1160 | POST `/app/app-global-service/v3/api/commoninfo/transparent/conf/upload` | 4 | HTTP 200，业务 code 0 | 上传蓝牙开关配置 |
| 1129、1132、1136 | POST `/app/app-global-service/v3/api/bluetoothkey/uploadAutonomyCalibrateParams` | 3 | HTTP 200，业务 code 0 | 新增或删除自主标定参数 |
| 1106、1161 | POST `/file/1.0/vehicle/pointData` | 2 | HTTP 200，业务 code 20000 | 上传官方运行事件日志 |
| 1107、1125、1152 | POST `/app/app-signal-service/signal/info/query` | 3 | HTTP 200，业务 code 0、result 0 | 车辆状态查询 |
| 1110、1122、1148 | GET `/carownerservice/v3/api/vehicleinfo/commonConfig` | 3 | HTTP 200，业务 code 0、result 0 | 车辆云端配置，包括地址和控制器版本线索 |
| 1105、1123、1134、1141、1151、1163 | POST `/app-community/appuseroperate/appstatus` | 6 | HTTP 200，业务 code 200 | App 状态上报 |

网关请求记录的 App 版本为 `1.22.96`，子版本为 `3.21.3-2`。上述云端接口与手机直接向车辆写入的蓝牙报文属于不同通道。

## 2. 连接目标地址差异：优先核对

三份 `commonConfig` 响应的 `data.config["4"]` 均包含 `mac`、`version`、`updateTime`。

- 三份地址经规范化后完全一致。
- `version` 均为字符串 `"2.0"`。
- 两份 `pointData` 中，准备连接、二级页面和 `addCarLink-XiaoMi` 事件内共 8 个地址记录，均与上述配置地址一致。
- 在内存中与用户此前失败截图可见的地址后缀比较，结果为不一致。这里不记录原地址或后缀。

这说明存在可核实的目标地址差异；但抓包与失败截图不是同一次操作，仍需考虑地址变化、同车不同蓝牙模块及不同设备等可能性，不能仅凭后缀差异宣称连接了别人的车辆。

下一次现场核对应优先确认：当前选中车辆的云端地址、官方成功连接的实际 GATT 目标、LeapAuto 点击连接的目标，三者是否对应。云端地址尚未完成实车稳定性验证前，不应直接作为唯一硬过滤条件。

### 当前 App 首次连接没有车辆与地址的对应校验

源码确认的路径如下：

- `BleScanDeviceCache.kt:23`、`:47`、`:54`：合法地址、可连接、本轮已观察到 FFFE 服务的设备即可成为候选。
- `BleScanDeviceCache.kt:63`：按信号强度排序；名称不用于确认车辆身份。
- `BluetoothKeyDialog.kt:364`：列表展示候选，由用户点击对应设备进行连接，并非自动选择信号最强者。
- `MainActivity.kt:606`：证书绑定当前账号和选中 VIN，但未将候选地址与这个 VIN 的云端目标地址比较。
- `BluetoothKeyController.kt:126`、`:274`：检查地址来自本次扫描候选后，向该地址建立 GATT 连接。

因此“发现可连接车辆”当前只代表识别到协议服务，不代表已核实属于选中车辆。这是代码确认的功能缺口；它与本次地址差异一起，构成应先排除连接目标不对应的理由。认证成功后的本地可信绑定及后台按绑定地址重连，是另一条已有约束的路径。

`base.apk` 首次配对也仅按 FFFE 服务筛选、由用户选择地址，认证成功后才保存 `aj` 本地绑定；它同样没有云端 `config4.mac` 校验。因此这里是值得完善的目标核对能力，不能仅凭这项缺失认定移植错误。来源为 `xj.java:1380`、`:1547`、`:1640` 和 `i2.java:109`。

### 控制器版本不能直接替代协议 minor

官方准备连接日志也记录 `controllerVersion="2.0"`，与配置版本一致。但本批资料没有给出它与 `000001xx` 广播 UUID、认证 minor 的转换关系。

当前 App 使用已观察到的广播 minor，其次使用同账号/车辆/证书/设备的可信绑定记录，否则回退 8。不能直接把 `"2.0"` 转换成 minor 2、8 或 9。

`base.apk` 确实请求 `commonConfig`，但已恢复调用链为 `fb1.k -> fb1.J -> c13.d -> rs(ChargePlan)`，只消费配置 `"3"`，兼容键 `"_$3"`。没有发现其蓝牙 minor 选择读取配置 `"4"` 的路径。因此不能认定 LeapAuto 缺少该云端接口就是认证失败原因。

## 3. 官方实际标定与当前固定值不同

标定上传为表单，请求字段有 `anchorType`、`deviceID`、`model`、`nonce`、`operate`、`params`、`signStr`、`timespan`、`vin`。

| 请求 | anchorType | operate | params |
| --- | ---: | --- | --- |
| 1129 | 0 | add | `69;1.00;04;21` |
| 1132 | 0 | add | `69;1.00;07;21` |
| 1136 | 0 | delete | 空字符串 |

五条官方“准备连接”事件均记录四项数值 `[69, 1, 4, 21]`。当前 `BlePassiveConfiguration` 的认证文本固定为 `56;2.00;08;16;...`，配置命令二进制则固定为 `[56, 200, 0, 8, 16] + flags`。

`base.apk` 的 `h91` 默认值同样是 `56 / 200 / 8 / 16`，其中第二项在 `j91.f` 中按百分之一转换为文本 `2.00`。这说明当前实现复用了原始默认参数，但没有覆盖此次官方运行中使用的车辆/设备标定。

这项差异对靠近、远离感应功能有直接研究价值，并且标定字段参与完整认证文本，值得做同条件对照。不过目前没有证据说明不同标定值必然导致 result 9。

后续应补查标定读取来源、参数约束、恢复默认含义及生效确认，再支持按车辆保存并应用。不要直接把这辆车的一组值写成全局默认；`04`、`07`、`21` 也不能未经公式和实测验证直接标注为米。

## 4. 四个蓝牙开关的云端保存结构

配置上传表单包含 `conf`、`deviceID`、`nonce`、`signStr`、`timespan`、`vin`。`conf` 是 JSON 字符串。

| 请求 | bleKeySwitch | bleKeyUnlock | bleKeyLock | bleKeyBtn |
| --- | --- | --- | --- | --- |
| 1104 | true | true | true | true |
| 1116 | true | false | false | true |
| 1157 | false | false | false | true |
| 1160 | true | false | false | true |

结合事件日志，这四项对应钥匙总开关、自动解锁、自动上锁及微动开关偏好。总开关关闭时，云端仍保存 `bleKeyBtn=true`，说明原始偏好和当前有效开关应分开理解。这些是用户操作后的快照，不是功能默认值。

七次配置/标定请求中，表单 `deviceID` 与请求头 `deviceid` 一致，表单 VIN 与请求头车辆身份一致。这只能证明本批官方请求内部身份一致，无法替代与 LeapAuto 当前会话身份的对照。

当前 App 的用户配置在本地保存，通过认证后发送 GATT command 3；只有匹配的解密响应才确认车辆已应用。它尚未实现这里的两个云端上传接口。后续若补云端同步，应分别表示用户期望配置、云端保存结果和车辆确认结果，保留用户要求的默认关闭。

## 5. pointData 不只是普通埋点：包含证书结构和连接参数

两份请求正文分别为 5497、11345 字节，与 `Content-Length` 相符。它们可完整解析为 Protobuf wire-format：顶层重复字段 1 分别包含 11、29 个记录，各记录包含字段 1 至 13，字段 9 中是 JSON 事件内容。

已识别事件包括准备连接、蓝牙钥匙开/关、解闭锁方式、用户调整解锁距离，以及 `carLinkState-XiaoMi`、`addCarLink-XiaoMi`、`deleteCarLink-XiaoMi`。

两份日志各有一条二级页面记录嵌入证书对象，安全结构检查结果一致：

| 项目 | 观察结果 |
| --- | --- |
| keyType | 数字 1，与本项目 SM2 分支相符 |
| ecdhPublicKey | 字符串，PEM X.509 证书容器，可解析；SPKI 算法 OID 为 EC，曲线 OID 为 SM2 |
| passwordCard | 字符串，80 个字符 |
| plainText | 字符串，按分号分为 6 段，长度为 `[0, 0, 0, 3, 13, 1]` |
| signResult | 合法 Base64 字符串，解码为 64 字节 |

前三段原值为空，是本批官方证书的已观察事实。当前 App 和 `base.apk` 在完整认证时把零基下标 1、2 替换为当前账号与设备 ID。因此，当前诊断的原字段身份匹配掩码为 0，也可能只是证书使用空占位，不能独立判为身份错误。

证书“结构可解析”不等于当前会话证书与官方证书一致，也不等于车辆验签成功。本批没有证书同步 HTTP 请求，无法据此检查下发时的认证头、来源路由和完整请求响应关系。

没有识别到可还原的 `AAAE`/`AAEE` 认证帧、完整 GATT 写入/通知序列或车辆认证成功确认。上述事件名也不能替代 GATT 证据。

## 6. 当前故障与下一步优先级

此前 LeapAuto 日志已完成扫描、GATT 连接、服务发现、通知订阅、MTU 协商和两次写入回调，在发送 311 字节完整认证报文后收到车辆 result 9。因此排查重点仍是目标匹配和认证阶段。

1. **核对目标。** 优先用同一时段官方成功连接和 LeapAuto 的实际目标作对照，确认本次云端/官方地址与失败截图地址不同的原因。相同设备名不能证明属于当前选中车辆。
2. **补齐成功链路。** 在同手机、同车辆、同目标条件下，获取成功连接的广播 UUID 与 GATT 写入/通知，确认使用 `AAAE` 还是已有凭据的 `AAEE`。本批 HTTP 日志不能回答这一点。
3. **同条件比较认证输入。** 对照证书来源、当前账号/设备、协议 minor、标定参数及实际配置 flags，先输出结构差异，避免记录密钥原文。
4. **完善感应设置。** 确认标定读取与恢复默认协议后，再接入标定和云端偏好同步；云端接受与车辆生效分别确认。

不建议以补发配置上传请求、把 minor 强改成某个值，或把标定常量换成抓包值，作为已确认的 result 9 修复方案。

## 7. 可复现解析与源码索引

只读脱敏解析脚本：`D:/young/work/hackapp/analysis/ble_http_capture/inspect-point-data.cjs`。
脱敏摘要：`D:/young/work/hackapp/analysis/ble_http_capture/point-data-safe-summary.json`。

```powershell
node D:/young/work/hackapp/analysis/ble_http_capture/inspect-point-data.cjs --summary
```

脚本仅从原目录读取文件，输出白名单事件、参数、字段类型/长度和地址相等判断，不输出原始身份、地址、证书或密钥。已实际运行并验证两份正文长度、40 条结构化事件以及三份云端配置的比对。

| 来源 | 相关实现 |
| --- | --- |
| `app/src/main/java/com/leapauto/app/bluetooth/BlePassiveConfiguration.kt` | 固定标定字段和有效开关编码 |
| `app/src/main/java/com/leapauto/app/bluetooth/BleKeyProtocol.kt` | 完整认证构造、证书字段替换、配置命令 |
| `app/src/main/java/com/leapauto/app/bluetooth/BleProtocolSelection.kt` | minor 来源选择 |
| `app/src/main/java/com/leapauto/app/bluetooth/BleKeyRuntime.kt` | 本地绑定、认证后配置和车辆确认 |
| `app/src/main/java/com/leapauto/app/LeapmotorApi.kt` | 当前证书同步请求 |
| `D:/young/work/hackapp/analysis/smali1/fb1.smali` | `k` 请求 commonConfig，`J` 调用链 |
| `D:/young/work/hackapp/analysis/smali1/c13.smali` | 配置 3 解析为充电计划 |
| `D:/young/work/hackapp/analysis/payload_src/sources/defpackage/xj.java` | 原始扫描、连接、minor 与完整/快速认证选择 |

验证范围仅为文件解析和源码对照；未以真实车辆操作验证推断，未执行无关的 App 编译或单元测试。
