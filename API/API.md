# 零跑云接口 API 清单（leapauto）

协议移植自社区逆向的零跑中国 App 链路，所有接口在 2026 款 C16（国内版）上验证可用。
两个主机域：

| 域 | 用途 |
| --- | --- |
| `https://appuser.leapmotor.cn` | 旧链路：短信/登录/token 续期（RSA + MD5 签名） |
| `https://app-gw-global-master.leapmotor.com` | 新网关：车辆列表/路由/车况（HMAC-SHA256 签名） |

路由域 `appRegion`（如 `https://app-gw-global-master.leapmotor.com`）由 getCarRoute 动态返回，用于车况和控车。

## 蓝牙钥匙（手动控制与可选后台配置）

新增蓝牙路径的证据来自 `D:/young/work/hackapp/BLUETOOTH_ANALYSIS.md`、对应 APK
解壳源码和 smali。用户回传的 `3.3.33` 实车日志已到达认证阶段，但被车辆以结果码 `9` 拒绝；尚未完成认证与控车互通验收，不属于上述已实车验证的云接口范围。
详细字段和离线向量分别见 [证书同步](BLUETOOTH_CERTIFICATE.md)、[蓝牙协议](BLUETOOTH_PROTOCOL.md) 和 [现场测试](BLUETOOTH_FIELD_TEST.md)。
本节描述当前源码；本轮认证修复已随 `3.3.34`（versionCode `3003034`）生成本地签名 Release，实车复测待验收。

- 蓝牙入口仅位于“我的（设置） → 蓝牙钥匙”。手动同步、扫描、连接、锁控、自动操作配置和诊断均从管理页发起；后台通知点击也定位到该页。
- 爱车页不展示蓝牙入口、状态或通道选择；原有控车按钮均使用既有云端路径，不按蓝牙连接状态切换。
  蓝牙动作确认仅在管理页可见时有效；关闭管理页或失去认证则取消待确认操作，失败不自动回退云端。
  蓝牙锁控仍在等待车辆确认时，主界面暂不允许再次发出云端车锁指令，需本次操作结束后重新点击。
- 证书同步使用运行时 `appCenter`：`POST /carownerservice/v3/api/bluetoothkey/combine/syncBluetoothKeys`，
  签名表单为 `vin/timespan/nonce/deviceID/signStr`。证书请求不经过网络诊断拦截器；证书按账号及 VIN 在 Android Keystore 加密存储中隔离。
  `APPVersion` 使用会话 App 版本；证书缓存绑定请求设备 ID，续期后的凭据与证书一起校验并提交，设备身份变化时旧连接失效。
- 蓝牙管理页会按当前账号、VIN 和请求设备 ID 获取并加密保存 `commonConfig["4"]` 的车辆蓝牙元数据；扫描候选按地址匹配优先展示，未匹配只标记“身份待核对”，不会阻断用户选择，也不会把 `version="2.0"` 转换为协议 minor。
- 蓝牙云端偏好使用官方已核实的两个上传接口：`POST /app/app-global-service/v3/api/commoninfo/transparent/conf/upload` 保存 `bleKeySwitch/bleKeyUnlock/bleKeyLock/bleKeyBtn`，`POST /app/app-global-service/v3/api/bluetoothkey/uploadAutonomyCalibrateParams` 保存或删除标定参数。云端状态与车辆 GATT `cmdId=3` 应用状态分开显示；云端 HTTP 成功不表示车辆已应用。
- 支持 `keyType=0` 的 P-256 / AES 与 `keyType=1` 的 SM2 / SM3 / SM4 会话，使用 FFFE 服务与 FFF2 特征、完整认证和手动上锁/解锁。
  SM2 公钥从 X.509 证书提取，按已解包源码的协商和派生流程实现；两类协议均待实车互通验证。
  未实现原版快速重连凭据或 EEED 座舱通道。后台断线恢复每次都重新完整认证，不复用会话密钥。
- 扫描不依赖钥匙证书，用户可先搜索附近车辆；连接前必须持有当前车辆的受支持证书。
  同 MAC 的广播信息跨包合并，缺少版本的后续广播不会覆盖已见版本；认证日志区分真实广播 minor 与默认回退 `8`。
  同步钥匙、扫描和认证连接遵守互斥规则，页面退出或进入后台会作废待处理的权限回调。
