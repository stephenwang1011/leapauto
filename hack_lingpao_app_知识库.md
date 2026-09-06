# hack_lingpao_app 零跑中国 App 逆向知识库

> 整理日期：2026-08-14
> 来源仓库：[cqrg/hack_lingpao_app](https://github.com/cqrg/hack_lingpao_app)
> 用途：为 LeapAuto 的协议分析、接口排查和兼容性判断提供本地参考。
> 安全边界：不记录账号、Token、VIN、操作密码、手机号、原始车辆响应或其他个人数据。

## 1. 项目概况

目标 App：

- 应用：零跑中国 App
- 包名：com.dahua.leapmotor
- 样本版本：v1.22.93
- 用途：提取私有 API，供 Home Assistant 等第三方集成参考

公开仓库的主要文件：

| 文件 | 内容 |
| --- | --- |
| README.md | 项目现状、完成项、限制和使用方式 |
| API_REFERENCE.md | 域名、请求头、签名、登录、车控和业务接口 |
| APP_PROTOCOL.md | 登录、认证、签名、加密和响应格式 |
| REVERSE_NOTES.md | 脱壳、字符串解密、模拟器检测和逆向过程 |
| lingpao_client.py | Python 协议原型客户端 |

项目现状：

- 域名、接口、参数、签名算法和 RSA 公钥已静态提取。
- 发送验证码和登录接口有黑盒验证记录。
- 登录可能触发 GT4 极验风控。
- 模拟器可能被模拟器检测和 360 加固保护拦截。
- 车控接口需要有效 accessToken 和 signKey 才能进一步验证。
- 接口清单不是零跑官方公开 API 文档。

## 2. 服务域名

| 服务 | 地址 | 用途 |
| --- | --- | --- |
| 主车控网关 | https://app-gw-global-master.leapmotor.com | 车辆列表、车控 v3、车况 |
| 备用车控网关 | https://app-gw-global-slave.leapmotor.com | 主网关备用域 |
| 用户/登录 | https://appuser.leapmotor.cn | 短信登录、Token、用户会话 |
| 社区/业务 | https://apptec.leapmotor.cn | 发送验证码、社区、H5 |
| 实名认证 | https://lpau.leapmotor.cn | 实名认证 |
| MQTT | https://mqtt-center.leapmotor.cn | 实时车控相关服务 |
| 消息中心 | https://msgcenter.leapmotor.cn | 通知和消息 |
| AI | https://app-ai.leapmotor.cn | AI 助手 |
| AI API | https://api.leapmotor.com | AI 助手 RN 接口 |
| 商城 | https://store-center.leapmotor.cn | 商城业务 |

新版车控基础路径：

    https://app-gw-global-master.leapmotor.com/carownerservice/v3/api/

备用路径：

    https://app-gw-global-slave.leapmotor.com/carownerservice/v3/api/

## 3. 请求头和签名

登录 SDK 请求头：

    XFX-CDN-VRS: v4
    APPPlatform: Android
    APPVersion: 1.22.93
    APPImei: <PhoneUtils.getPhoneID()>
    C-VERSIONS: APP
    XFX-CDN-CROSS-NODE: <accessToken>

新车控网关请求头：

    source: leapmotor
    channel: 1
    acceptLanguage: zh-CN
    x-region: CN
    x-api-signature-version: 2.0
    digest: <请求摘要，通常为空>
    version: <App版本>
    deviceType: Android
    nonce: <随机数>
    timestamp: <毫秒时间戳>
    deviceId: <手机设备ID>
    userId: <accountId>
    carvin: <VIN>
    cartype: <车型>
    x-subversion: <子版本>
    token: <accessToken>
    sign: <签名>

签名流程：

1. 合并请求头参数和业务参数。
2. 按 key 排序。
3. 按排序后的顺序只拼接 value。
4. 登录接口使用 SHA256。
5. 已登录接口使用 HMAC-SHA256(signKey)。

signKey 派生：

    r1 = Base64URLDecode(accessToken JWT 的第三段)
    r2 = Base64Decode(signParam.r2)
    r3 = Base64Decode(signParam.r3)
    signKey[i] = r1[i] XOR r2[i] XOR r3[i]

长度取三个字节数组的最小长度。

## 4. API 请求参数明细

本节把目前能从 `hack_lingpao_app` 和当前 LeapAuto 代码中确认的参数分为三类：已验证参数、代码中实际使用的参数、仅有接口路径但尚未完整验证的参数。尖括号中的内容是运行时值，不能写入文档、日志或提交记录。

### 4.1 参数分类

| 参数类别 | 典型参数 | 说明 |
| --- | --- | --- |
| 登录参数 | `phoneNoCiphertext`、`smsCode`、`deviceID`、`smDeviceId` | 登录接口的表单参数 |
| 新网关请求头 | `deviceId`、`userId`、`carvin`、`cartype`、`token`、`sign` | 放在 HTTP Header，不是 JSON body |
| 新网关业务参数 | `vin`、接口专用 JSON 字段 | 参与签名，同时按接口要求放在 query 或 JSON body |
| 旧控车参数 | `cmdid`、`state`、`carvin`、`oppwd` | 当前 LeapAuto 实际使用的旧控车链路 |
| 旧链路签名参数 | `timespan`、`nonce`、`deviceID`、`signStr` | `token` 参与签名但会从最终表单参数中移除 |
| MQTT 参数 | `vin`、`cmdId`、`payload` | 只确认调用线索，Topic 和完整连接参数尚未确认 |

### 4.2 新网关 Header 参数

| Header | 是否必需 | 来源/含义 |
| --- | --- | --- |
| `source` | 是 | 固定为 `leapmotor` |
| `channel` | 是 | 固定为 `1` |
| `acceptLanguage` | 是 | 中国区为 `zh-CN` |
| `x-region` | 是 | 中国区为 `CN` |
| `x-api-signature-version` | 是 | 当前记录为 `2.0` |
| `digest` | 需要随版本确认 | 当前代码通常为空 |
| `version` | 是 | App 版本，例如 `1.22.87` 或样本版本 `1.22.93` |
| `deviceType` | 是 | App 样本记录为 `Android`；当前 LeapAuto 使用 `android` |
| `nonce` | 是 | 每次请求生成的随机数 |
| `timestamp` | 是 | 当前毫秒时间戳 |
| `deviceId` | 是 | 手机/设备 ID |
| `userId` | 登录后需要 | 账号 ID |
| `carvin` | 车辆接口通常需要 | 当前车辆 VIN |
| `cartype` | 车辆接口通常需要 | 当前车型编码 |
| `x-subversion` | 需要随版本确认 | App 子版本 |
| `token` | 登录后需要 | `accessToken` |
| `sign` | 是 | SHA256 或 HMAC-SHA256 计算结果 |

注意：Header 的实际大小写、`deviceType` 的大小写、版本号和 `digest` 规则都可能受 App 版本影响，不能只复制一份旧抓包结果长期使用。

### 4.3 发送验证码参数

请求：

    GET https://apptec.leapmotor.cn/app-community/applogin/sendmessagecode

Query 参数：

| 参数 | 类型 | 说明 |
| --- | --- | --- |
| `phoneNo` | string | 明文手机号；敏感数据，不得记录 |
| `smDeviceId` | string | 数美 SDK 设备 ID |

### 4.4 短信登录参数

请求：

    POST https://appuser.leapmotor.cn/app-user/applogin/check_login_with_phone

Content-Type：`application/x-www-form-urlencoded`

Form 参数：

| 参数 | 类型 | 说明 |
| --- | --- | --- |
| `phoneNoCiphertext` | string | RSA/ECB/PKCS1Padding 加密手机号，Base64URLSafe |
| `smsCode` | string | 短信验证码 |
| `deviceID` | string | 手机设备 ID |
| `smDeviceId` | string | 数美设备 ID |
| `os` | string | `android` |
| `pageUrl` | string | 当前代码为空字符串 |
| `captchaOutput` | string | 仅触发 GT4 风控并完成验证后追加 |
| `genTime` | string | 仅 GT4 验证后追加 |
| `lotNumber` | string | 仅 GT4 验证后追加 |
| `passToken` | string | 仅 GT4 验证后追加 |
| `requestId` | string | 风控响应返回，重试时使用 |
| `phone` | string | GT4 重试流程记录的附加字段 |

### 4.5 车辆列表和路由参数

公开仓库记录的新版车辆列表：

    GET /carownerservice/v1/vehicle/list

通常没有业务 query/body 参数，但需要新版登录 Header。

当前 LeapAuto 使用的车辆列表：

    GET /app/app-global-service/v1/vehicle/list

当前 LeapAuto 的车辆路由：

    GET /app/app-global-service/v1/vehicle/getCarRoute?vin=<VIN>

Query 参数：

| 参数 | 类型 | 说明 |
| --- | --- | --- |
| `vin` | string | 当前车辆 VIN |

路由响应需要提取 `appRegion` 和 `appCenter`，之后车况和旧控车请求会使用 `appRegion`。

### 4.6 车况参数

当前 LeapAuto 车况请求：

    POST {appRegion}/app/app-signal-service/signal/info/query

JSON body：

    {"vin":"<VIN>"}

公开仓库记录的新车况入口：

    POST /carownerservice/v3/api/chassis/query

该仓库没有给出完整、经过实车验证的 `chassis/query` JSON body，因此目前只确认路径和新版签名要求，不确认额外字段。

### 4.7 当前 LeapAuto 旧控车参数

请求：

    POST {appRegion}/app/app-control-service/v3/api/appremotectl

Form 参数：

| 参数 | 类型 | 说明 |
| --- | --- | --- |
| `timespan` | string | 当前毫秒时间戳，参与旧签名 |
| `nonce` | string | 随机字符串，参与旧签名 |
| `deviceID` | string | 当前设备 ID，参与旧签名 |
| `cmdid` | string | 命令 ID，例如解锁为 `110` |
| `state` | string | 命令 JSON 字符串，例如 `{"value":"unlock"}` |
| `carvin` | string | 当前车辆 VIN |
| `oppwd` | string | 操作密码使用旧 Token 派生的 AES-128-CBC 加密结果 |
| `signStr` | string | 旧签名，Token 参与计算但不作为最终表单字段发送 |

旧请求 Header：

    APPPlatform
    APPVersion
    APPImei
    C-VERSIONS
    XFX-CDN-VRS
    XFX-CDN-CROSS-NODE=<旧Token>

解锁业务参数：

    cmdid=110
    state={"value":"unlock"}

### 4.8 当前 LeapAuto 控车结果查询参数

请求：

    GET {appRegion}/app/app-control-service/v3/api/appremotectl/query

业务参数：

| 参数 | 类型 | 说明 |
| --- | --- | --- |
| `msgID` | string | 初始控车响应返回的消息 ID |
| `timespan` | string | 当前毫秒时间戳 |
| `nonce` | string | 随机字符串 |
| `deviceID` | string | 当前设备 ID |
| `signStr` | string | 旧链路签名 |

### 4.9 新版车控参数的已知和未知部分

公开仓库确认的新版路径：

    POST /carownerservice/v3/api/appremotectl/
    POST /carownerservice/v3/api/appremotectl/query

目前能确认：

- 使用新版网关 Header。
- 登录后使用 `accessToken`。
- 使用 `signKey` 计算 HMAC-SHA256 签名。
- 车控业务与锁车、解锁、空调等能力相关。

目前不能确认：

- 新版解锁 JSON body 的完整字段。
- 新版 `cmdid`、`state`、VIN 字段的具体命名和嵌套层级。
- 新版是否直接提交操作密码，还是先调用 `verifyoperatepwdnew`。
- 新版控车查询 body 的完整结构。
- 新版响应中的消息 ID 和状态字段的全部取值。

因此不能把旧链路的 `cmdid/state/carvin/oppwd` 直接复制到新版接口中。

### 4.10 MQTT 参数

仓库只明确记录了调用形式：

    MqttUtils.sendRemoteControl(vin, cmdId, payload)

已知业务参数：

| 参数 | 说明 |
| --- | --- |
| `vin` | 车辆 VIN |
| `cmdId` | MQTT 车控命令 ID，例如仓库提到的 `171`、`111` |
| `payload` | 命令 JSON，具体结构按命令和车型变化 |

MQTT Broker、Topic、鉴权 Token、QoS 和完整 payload 尚未在公开仓库中形成完整可验证规范。

## 5. 登录和会话接口

### 5.1 发送短信验证码

    GET https://apptec.leapmotor.cn/app-community/applogin/sendmessagecode

参数：

    phoneNo=<明文手机号>
    smDeviceId=<数美设备ID>

smDeviceId 来自数美 SDK 的 SmAntiFraud.getDeviceId()。仓库记录该接口有黑盒验证结果，但当前项目不应在未授权情况下发送真实短信。

### 5.2 手机验证码登录

    POST https://appuser.leapmotor.cn/app-user/applogin/check_login_with_phone
    Content-Type: application/x-www-form-urlencoded

参数：

    phoneNoCiphertext=<RSA 加密手机号>
    smsCode=<验证码>
    deviceID=<手机设备ID>
    smDeviceId=<数美设备ID>
    os=android
    pageUrl=

手机号使用 RSA/ECB/PKCS1Padding 加密，1024-bit RSA 公钥，UTF-8，每块 117 字节，最终使用 Base64URLSafe。

成功响应字段：

    data.appLoginVO.accountId
    data.appLoginVO.accessToken
    data.appLoginVO.refreshToken
    data.appLoginVO.tokenExpireTime
    data.appLoginVO.signParam.r2
    data.appLoginVO.signParam.r3

触发风控时可能返回 data.risk_type 和 data.requestId，需要 GT4 验证后重试登录。

### 5.3 其他会话接口

    POST /app-user/applogin/check_one_login
    GET  /app-user/applogin/delegatedauth?deviceID=<id>&clientId=<id>
    GET  /app-user/applogin/tokenExist
    GET  /app-user/appuseroperate/logout

## 6. 车辆和车况接口

车辆列表：

    GET /carownerservice/v1/vehicle/list

车况和泊车：

    POST /carownerservice/v3/api/chassis/query
    POST /carownerservice/v3/api/vehicleinfo/parking/query

其他已提取接口：

    POST /carownerservice/v3/api/carpicture/key
    POST /carownerservice/v3/api/3d/key
    POST /carownerservice/v3/api/drivingrecord/*
    POST /carownerservice/v3/api/sharecar/*
    POST /carownerservice/v3/api/scene/query
    POST /carownerservice/v3/api/scene/addOrUpdate
    POST /carownerservice/v3/api/scene/delete
    POST /carownerservice/v3/api/schedule/list
    POST /carownerservice/v3/api/schedule/operate
    POST /carownerservice/v3/api/schedule/syncCode

以上多数只有静态提取记录，不能直接视为已在当前车型上验证可用。

## 7. 车控和能源接口

车控命令：

    POST /carownerservice/v3/api/appremotectl/

车控结果查询：

    POST /carownerservice/v3/api/appremotectl/query

预约查询：

    POST /carownerservice/v3/api/appremotectl/getappointment

操作密码：

    POST /carownerservice/v3/api/appoperate/verifyoperatepwdnew

健康充电：

    POST /carownerservice/v3/api/healthyCharging/control
    POST /carownerservice/v3/api/healthyCharging/queryPushState

OTA：

    POST /carownerservice/v3/api/fota/getCurrentVersion
    POST /carownerservice/v3/api/fota/resetStatus

仓库将 appremotectl/ 归类为锁车、解锁、空调等车控入口，但没有公开一套经过完整实车验证的全部命令 payload。

## 8. MQTT 实时车控

仓库记录 App 内部调用：

    MqttUtils.sendRemoteControl(vin, cmdId, payload)

相关服务：

    https://mqtt-center.leapmotor.cn

命令示例：

- 171：空调预约相关
- 111：车控响应相关

这些命令码不能直接当作普通 HTTP 控车命令使用。MQTT Token、Topic、连接参数和 payload 仍需有效会话及车型实测验证。

## 9. 逆向过程与工具

仓库记录的过程：

- 360 壳脱壳并调整 frida-server 端口。
- 使用 frida-dexdump 提取约 150 个 dex。
- 修复 dex 的 SHA-1 和 Adler-32 校验。
- 使用 androguard 和自定义脚本分析 dex。
- 从 StubApp.getString2() 解密运行时字符串和 URL。
- 从 HttpHeadString 等类中提取请求头和签名逻辑。
- 分析模拟器检测、Root/Xposed/Frida 检测和 360 反调试。

工具脚本：

    dex_scan.py
    fix_dex_checksum.py
    disasm_class.py
    find_ref_classes.py
    find_callers.py
    hook_strings.py
    hook_strings_key.py
    hook_http.py
    get_smdeviceid.py
    verify_rsa.py

## 10. 与当前 LeapAuto 的对应关系

| hack_lingpao_app | 当前 LeapAuto |
| --- | --- |
| appuser.leapmotor.cn | LeapmotorApi.APP_USER_HOST |
| app-gw-global-master.leapmotor.com | LeapmotorApi.GLOBAL_HOST |
| check_login_with_phone | LeapmotorApi.loginWithSms() |
| RSA 手机号加密 | Crypto.rsaEncryptPhone() |
| accessToken + r2/r3 派生 signKey | Crypto.deriveSignKey() |
| 新网关 HMAC-SHA256 | LeapmotorApi 新网关请求流程 |
| 车辆列表 | LeapmotorApi.listVehicles() |
| 车况查询 | LeapmotorApi.getVehicleState() |

关键差异：

当前 LeapAuto 的控车仍使用旧控车链路：

    {appRegion}/app/app-control-service/v3/api/appremotectl

使用旧 Token、oldSignedParams()、AES 加密的 oppwd，请求参数为 cmdid、state、carvin、oppwd。

本仓库记录的新 App 车控链路为：

    app-gw-global-master.leapmotor.com/carownerservice/v3/api/appremotectl/

使用 accessToken、signKey、HMAC-SHA256 和新版网关请求头。

两者可能对应不同 App 版本、接口迁移阶段或车控子系统。不能仅凭路径差异直接替换当前实现，必须用脱敏响应或授权测试账号验证。

## 11. 对当前解锁失败问题的参考

当前 LeapAuto 解锁命令：

    cmdid = 110
    state = {"value":"unlock"}

结合本仓库，排查时还应关注：

1. 当前账号是否被新版官方 App 或其它客户端刷新/踢出会话。
2. 当前 appRegion 是否仍指向可用的控车服务。
3. 当前车型和 App 版本是否仍接受旧的 app-control-service 路径。
4. 新版接口是否要求 carownerservice 路径和新网关签名。
5. 解锁是否已经迁移到 MQTT 或需要新的操作密码校验流程。
6. 服务端返回的 result、code、msg、message 具体是什么，而不是只看统一失败提示。

## 12. 可信度和使用边界

| 内容 | 可信度 | 说明 |
| --- | --- | --- |
| 域名和部分接口路径 | 较高 | 来自 APK 静态提取，仍可能随版本变化 |
| 登录参数和 RSA 加密 | 较高 | 仓库有黑盒验证说明 |
| 新网关签名算法 | 较高 | 客户端实现和逆向笔记互相印证 |
| 车控接口完整可用性 | 中等偏低 | 仓库明确表示需要有效 Token/签名验证 |
| 全部命令 payload | 偏低 | 公开文档未完整列出并逐项实车验证 |
| MQTT 实时控制 | 偏低到中等 | 有调用线索，但会话、Topic 和车型行为未完整验证 |
| 与当前中国版 C16 LeapAuto 的直接兼容性 | 未确认 | 需要版本、车型和真实脱敏响应对照 |

网络、短信、车辆控制、Token、VIN、操作密码和原始响应均属于敏感数据或敏感操作。不得在没有用户明确授权的情况下发送真实短信、登录请求或车辆控制命令。

## 13. 原始参考

- [项目主页](https://github.com/cqrg/hack_lingpao_app)
- [API_REFERENCE.md](https://github.com/cqrg/hack_lingpao_app/blob/main/API_REFERENCE.md)
- [APP_PROTOCOL.md](https://github.com/cqrg/hack_lingpao_app/blob/main/APP_PROTOCOL.md)
- [REVERSE_NOTES.md](https://github.com/cqrg/hack_lingpao_app/blob/main/REVERSE_NOTES.md)
- [lingpao_client.py](https://github.com/cqrg/hack_lingpao_app/blob/main/lingpao_client.py)
- 当前项目协议记录：[API.md](API.md)
- 当前项目请求实现：[LeapmotorApi.kt](app/src/main/java/com/leapauto/app/LeapmotorApi.kt)

## 14. 2026-08-17 对 API_REFERENCE.md 的集成评审

> 评审范围：只读对照 `hack_lingpao_app` 的 `API_REFERENCE.md`（来源标注为零跑 App v1.22.93、2026-08-14）与当前 LeapAuto 实现。
>
> 本节不是接口可用性证明。除当前 LeapAuto 已实现并经现有代码/测试覆盖的链路外，未对第三方仓库中的接口发起真实登录、车辆读取、控车、MQTT 或操作密码请求。

### 14.1 已与当前实现对齐，不应重复接入

| 外部参考内容 | 当前 LeapAuto 实现 | 结论 |
| --- | --- | --- |
| `appuser.leapmotor.cn`、`app-gw-global-master.leapmotor.com` | `LeapmotorApi.APP_USER_HOST`、`LeapmotorApi.GLOBAL_HOST` | 已使用 |
| 手机验证码登录、RSA 手机号加密 | `loginWithSms()`、`Crypto.rsaEncryptPhone()` | 已使用；发送验证码实际路径以当前 `API.md` 和代码为准 |
| accessToken + `signParam.r2/r3` 派生 signKey | `extractNewAuth()`、`Crypto.deriveSignKey()` | 已使用 |
| 新网关 Header、SHA-256 / HMAC-SHA256 签名 | `newGatewayHeaders()`、`gatewayFetch()` | 已使用 |
| 网关 Token 续期 | `refreshGatewayToken()`，并保留旧 Token 续期回退 | 已使用 |

外部文档的验证码发送路径为 `apptec` 域名下的接口，而当前实现使用
`/app-user/applogin/compliance/sendmessagecode` 并传 RSA 加密手机号。两者不能仅因
“都能发送验证码”而相互替换。

### 14.2 可作为后续只读研究候选，当前不能直接替换

| 候选接口 | 潜在用途 | 当前状态与前置条件 |
| --- | --- | --- |
| `GET /carownerservice/v1/vehicle/list` | 新版车辆列表/车型元数据补充 | 当前已经使用 `/app/app-global-service/v1/vehicle/list`。需先取得脱敏成功响应，确认字段、签名、车型覆盖和与当前 Vehicle 模型的兼容性。 |
| `POST /carownerservice/v3/api/chassis/query` | 结构化车况候选 | 外部文档只给出路径，未给出经实车确认的完整 body/响应。不得替换当前经验证的 `app-signal-service/signal/info/query` 和 signalMap 解析。 |
| `vehicleinfo/parking/query`、`carpicture/key`、`3d/key` | 泊车/车型图片候选 | 需要明确请求参数、授权范围、响应中的隐私字段、缓存和资源许可。当前本地车型图片继续使用已授权资源。 |
| `healthyCharging/*`、`fota/*`、`scene/*`、`schedule/*` | 健康充电、OTA、场景、定时等扩展 | 仅有路径，不具备可上线的业务请求体、错误语义、车型适用范围和产品授权。应逐项独立立项。 |
| `drivingrecord/*` | 历史行程、行程能耗候选 | 外部仓库只保留通配路径，未公开具体子路径、请求 body、VIN/日期/分页参数、响应字段或错误语义；仓库代码也没有对应客户端实现。当前不能据此接入。 |

建议的验证顺序是：先为单个只读接口建立 fixture 和规范化字段对照，再由用户明确授权决定是否对测试账号发起真实只读请求。验证通过后只能以并行回退方式接入，不能一次性替换当前车况链路。

### 14.3 明确不应直接集成的内容

1. `carownerservice/v3/api/appremotectl/` 及其查询接口：外部文档未给出可覆盖当前命令的完整 payload、操作密码语义和车型结果轮询契约。当前控车仍使用已实现的旧 `appremotectl` 路径、旧 Token 签名及 AES 加密 `oppwd`。不得把旧 `cmdid/state/carvin/oppwd` 直接迁移到新版路径。
2. `appoperate/verifyoperatepwdnew`：涉及操作密码，必须先明确密码传输、加密、服务端错误语义和兼容车型；不得为排障而传输明文操作密码。
3. MQTT 实时车控：外部参考仅提供调用线索和少量命令号，尚未确认 Broker、Topic、Token、QoS、订阅生命周期和车型行为。接入将引入后台连接、耗电、会话及控车安全边界，不能作为普通 HTTP 接口直接加入。
4. 消息、共享车、二维码授权和实名认证：均超出当前个人客户端已验证范围，且涉及账户、隐私或授权边界，需单独产品和安全评审。

### 14.4 项目结论

该外部仓库可作为协议演进和只读接口研究的参考，尤其适合辅助定位新版服务域、签名体系和候选功能入口；但不能作为当前 APP 的生产接口规范或控车迁移依据。

当前最稳妥的策略是保留现有已验证的登录、车辆列表、路由、signalMap 车况和旧控车链路；如后续需要扩展，优先选择单个只读接口做脱敏契约比对。控车和 MQTT 在取得完整、车型相关的验证证据之前保持不接入。

### 14.5 行程与能耗接口专项结论

`API_REFERENCE.md` 仅列出：

    POST /carownerservice/v3/api/drivingrecord/*

仓库文件中没有列出 `*` 对应的实际资源名，也没有 `lingpao_client.py` 调用示例、分页规则或返回 JSON。因而目前无法确认它是行程列表、单次行程详情、能耗统计、轨迹还是其他服务。

当前 LeapAuto 只能从已验证的实时车况读取总里程、SOC、速度，以及服务端偶尔直接返回的综合电耗命名字段；它没有历史行程/历史能耗数据源。因此：

1. 基于当前资料不能安全开发历史行程或按次能耗页面。
2. 如只做实时展示，应继续使用当前 signalMap 和命名字段兼容读取，不依赖 `drivingrecord/*`。
3. 如要接入历史行程，必须先取得单个只读接口的脱敏成功与失败样本，至少确认：具体路径、请求方法、签名参数、VIN、时间范围、分页、行程距离、能耗字段、坐标/地址字段、时区和错误码。
4. 历史行程通常包含地点和时间线，属于比当前“车辆位置”更高敏感度的数据；仅在用户主动进入行程页并单独确认展示目的后读取，不做后台拉取、桌面插件展示、异常日志记录或本地长期保存。

## 15. 2026-08-17 对 hack_lingpao_app 逆向方法的评估

> 本节只记录方法与证据边界，不记录可用于绕过登录风控、模拟器检测、证书校验或操作密码保护的可执行步骤。

该仓库采用的是四层组合方法，而不是通过单一抓包或猜测接口完成：

1. **运行时提取与脱壳**：针对样本中的 360 加固和运行时字符串解密，提取可分析的 dex，并修复提取产物校验信息。
2. **静态分析**：用字符串、类名、调用引用和指令级分析定位域名、接口路径、Header、签名相关类、RSA 公钥和业务模块。
3. **运行时观察**：对字符串解密、HTTP 调用和关键方法进行观察，用于把静态常量和实际调用关系对应起来。
4. **黑盒复刻**：将已经确认的登录 Header、签名和部分 API 封装成 Python 客户端，再用有限的真实登录响应验证通信外形。

从其公开说明可判断的可信度：

| 产出 | 证据强度 | 使用方式 |
| --- | --- | --- |
| App 技术栈、加固方式、域名、字符串解密入口、接口目录 | 较高 | 可用作当前协议排查与候选接口发现。 |
| Header、RSA 手机号加密、signKey 派生和 HMAC 签名形态 | 中到较高 | 可与当前 LeapAuto 代码交叉验证；仍要以本项目实际实现和服务端响应为准。 |
| 短信发送、登录请求外形 | 中等 | 仓库报告做过黑盒验证，但登录会受风控和设备条件影响，不能视为长期稳定契约。 |
| 车控命令 payload、结果语义、行程/能耗、MQTT | 偏低 | 多数只是静态发现或调用线索，缺少完整车型、请求体和响应验证，不能直接上线。 |

该仓库在模拟器环境受到原生反调试、模拟器和风控组件限制后，转向在真实设备会话条件下验证协议外形。这说明它没有形成“全自动、完整复刻官方 App”的结果；其明确未完成项包括风控验证和需要有效会话才能验证的控车接口。

对本项目的可取部分是：使用静态常量、调用关系和只读响应 fixture 来缩小协议排查范围；不可取部分是把绕过风控、截取会话凭据或模拟器规避流程做成产品能力。当前 LeapAuto 应继续使用用户自己的正常登录会话和已验证接口，不把外部逆向工具链或凭据捕获流程引入 APP。
