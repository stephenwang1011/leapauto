# 蓝牙钥匙证书接口静态契约

复核日期：2026-09-14。

本文件记录 `D:/young/work/hackapp/base.apk` 的静态证据和 LeapAuto 对应实现。
APK SHA-256 为 `43A0EBE3442E4E7DD6C3C8C9C9B9C838C1AE6E8433C842509EF913996BF8D113`，来自同目录 `BLUETOOTH_ANALYSIS.md` 的分析记录。本轮没有重新解包或连接服务端、手机蓝牙、车辆，不能将以下静态恢复结果表述为服务端或实车验证通过。

## 请求

```http
POST {appCenter}/carownerservice/v3/api/bluetoothkey/combine/syncBluetoothKeys
Content-Type: application/x-www-form-urlencoded
APPPlatform: Android
APPVersion: <legacy app version>
APPImei: <session deviceId>
C-VERSIONS: APP
XFX-CDN-VRS: IPv4
XFX-CDN-CROSS-NODE: <legacy app token>
```

`appCenter` 来自所选车辆 `getCarRoute` 的响应，不能替换为 `appRegion`，也不固定为某个网关。
表单 body 字段为 `vin`、`timespan`、`nonce`、`deviceID`、`signStr`，不存在业务 JSON body，也不是 URL query。

签名步骤：

1. 取当前毫秒时间戳为 `timespan`；`nonce` 为随机整数，原范围为 `[10000, 10009999]`。
2. 合并 `timespan`、`nonce`、`deviceID`、旧 token 和业务参数 `vin`。
3. 按参数名升序排列，直接连接各参数的值，不带参数名、分隔符或固定盐。
4. 对 UTF-8 字符串计算 MD5，将结果编码为小写十六进制，取 `substring(8, 24)` 为 `signStr`。
5. 从发送表单删除 `token`，加入 `signStr`；旧 token 保留于 `XFX-CDN-CROSS-NODE` header。

目标项目 `oldSignedParams()`、`Crypto.md5Short()` 与上述签名一致，因此没有新增签名算法或更改其他接口。
蓝牙证书请求的 `APPVersion` 使用 `session.appVersion`（当前为 `1.22.96`），
与原 APK `fb1.x()` 读取会话 `appVersion` 的字段语义一致，不能填入 `subVersion`。
此次仅覆盖蓝牙证书请求 header，其他接口保留现有行为。`User-Agent=okhttp/4.9.3` 保持原值。
原 APK 的默认 appVersion 为 `1.22.87`，但可由导入会话覆盖，不能把静态默认值认定为手机实际值。

## 响应与模型

主要证书位置为 `data.bluetoothKey`，兼容顶层 `bluetoothKey` 与直接证书对象。

| 字段 | 目标模型 | 校验与用途 |
| --- | --- | --- |
| `ecdhPublicKey` | `String` | 必须是非空字符串；具体公钥编码和曲线由 BLE 会话协议层校验 |
| `keyType` | `Int` | 仅接受 0 或 1，缺省为 0；会话层分别使用 P-256/AES 与 SM2/SM3/SM4，具体契约见 `BLUETOOTH_PROTOCOL.md` |
| `passwordCard` | `String` | 至少 80 字节的 ASCII 字符串，参与会话密钥派生 |
| `plainText` | `String` | 非空认证字段，格式由会话协议层继续校验 |
| `signResult` | `String` | 非空认证签名字段，格式由会话协议层继续校验 |
| `vin` | `String` | 非空时必须匹配请求车辆；源响应省略/空串时绑定为本次请求 VIN |

原 APK 的 `s31.n()` 接受 `success=true`，或者缺少业务码，或者业务码为 `0`/`200`。
目标解析器更保守：显式 `success=false` 或首个 `code`/`result`/`status` 非 `0`/`200` 时拒绝，即便同时附带证书数据。缺少业务码仍需全部证书字段校验通过。
目标解析器只接受以上三种确定的对象路径；没有照搬原 APK 的任意递归字段搜索。