- 设置页新增四个开关：后台蓝牙钥匙 `enabled`、靠近自动解锁 `autoUnlock`、远离自动锁车 `autoLock`、微动开关控锁 `buttonEnabled`，本应用默认全部为 `false`。
  三个子选项只有总开关打开后可编辑；微动选项要求协议 minor 至少为 9，未知或旧协议不假定支持。
  开关只修改本地草稿，点击“保存设置”后仍须操作密码前置检查和明确确认；关闭总开关同时关闭三个子选项。蓝牙忙碌时暂不提交新配置。
- 设置页高级标定允许保存协议字段 `uint8 / uint16 little-endian / uint8 / uint8`，系数按百分之一编码；当前不把这些字段解释为米，也不把抓包中的单车参数改成全局默认。完整认证和 `cmdId=3` 使用同一份按账号、VIN、设备 ID 隔离的标定配置。
- 首次扫描连接只建立手动会话，自动解锁、自动锁车、微动选项保持关闭；完整认证成功后才保存设备绑定。
  绑定含账号、VIN、设备地址、协议 minor 和证书指纹，按账号及 VIN 隔离加密存储；证书变化时不得直接复用旧绑定启动后台。
- 配置同步复用加密命令 `cmdId=3`。仅当前认证会话的待同步配置收到可解密的 `2;3;0` 或 `2;3;00` 结果，并完成该帧全部写入，才进入本地确认流程。
  持久状态区分 `desired`、`applied`、`revision/confirmedRevision`；只确认当前请求版本，即使新请求值与上次相同也必须重新确认。缺失、其他结果或超时都不标记“已同步”。
  完整认证本身也携带配置字段；`cmd3` 是客户端确认依据，不能据此断言车辆仅在该回执之后才应用自动操作。
- 关闭设置会记录全关闭目标并显示“关闭待同步”；在车辆确认前保留待同步状态，条件允许时继续后台连接以同步关闭，确认后停止后台服务。
  断连、暂停通知服务、权限撤销或强制停止应用均不等于车辆已关闭自动操作。
- 用户明确保存并确认后，`BleKeyService` 以 `connectedDevice` 前台服务维持当前绑定车辆连接，通知区显示连接/同步状态。
  重连采用 2、5、10、20、30 秒退避，之后最多每 30 秒尝试一次；只重建连接、完整认证并同步当前保存配置，不排队或重发手动上锁/解锁。
  靠近、远离和微动的物理动作由车辆按配置决定；手机没有根据 RSSI 阈值发送自动锁控命令，也不把信号强度换算成米。
- 控制沿用应用已保存四位操作密码的本地前置检查，并要求一次用户动作确认；蓝牙控制帧本身不携带该密码，不代表车辆端校验了操作密码。
- GATT 写入成功只代表传输回调。控制必须在当前认证连接中、开始写入后，收到匹配动作的 `AA AC / Active` 事件才报告确认。
  事件尚无已证实的请求 ID 关联，每次认证连接仅执行一条手动控制，成功、失败或超时均断开；后台模式可重新建立认证连接，但不会代替用户发出下一条手动控制。
- 已写入后的断连或超时一律显示“结果未确认”，不自动重发、不回退云控，也不修改云端遥测或桌面插件快照。
- 收起管理页会取消待确认操作。未启用后台时离开前台会断开手动连接；已启动后台服务时，Activity 退到后台不主动销毁服务连接。
  切车、会话过期及退出登录停止服务、关闭 GATT 并作废旧回调，旧绑定目标按全关闭保留待同步状态；退出登录清除本地证书。
  从最近任务划掉应用会主动暂停后台连接，系统强制停止不保证自动恢复；重新打开后仅在当前会话、绑定、权限及操作密码满足时恢复此前已请求的配置或待关闭同步，也可点击“恢复连接”。
- 连接诊断只在内存中保留最近 120 条结构化事件，包括相对耗时、系统 GATT 状态码、协议类型、报文长度和动作确认。
  用户可主动复制或通过系统分享诊断；诊断、日志、通知与测试记录不得包含证书、密钥、PIN、VIN、蓝牙地址、设备名或原始报文。上述身份数据只可保存在明确用途的加密存储，扫描列表的设备地址仅供本机用户区分设备。

