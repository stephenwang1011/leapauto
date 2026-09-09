# 零跑云接口 API 清单（leapauto）

协议移植自社区逆向的零跑中国 App 链路，所有接口在 2026 款 C16（国内版）上验证可用。
两个主机域：

| 域 | 用途 |
| --- | --- |
| `https://appuser.leapmotor.cn` | 旧链路：短信/登录/token 续期（RSA + MD5 签名） |
| `https://app-gw-global-master.leapmotor.com` | 新网关：车辆列表/路由/车况（HMAC-SHA256 签名） |

路由域 `appRegion`（如 `https://app-gw-global-master.leapmotor.com`）由 getCarRoute 动态返回，用于车况和控车。

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
- **URL**: `POST /app-user/applogin/check_login_with_phone`（参数走 query + POST body）
- **认证**: 无
- **参数**（query）:
  - `phoneNoCiphertext`：RSA 加密的手机号
  - `smsCode`：6 位短信验证码
  - `deviceID` / `smDeviceId`：设备 ID
  - `os=android`，`pageUrl=`
- **响应**: `data.appLoginVO` 含 `accountId / token / refreshToken / tokenExpired`（即 oldAuth）
- **代码**: `LeapmotorApi.loginWithSms()`

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
| 车窗与天幕 | `3727/3728/1879/1880 → 四窗开度`、`1693/1694/1695/1696 → 四窗状态`、`1724 → roofOpening` | 车窗百分比、开关状态、天幕开度 |
| 轮胎 | `2646 → 左前`、`2653 → 右前`、`2660 → 左后`、`2667 → 右后`；`2641/2648/2662/2655 → 对应胎压状态` | 四轮胎压及异常状态（数值映射按最新实车核验修正；告警状态映射保持原验证结果） |
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
`oppwd`：操作密码用 **AES-128-CBC**（key/iv 由旧 token 派生）加密

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