## 目标实现与数据边界

- `bluetooth/BleCertificate.kt` 提供 `BleKeyCertificate`、`fromResponse()`、`fromJson()` 和 `toJson()`。`toString()` 只显示类型，不显示 VIN 或钥匙材料。
- `LeapmotorApi.fetchBluetoothKeyCertificate()` 使用当前账号和所选 VIN，要求旧 token、账号 ID、设备 ID 和车辆均存在；云请求只由明确的蓝牙设置操作触发。
- 专用 OkHttp 客户端不安装 Chucker 或其他拦截器、不使用缓存、拒绝重定向。这样用户开启网络诊断也不会将证书响应存入诊断库。
- 目标只接受 HTTPS 的 `*.leapmotor.cn`/`*.leapmotor.com` 来源路由，拒绝含账号密码、query、fragment 或额外路径的路由值。
- HTTP、JSON、网络和业务失败不回显原始响应、服务端任意消息或底层异常内容。
- `toJson()` 的输出含秘密，只能交给按账号和车辆隔离的加密存储；不能写入日志、界面或普通偏好设置。
- 单元测试全部使用合成字段，不使用真实账号、VIN、token 或证书。覆盖表单和签名、业务失败、错 VIN、损坏字段、脱敏和无诊断拦截器边界。
- 证书请求使用独立会话快照；令牌续期可能更新该快照的设备 ID。提交时对原账号、VIN、登录代次、设备 ID 和令牌做并发校验，成功后将刷新凭据与证书原子写入加密存储，并更新内存中的认证身份。
- 缓存额外记录加密的 `requestDeviceId`，它不属于上传字段或证书协议字段。缺少该绑定的旧缓存、或者设备 ID 已变化时，需要重新同步；该元数据不参与证书指纹或认证帧。
- 蓝牙会话身份包含设备 ID，变化时释放旧连接；密钥派生结束和认证报文发送前再次检查当前身份。不能混用同步时与认证时的设备身份。

## 证据位置

下列路径均相对于 `D:/young/work/hackapp`：

| 证据 | 文件位置 |
| --- | --- |
| POST 路由与 VIN 表单实参 | `analysis/payload_src/sources/defpackage/ib.java:188-198` |
| 时间戳、nonce、deviceID、token、签名去 token | `analysis/payload_src/sources/defpackage/fb1.java:184-211`；`analysis/smali1/fb1.smali:1727` 方法 `B` |
| MD5 中间 16 字符，排序后拼值 | `analysis/payload_src/sources/defpackage/hb1.java:101-117`；`analysis/smali1/hb1.smali` 方法 `e`、`f` |
| 公共 headers | `analysis/payload_src/sources/defpackage/fb1.java:2080-2088`；`analysis/smali1/fb1.smali:24888` 方法 `x` |
| 混淆字符串解码 | `analysis/payload_src/sources/defpackage/mb1.java`；`analysis/smali1/lb1.smali` switch 7/8/9 的整型数组按循环密钥异或，分别得到 `carownerservice`、`XFX-CDN-VRS`、`XFX-CDN-CROSS-NODE` |
| 表单类型参数传递和编码 | `analysis/smali1/fb1.smali` 方法 `L`；`analysis/payload_src/sources/defpackage/yw0.java:64-94` |
| 响应 envelope、字段与 VIN 校验 | `analysis/smali1/fb1.smali:12469` 方法 `h`，尤其 `data.bluetoothKey`、`z91` 构造及 VIN 比较分支 |
| 模型字段与 passwordCard 校验 | `analysis/payload_src/sources/defpackage/z91.java`；`analysis/smali1/z91.smali` 方法 `b` |
| 原业务成功判定 | `analysis/payload_src/sources/defpackage/s31.java:304-308` |

尚未确认：实际车辆路由、服务端当前签名/headers 接受情况、账号是否有蓝牙钥匙授权、实车下发证书内容、配对及动作回执。接口模型或离线测试通过不能替代这些验收。