Android 权限依据：[官方蓝牙权限文档](https://developer.android.com/develop/connectivity/bluetooth/bt-permissions)。
Android 12+ 仅在用户启动扫描时请求附近设备权限；Android 11 及以前使用扫描所需定位权限，
两者均不采集或保存位置，不添加后台定位权限。新增 `FOREGROUND_SERVICE_CONNECTED_DEVICE` 和非导出的 `BleKeyService`；通知可见性、系统省电限制及强制停止后的恢复均需按 Android 版本和手机厂商现场核验。

---

## 一、登录 / 会话（旧链路 appuser）

### 1. 发送短信验证码
- **URL**: `GET /app-user/applogin/compliance/sendmessagecode`
- **认证**: 无（不需要 token）
- **参数**（query）:
  - `phoneNo`：手机号，**RSA PKCS1 加密**后再 URL-safe base64（无填充）编码
- **响应**: `result=0` 成功；`100115` 验证码错误等
- **代码**: `LeapmotorApi.sendSms()`

### 2. 短信验证码登录
- **URL**: `POST /app-user/applogin/check_login_with_phone`（业务参数只走 URL query，POST body 为空）
- **认证**: 无
- **参数**（query）:
  - `phoneNoCiphertext`：RSA 加密的手机号
  - `smsCode`：6 位短信验证码
  - `deviceID`：应用生成并持久化的设备 ID，与请求头 `APPImei` 使用同一值
  - `smDeviceId`：数美 SDK 在当前设备返回的风险指纹；与 `deviceID` 含义不同，不能互相替代
  - `os=android`，`pageUrl=`
- **响应**: `data.appLoginVO` 或兼容节点 `data.appOneLoginVO` 含 `accountId / token / refreshToken / tokenExpired`（即 oldAuth）
- **代码**: `LeapmotorApi.loginWithSms()`

#### 设备验证与风控边界

- 用户主动提交登录后，在后台初始化数美组件并有界等待 SDK 回调。组件加载失败、缺少当前进程 ABI 对应的原生库、指纹为空或获取超时时，终止本次登录，不使用普通 `deviceID` 或导入的历史指纹兜底发送请求。
- 动态 DEX 必须从当前 APK 自带资产验证完整性，并在加载前满足 Android 14 及以上的只读要求。数美原生库由 SDK 所在类加载器加载，不在父类加载器手工预加载。
- 当前数美 SDK 资产包含 175 个 SDK 类及 7 个必要辅助类的 10 个原始方法。辅助类置于 `com/leapauto/security/shumei/compat/`，避免与主应用混淆类名冲突；资产测试检查 DEX 完整性及非系统类型依赖闭合。缺少这些辅助类会使初始化的正常 URL 构建路径发生类加载错误。
- `create()` 返回成功只表示 SDK 初始化成功，不表示设备已通过车厂服务端风控。不能仅凭 SDK 输出以 `D` 开头认定其为错误；参考 SDK 的本地生成路径和异常路径都可能使用该前缀。非空 SDK 输出是否被接受，仍由服务端判断。
- 仅服务端实际返回且字段完整的挑战进入极验流程；临时管制文案本身不是挑战凭据，客户端不能自行生成服务端 `requestId`。登录错误保留可诊断的业务码及接口阶段，不记录手机号、验证码、指纹、Token 或完整响应。
- 证据边界：请求结构与指纹来源对照本地 `D:/young/work/hackapp/LOGIN_API_ANALYSIS.md` 及其指向的 `com.qian.leapcontrol 0.6` 静态代码；该样本不是官方 App 的实时成功登录证明，也不能证明某次服务端风控的触发规则或解除时间。本轮使用离线测试验证客户端行为，真实登录需单独授权。

SDK 资产由 `scripts/rebuild-shumei-dex.ps1` 离线重建，使用本地 Android 命令行工具中的 dexlib2；脚本校验来源 DEX 摘要，只提取必要方法，并校验重定位前后的执行代码一致性。当前环境的只读检查命令为：

```powershell
.\scripts\rebuild-shumei-dex.ps1 `
  -SourceDex D:\young\work\hackapp\analysis\payload\classes1.dex `
  -AndroidCommandLineLib D:\young\work\hackapp\android-sdk\cmdline-tools\latest\lib `
  -CheckOnly
```

移除 `-CheckOnly` 可重建 `app/src/main/assets/shumei.dex`。SDK 更新后必须通过资产守卫测试与全量单测；该检查不运行 SDK，也不发起数美或车厂网络请求。

### 3. 旧 token 续期
- **URL**: `GET /app-user/appuseroperate/getnewtoken`
- **认证**: 旧 token（header `XFX-CDN-CROSS-NODE` + `XFX-CDN-CROSS-REFRESH-NODE`）
- **签名参数**（query）: `timespan / nonce / deviceID / refreshtoken / accountId / accountNumber`（RSA 加密手机号），拼接后 MD5 前 16 位取 `signStr`
- **响应**: `data.token`（新 token），`tokenExpired` 默认 21600 秒
- **代码**: `LeapmotorApi.refreshOldToken()`（token 剩余 <60s 自动触发）

---

## 二、新网关认证（app-gw-global-master）

网关请求统一走 `newGatewayHeaders()`：
- Header：`source=leapmotor`、`channel=1`、`acceptLanguage=zh-CN`、`x-region=CN`、`x-api-signature-version=2.0`、`digest`、`version`、`deviceType=android`、`nonce`、`timestamp`、`deviceId`、`userId`、`carvin`、`cartype`、`x-subversion`
- 签名：`sign = HMAC-SHA256(signKey, 排序拼接的签名头 + 业务参数)`（登录前为 `SHA256(拼接串)`），`signKey` 由 `accessToken + signParam.r2 + r3` 派生

### 4. 用旧 token 换新网关凭证（登录后自动执行）
- **URL**: `POST /base/base-user/account/v1/login`
- **认证**: 不需要 accessToken（`sign=SHA256`），body 携带旧凭证
- **body**: `{"identifier": accountId, "identifierType": "1", "security": 旧token}`
- **签名参数**: `identifier / identifierType / security`（必须进签名）
- **响应**: `data.accessToken / refreshToken / signParam{r2,r3} / tokenExpireTime`（即 newAuth）
- **代码**: `LeapmotorApi.exchangeNewGateway()`

### 5. 新网关 token 续期
- **URL**: `POST /base/base-user/token/v1/refresh`
- **认证**: 新 accessToken（HMAC 签名）
- **body**: `{"refreshToken": ...}`
- **响应**: 新的 `accessToken / refreshToken / signParam`
- **代码**: `LeapmotorApi.refreshGatewayToken()`（剩余 <5min 自动触发）

---

## 三、车辆信息（新网关）

### 6. 车辆列表
- **URL**: `GET /app/app-global-service/v1/vehicle/list`
- **认证**: 新网关
- **参数**: 无
- **响应**: 含 `vin / carType` 的车辆对象列表；自动选中第一辆存入 session
- **代码**: `LeapmotorApi.listVehicles()`

### 7. 车辆路由
- **URL**: `GET /app/app-global-service/v1/vehicle/getCarRoute`
- **认证**: 新网关
- **参数**: `vin`
- **响应**: `data.appRegion / appCenter`（车况/控车都要用它拼 URL）
- **代码**: `LeapmotorApi.getCarRoute()`（缓存到 session，之后自动复用）

### 7.1 车辆外观图片元数据
- **URL**: `POST /carownerservice/vehicle/v1/carpicture/key`
- **认证**: 新网关（`newGatewayHeaders`，参数 `deviceID / vin` 参与签名）
- **参数**: `deviceID`、`vin`（`application/x-www-form-urlencoded`）
- **响应**: `data.shareBindUrl`（官方 CDN 车辆外观图）、`data.key`（分层切图包密钥）
- **代码**: `LeapmotorApi.getVehiclePictureMeta()`

---

## 四、车况（新网关 + 路由域）

### 8. 车况查询（原始）
- **URL**: `POST {appRegion}/app/app-signal-service/signal/info/query`
- **认证**: 新网关
- **参数**: `vin`（进签名）
- **body**: `{"vin": ...}`
- **响应**: 深层嵌套的 JSON，内含 `signalMap`
- **代码**: `LeapmotorApi.getVehicleStateRaw()`

### 9. 车况查询（解码）
- 对原始响应用 `extractSignalMap()` 提取 `signalMap`，再用 `SignalTable.decode()` 把数字 signal ID（如 `"1182":29`）翻译成命名栏位（锁/电量/续航/胎压/空调等 80+ 字段）
- **代码**: `LeapmotorApi.getVehicleState()`

## 五、里程 / 能耗（旧链路 appuser）

### 10. 行驶里程与能耗明细
- **URL**: `GET /carownerservice/v3/api/drivingrecord/mileage/energy/detail`
- **认证**: 旧 App token（`XFX-CDN-CROSS-NODE`）和 `oldSignedParams()` 签名。
- **参数**（query）: `begintime`、`endtime`（Unix 秒级时间戳）和 `vin`，以及旧签名参数 `nonce / deviceID / signStr`。
- `begintime` 使用车辆购买日 00:00:00；当前会话没有可用购车日时，客户端首次读取回退到当天 00:00:00，后续优先依据已返回的 `deliveryDays` 推算购车日。
- `endtime` 使用当前时刻的 Unix 秒级时间戳；该详情接口不发送 `timespan`。
- **路由**: `https://appgateway.leapmotor.com`；旧 token 鉴权失败后续期并重试一次。
- **代码**: `LeapmotorApi.getMileageEnergy()`。
- **数据边界**: 该接口目前只有 MCP 的路径/签名链路证据，未沉淀真实响应字段样本；客户端只对已识别业务字段作结构化展示，其余非敏感业务字段在“其他数据”中按原键值展示，不推断单位或含义。

### 10.1 近 7 日行驶里程明细
- **URL**: `GET /carownerservice/v3/api/drivingrecord/mileage/energy/detail`
- **认证**: 旧 App token（`XFX-CDN-CROSS-NODE`）和 `oldSignedParams()` 签名。
- **路由**: `https://appgateway.leapmotor.com`。
- **参数**（query）:
  - `vin`：当前车辆 VIN；
  - `begintime`：上海时区 7 天前当地零点，Unix **毫秒**时间戳；
  - `endtime`：当前时刻，Unix **毫秒**时间戳；
  - `timespan / nonce / deviceID / signStr`：由旧签名流程生成。
- **响应**: `data.detail[]` 中读取 `day` 与 `accumulatedMileage`。客户端按服务端原始顺序保留有效结果的最后 8 条，页面展示最后 7 条并求和为近 7 天里程；空值、负数、非有限数和异常大数值会被丢弃。
- **代码**: `LeapmotorApi.getRecentMileageEnergy()`、`RecentMileageEnergyParser`。
- **边界**: 该近 7 日方法与 `getMileageEnergy()` 生命周期明细方法分开；后者仍使用购买日至今的秒级 `begintime/endtime` 且不发送 `timespan`，不得混用时间单位或参数契约。

### 11. 近六周百公里能耗与排行
- **URL**: `GET /carownerservice/v3/api/drivingrecord/getLastNweeks100kmECAndRank`
- **认证**: 旧 App token（`XFX-CDN-CROSS-NODE`）和 `oldSignedParams()` 签名。
- **参数**（query）: `carvin`，以及旧签名通用参数 `timespan / nonce / deviceID / signStr`。该接口不接受 `vin`。
- **路由**: `https://appgateway.leapmotor.com`。

### `signalMap` 关键数据速查

> 数据来源是车辆实时上报的数字信号。不同车型、车辆状态和服务端版本可能导致字段缺失；只有已经在 `SignalTable.MAP` 中验证过的 ID 才能作为业务数据展示。未映射数字字段不能按名称猜测含义。

| 类别 | 信号 ID → 字段 | 用途 / 单位 |
| --- | --- | --- |
| 电量与续航 | `1204 → soc`、`3235 → fuelSoc`、`100003 → preciseSoc`（兼容回退）、`3260 → expectedMileage`、`3257 → electricRangeStandard`、`2188 → liveRemainingRange`、`3262 → rangeMode` | 纯电/燃油剩余百分比、各模式剩余续航、实时续航；续航模式 `0=标准续航`、`1=动态续航` |
| 充电 | `1149 → chargeState`、`1200 → chargeRemainTime`、`1178 → batteryCurrent`、`1177 → batteryVoltage`、`1197 → dcInputFastCharge`、`3736 → chargeCompleted` | 充电状态、预计剩余时间、电流、电压、直流快充、充电完成状态 |
| 电池热管理 | `1182 → minBatteryTemp`、`1186 → batteryThermalRequest`、`48 → healthyChargeEnabled` | 最低电池温度、热管理请求（已验证 `4=预热中`、`0=未预热`，其他值按未知处理）、健康充电开关 |
| 里程与驾驶 | `1318 → totalMileage`、`1319 → speed`、`1010 → gearStatus`、`1944 → vehicleState`、`1480 → parkingBrakeState` | 总里程、车速、挡位、整车状态、驻车制动 |
| 门锁与车门 | `1298 → driverDoorLockStatus`、`1277/1278/1279/1280 → 四门状态`、`1281 → bbcmBackDoorStatus` | 门锁、四门、后备箱状态；后备箱遥测仅按 `0=关闭`、`1=打开` 解释，其他值按未知处理 |
| 空调 | `1938 → acSwitch`、`2183/2184 → acSetting/acSettingRight`、`1349 → interiorTemp`、`1943 → recirculationMode`、`1945 → windshieldDefrost`、`1946 → rearWindowHeating`、`1941 → acAirVolume` | 空调开关、左右温度、车内温度、循环（`0=外循环`、`1=内循环`）、前后除雾、风量；旧 T03 在 `1943` 缺失时兼容命名字段 `acCircleMode`（`false=外循环`、`true=内循环`） |
| 车窗与天幕 | `1693/1694/1695/1696 → 四窗状态`、`1724 → roofOpening` | 开关状态、天幕开度 |
| 座椅与舒适 | `2100/2118 → 主驾/副驾座椅加热`、`2101/2119 → 主驾/副驾座椅通风`、`1879/1880 → 二排左/二排右座椅加热`、`3727/3728 → 二排左/二排右座椅通风`、`1816 → steeringWheelHeating`、`49/50 → 后视镜加热` | 前后排座椅加热/通风状态（0=关，1..3=档位）、方向盘加热（0=关，1..2=档位）、左右后视镜加热（0=关，1=开） |
| 轮胎 | `2646 → 左前`、`2653 → 右前`、`2660 → 左后`、`2667 → 右后`；`2641/2648/2662/2655 → 对应胎压状态` | 四轮胎压及异常状态（数值映射按最新实车核验修正；告警状态映射保持原验证结果） |
| 车载冰箱 | `10709 → fridgeSwitch`、`10708 → fridgeMode`、`10707 → fridgeTargetTemp`、`10711 → fridgeStyle`、`10712 → fridgeFault`、`11190 → fridgeParkSwitch`、`11189 → fridgeParkDurationHours`、`11191 → fridgeParkCycles`、`11260 → fridgeParkEndTime` | 冰箱开关（0=关，1=开）、模式（0=制冷，1=制热）、设定温度（制冷设定 ℃，制热为 50℃）、风格（0=标准，1=急速）、故障码、离车运行开关（0=关，1=开）、离车时长（小时）、离车频次（0=单次，1=每次离车）、离车结束时间戳（秒级） |
| 安防与位置 | `1255 → vehicleSecurityActive`、`3636 → sentryMode`、`3725/3724 → latitude/longitude` | 安防、哨兵模式、车辆坐标 |

**当前已知限制**：C16 胎温和综合电耗尚无已验证的数字信号 ID。服务端若直接返回命名字段，可兼容读取；否则必须先采集原始 `signalMap` 再补充映射。

### 增程车续航信号（用户确认 + 本地对照实现）

增程车在两种模式下返回的是对应能源的剩余续航值：

- `3256 → fuelRangeStandard`：CLTC 燃油剩余续航。
- `3257 → electricRangeStandard`：CLTC 纯电剩余续航。
- `3258 → combinedRangeStandard`：CLTC 综合剩余续航。
- `3259 → fuelRangeDynamic`：WLTC 燃油剩余续航。
- `3260 → expectedMileage`：WLTC 纯电剩余续航。
- `3261 → combinedRangeDynamic`：WLTC 综合剩余续航。
- `1204 → soc`：纯电剩余续航百分比，纯电和增程车型通用。
- `3235 → fuelSoc`：增程车型燃油剩余续航百分比。

桌面插件的两条进度分别表示两种能源自身的剩余比例，不是续航构成占比：

- 纯电进度直接取 `1204`；`1204` 缺失时才兼容回退到旧 `100003`。
- 燃油进度直接取 `3235`。
- `3257/3256` 是标准模式剩余值，不作为总续航分母。只有额外提供明确 `maxRange/maxFuelRange` 时，才可在百分比缺失时按剩余值 ÷ 满电总续航 × 100 回退。
- 缺少百分比和明确满电总续航时展示空进度，禁止使用综合续航作为任一分母。

---

## 六、控车（旧链路 + 路由域，全部需要操作密码 PIN）

签名：`oldSignedParams()`（`timespan / nonce / deviceID / token` + 业务参数，MD5 前 16 位取 `signStr`）
请求头：`APPPlatform / APPVersion / APPImei / C-VERSIONS / XFX-CDN-VRS / XFX-CDN-CROSS-NODE`（旧 token）
`oppwd`：操作密码用 **AES-128-CBC** 加密。在新网关架构（v1.22+）下，Key/IV 由当前网关 JWT `accessToken` 前 64 位派生（`key = md5(accessToken[0..32])[8..24]`，`iv = md5(accessToken[32..64])[8..24]`）；旧版接口或无网关 Token 时兼容回退旧 Token。另官方 App 支持 `POST /carownerservice/v3/api/appoperate/verifyoperatepwdnew` 进行前置密码校验。

### 12. 下发控车命令
- **URL**: `POST {appRegion}/app/app-control-service/v3/api/appremotectl`
- **认证**: 旧 token
- **表单参数**: `cmdid / state / carvin / oppwd`（AES 加密的 PIN）
- **响应**: `{"result":0, "data":"<msgID>", ...}`，msgID 在 `data` 顶层字符串
- **代码**: `LeapmotorApi.sendControl()`

### 13. 查询控车执行结果（轮询）
- **URL**: `GET {appRegion}/app/app-control-service/v3/api/appremotectl/query`
- **认证**: 旧 token
- **参数**: `msgID`
- **代码**: `LeapmotorApi.queryControlResult()`（首次等待 1s，之后每 0.5s 轮询；App 最多约 24s，桌面插件最多约 12s）

### 控车命令全集（与 `leap-cn-mcp commandPresets` 对齐）

> 本表以 `app/src/main/java/com/leapauto/app/Models.kt` 的 `Commands.build()` 为当前 App 实际下发依据。每次控车均需要已登录会话和零跑 App 操作 PIN。

| name | cmdid | state | 说明 |
| --- | --- | --- | --- |
| `lock` | 110 | `{"value":"lock"}` | 上锁 |
| `unlock` | 110 | `{"value":"unlock"}` | 解锁 |
| `trunkOpen` | 130 | `{"value":"true"}` | 开后备箱 |
| `trunkClose` | 130 | `{"value":"false"}` | 关后备箱 |
| `frunkOpen` | 131 | `{"value":"100"}` | 开前备箱 |
| `frunkClose` | 131 | `{"value":"0"}` | 关前备箱 |
| `windowOpen` | 230 | `{"value":"5"}` | 车窗半开 |
| `windowVent` | 230 | `{"value":"2"}` | 车窗通风 / 微开 |
| `windowClose` | 230 | `{"value":"0"}` | 车窗全关 |
| `sunshadeOpen` | 240 | `{"value":"10"}` | 遮阳帘打开 |
| `sunshadeClose` | 240 | `{"value":"0"}` | 遮阳帘关闭 |
| `horn` | 120 | `{"value":"true"}` | 鸣笛寻车 |
| `batteryPreheat` | 160 | `{"value":"ptcon"}` | 电池预热开 |
| `batteryPreheatOff` | 160 | `{"value":"ptcoff"}` | 电池预热关 |
| `seatHeat` | 301 | `{"position":"left_front","level":"3"}` | 座椅加热（position: left_front/right_front/left_rear/right_rear，level: 0..3） |
| `seatVentilation` | 370 | `{"position":"left_front","level":"3"}` | 座椅通风（position: left_front/right_front/left_rear/right_rear，level: 0..3） |
| `steeringWheelHeat` | 320 | `{"level":"2"}` | 方向盘加热（level: 0=关, 1=弱, 2=强） |
| `rearviewMirrorHeat` | 440 | `{"value":"2"}` | 后视镜加热（value: 1=关, 2=开） |
| `fridgeOn` | 500 | `{"cycles":"1","duration":3600,"enable":1,"mode":"cold","parkEnable":0,"style":"normal","temp":4,"value":"false"}` | 车载冰箱开机（默认制冷 4°C） |
| `fridgeOff` | 500 | `{"cycles":"1","duration":3600,"enable":0,"mode":"cold","parkEnable":0,"style":"normal","temp":4,"value":"false"}` | 车载冰箱关机 |
| `fridgeControl` | 500 | `{"cycles":"...","duration":...,"enable":...,"mode":"...","parkEnable":...,"style":"...","temp":...,"value":"false"}` | 车载冰箱全功能控制（温控/模式/风格/离车运行） |
| `sentryOn` | 400 | `{"operation":"on"}` | 开启哨兵模式 |
| `sentryOff` | 400 | `{"operation":"off"}` | 关闭哨兵模式 |

哨兵模式切换前先刷新 `sentryMode`，已处于目标状态时不重复发送命令。POST 返回轮询 ID 后复用现有查询链路，仅当查询响应的 `result`（缺失时兼容 `code`）为 `0`，且随后刷新到的 `sentryMode` 等于目标值时，UI 才显示“已完成”；命令查询成功但遥测尚未同步时显示“状态待确认”。
| `acOn` | 170 | `{"operate":"manual","temperature":"24","windlevel":"3","mode":"cold","circle":"in","wshld":"0","position":"all"}` | 开空调（制冷 24°C） |
| `acOff` | 170 | `{"operate":"off","temperature":"24","windlevel":"3","mode":"nohotcold","circle":"out","wshld":"0","position":"all"}` | 关空调 |
| `defrost` | 170 | `{"operate":"manual","temperature":"24","windlevel":"5","mode":"cold","circle":"out","wshld":"1","position":"wshld"}` | 前风挡除雾 |
| `quickCool` | 170 | `{"operate":"manual","temperature":"18","windlevel":"7","mode":"cold","circle":"in","wshld":"0","position":"all"}` | 极速制冷 |
| `quickHeat` | 170 | `{"operate":"manual","temperature":"32","windlevel":"7","mode":"hot","circle":"in","wshld":"0","position":"all"}` | 极速制热 |
| `deodorize` | 170 | `{"operate":"manual","temperature":"24","windlevel":"7","mode":"nohotcold","circle":"out","wshld":"0","position":"all"}` | 快速除味（静态客户端证据，待实车验证） |

`Commands.buildAc(temperature)` 支持 `16–32°C` 自定义温度：`≤26°C` 使用 `cold`，`≥27°C` 使用 `hot`，风量固定为 `7`。

### 空调精细控制（静态客户端证据，待授权实车验证）

空调详情页的精细设置继续复用 `cmdid=170` 和现有 `sendControl()` / `queryControlResult()` 安全链路。车型能力优先读取车辆列表响应中的 `funcConfig.HVAC`：

- 温度范围：`funcConfig.HVAC.temperature.min / max`。只有 `10–40°C` 内且 `min <= max` 时采用；否则 UI 回退为 `19–32°C`。
- 风量范围：`funcConfig.HVAC.fan.min / max`。只有非负整数且 `min <= max` 时采用；若范围不能用于可选档位，则 UI 回退为 `1–7` 档。

每次开启、关闭或应用精细设置都完整发送以下 7 个字符串字段，不能只发送变化项：

| 字段 | 当前值域 | 说明 |
| --- | --- | --- |
| `operate` | `manual / off` | 开启或关闭 |
| `temperature` | 车型能力范围内的整数字符串 | 目标温度 |
| `windlevel` | 车型能力范围内的整数字符串 | 风量档位 |
| `mode` | `cold / nohotcold` | 普通开启固定为制冷模式；关闭为无冷热 |
| `circle` | `in / out` | 内循环或外循环 |
| `wshld` | `0 / 1` | 前挡除雾开关 |
| `position` | `all / wshld` | 全向出风或前挡出风 |

`wshld` 与 `position=wshld` 是两个独立设置，不能自动绑定。当前已验证车况信号可回读空调开关、设定温度、风量、循环和前挡除雾；没有可靠的出风位置回读，因此页面不把服务端受理或结果查询成功误报成该字段已被车辆确认。空调详情页当前隐藏循环方式和出风位置编辑模块，但仍按协议发送这两个字段的默认/当前内部值。

空调详情页“快捷操作”新增“快速除味”，沿用 4.9 客户端静态确认的 `DEODORIZE` 编码：`operate=manual`、`mode=nohotcold`、风量 `7`、外循环、除雾关闭、全向出风。该命令复用现有操作密码、签名、结果查询和车况刷新链路，车辆实际是否执行仍需授权实车验证。

空调 `cmdid=170` 指令在 POST 返回 `msgID` 时使用通用控车结果查询链路：首次等待 1 秒，之后每 0.5 秒查询，最多等待约 24 秒。查询成功后再刷新车况，只有空调遥测与目标状态一致时显示“已完成”；服务端未返回 `msgID` 时保留旧车型兼容路径，显示“已发送，等待车辆状态确认”并延迟刷新车况。

**车型兼容性**：命令是否可用取决于车型硬件和当前车辆状态。前备箱快捷操作仅对 D19 展示，且以“开前备箱/关前备箱”两个明确动作呈现；电池预热快捷操作根据 `batteryThermalRequest` 的已知值在开启和关闭之间切换，状态未知时不发送命令。遮阳帘、电池预热等功能仍需逐车验证服务端权限和执行结果。

---

## 认证/签名速查

| 项 | 旧链路（appuser / 控车） | 新网关 |
| --- | --- | --- |
| 凭证 | `token` + `refreshToken`（oldAuth） | `accessToken` + 派生 `signKey`（newAuth） |
| 签名 | 参数排序拼接 → MD5 前 16 位（`signStr`） | 排序拼接 → HMAC-SHA256（hex） |
| 手机号 | RSA（PKCS1v1.5）+ URL-safe base64 无填充 | — |
| 操作密码 | AES-128-CBC，key/iv 由旧 token 派生 | — |
| 失效重试 | `result=39` / 含"token失效"等 → 续期后重试一次 | HTTP 401/403 或 token 过期文案 → 续期后重试一次 |
