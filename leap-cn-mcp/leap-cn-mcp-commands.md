# leap-cn-mcp MCP 指令大全

> 源码版本：`leap-cn-mcp@0.1.7`  
> 分析时间：2026-08-17  
> 来源文件：`bin/leap-cn-mcp.js`、`src/leapmotor-cn-sdk.js`、`package.json`

---

## 1. 包基本信息

| 属性 | 内容 |
|------|------|
| 名称 | `leap-cn-mcp` |
| 版本 | `0.1.7` |
| 描述 | 零跑中国 App 链路的 MCP stdio 服务，供支持 MCP 的 AI 客户端调用 |
| 运行时 | Node.js `>=20` |
| 模块格式 | ESM |
| MCP 入口 | `bin/leap-cn-mcp.js` |
| SDK 核心 | `src/leapmotor-cn-sdk.js` |
| 默认 session 文件 | `.leap-session.json` |

---

## 2. MCP Tools 完整列表

当前 MCP 服务共暴露 **16 个 tools**：

| 序号 | Tool 名称 | 类型 | 说明 |
|------|-----------|------|------|
| 1 | `leap_session` | 查询 | 读取当前会话摘要 |
| 2 | `leap_refresh_token` | 查询 | 立即刷新新网关 access token |
| 3 | `leap_refresh_old_token` | 查询 | 刷新旧 App token（用于远控/里程/位置签名） |
| 4 | `leap_send_sms` | 查询 | 发送短信验证码 |
| 5 | `leap_login_sms` | 查询 | 验证码登录并交换网关 token |
| 6 | `leap_vehicles` | 查询 | 查询账号下绑定的车辆列表 |
| 7 | `leap_select_vehicle` | 查询 | 选择后续调用使用的车辆 |
| 8 | `leap_route` | 查询 | 查询所选车辆路由 |
| 9 | `leap_state` | 查询 | 查询车辆实时状态 |
| 10 | `leap_mileage` | 查询 | 查询里程和能耗详情 |
| 11 | `leap_location` | 查询 | 查询车辆位置 |
| 12 | `leap_overview` | 查询 | 综合查询：路由 + 状态 + 里程 + 位置 |
| 13 | `leap_commands` | 查询 | 列出支持的远控命令预设 |
| 14 | `leap_control_preview` | 控车 | 构建控车请求但不发送（dryRun） |
| 15 | `leap_control_send` | 控车 | 发送真实控车命令 |
| 16 | `leap_control_query` | 控车 | 查询控车执行结果 |

---

## 3. 查询类指令详细参数

### 3.1 `leap_session`

**描述**：读取当前 Leapmotor 会话摘要。

**参数**：无

**返回示例字段**：
- `session.deviceId`
- `session.oldLogin`
- `session.newLogin`
- `session.accessTokenValid`
- `session.accessTokenExpiresAt`
- `session.oldTokenValid`
- `session.oldTokenExpiresAt`
- `session.oldTokenRemainingSec`
- `session.phonePresent`
- `session.accountIdMasked`
- `session.selectedVin`
- `session.selectedVinMasked`
- `session.selectedCarType`
- `session.vehicleCount`
- `session.appRegion`
- `session.appCenter`
- `session.sessionFile`

---

### 3.2 `leap_refresh_token`

**描述**：立即刷新新网关 access token。普通认证工具也会自动刷新。

**参数**：无

**返回字段**：
- `refreshed: true`
- `kind: "gateway"`
- `session`

---

### 3.3 `leap_refresh_old_token`

**描述**：通过 `getnewtoken` 刷新旧 App token（用于远控/里程/位置签名）。需要 session 中已有 `phone`，或显式传入 `phone` 参数。

**参数**：

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `phone` | string | 否 | 手机号；如果 session 中已存储则可不传 |

**返回字段**：
- `refreshed: true`
- `kind: "old"`
- `tokenExpired`
- `session`

---

### 3.4 `leap_send_sms`

**描述**：发送短信验证码。手机号会在服务端按 App 规则 RSA 加密后发送，不落盘保存。

**参数**：

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `phone` | string | 是 | 手机号 |

**接口**：`GET /app-user/applogin/compliance/sendmessagecode`

---

### 3.5 `leap_login_sms`

**描述**：验证短信验证码并交换网关 token。登录成功后会存储 `phone` 用于旧 token 自动续期。

**参数**：

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `phone` | string | 是 | 手机号 |
| `smsCode` | string | 是 | 短信验证码 |

**接口**：`POST /app-user/applogin/check_login_with_phone`

**返回字段**：
- `login`（脱敏后的登录响应）
- `exchanged`
- `exchangeCode`
- `signKeyBytes`
- `session`

---

### 3.6 `leap_vehicles`

**描述**：查询账号下绑定的车辆列表。

**参数**：无

**接口**：`GET /app/app-global-service/v1/vehicle/list`

**返回字段**：
- `vehicles`（数组，每个元素包含 vin、carType 等）
- `raw`
- `session`

---

### 3.7 `leap_select_vehicle`

**描述**：选择后续调用使用的活动车辆。

**参数**：

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `vin` | string | 是 | 车辆 VIN |
| `carType` | string | 否 | 车型代码，如 `S01` |

**返回字段**：
- `session`

---

### 3.8 `leap_route`

**描述**：查询所选车辆的路由信息（`appRegion`、`appCenter`），用于后续接口寻址。

**参数**：无

**接口**：`GET /app/app-global-service/v1/vehicle/getCarRoute`

**返回字段**：
- `route`
- `raw`
- `session`

---

### 3.9 `leap_state`

**描述**：查询车辆实时状态，返回原始响应、标准化摘要和每个 `signalMap` 字段的定义。**必须以 `fieldDefinitions` 中的语义、单位、枚举、置信度为准**；`confidence=unknown` 的字段不得自行猜测。

**参数**：无

**接口**：`POST /app/app-signal-service/signal/info/query`

**返回结构**：
```
{
  raw: 原始响应,
  summary: {
    battery: { soc, expectedMileage, batteryCurrent, batteryVoltage, chargeState, chargeRemainTime, dcInputFastCharge, chargesocSetting },
    locks: { driverDoorLockStatus, bcmDoorCtrlAllow },
    doors: { driverDoor, passengerDoor, leftRearDoor, rightRearDoor, trunk },
    windows: { isSupportWindowsRemoteControl, leftFrontWindowPercent, leftRearWindowPercent, rightFrontWindowPercent, rightRearWindowPercent },
    climate: { acSwitch, acSetting, acAirVolume, acAirVolumeSetting, acWindDirection, acCircleMode, acCoolingAndHeating, acTempMode, indoorTemp, ptcState, ptcPowerSettingValue, minSingleTemp },
    location: { latitude, longitude, privacyGPS, privacyData }
  },
  fieldDefinitions: { ... },
  session: { ... }
}
```

**`fieldDefinitions` 已知字段置信度说明**（部分）：

| 字段 | 含义 | 单位 | 置信度 |
|------|------|------|--------|
| `soc` | 动力电池剩余电量 | `%` | high |
| `chargeState` | 充电状态 | — | high |
| `chargeRemainTime` | 预计充电剩余时间 | `min` | high |
| `chargesocSetting` | 用户设置的充电 SOC 上限 | `%` | high |
| `batteryCurrent` | 动力电池包电流 | `A (likely)` | medium |
| `dumpEnergy` | 动力电池估算剩余能量 | `Wh (likely)` | medium |
| `expectedMileage` | 车辆估算剩余续航 | `km` | high |
| `batteryVoltage` | 动力电池包总电压 | `V (likely)` | medium |
| `dcInputFastCharge` | 直流快充输入状态 | boolean-like | high |
| `acSwitch` | 空调开关状态 | boolean | high |
| `acSetting` | 空调设定温度 | `°C` | high |
| `acCoolingAndHeating` | 空调制冷制热模式 | — | high |
| `acCircleMode` | 空调循环模式 | boolean | high |
| `indoorTemp` | 车内温度 | `°C (likely)` | medium |
| `outdoorTemp` | 车外温度 | `°C (likely)` | medium |
| `driverDoorLockStatus` | 主驾/车门锁锁止状态 | boolean | high |
| `bbcmBackDoorStatus` | 后备箱/尾门开启状态 | boolean | high |
| `lbcmDriverDoorStatus` | 主驾车门开启状态 | boolean | high |
| `rbcmDriverDoorStatus` | 副驾车门开启状态 | boolean | high |
| `latitude` | 车辆纬度 | decimal degrees | high |
| `longitude` | 车辆经度 | decimal degrees | high |
| `leftFrontWindowPercent` | 左前车窗位置刻度 | S01: 0-10 | high |
| `rightFrontWindowPercent` | 右前车窗位置刻度 | S01: 0-10 | high |
| `leftRearWindowPercent` | 左后车窗位置刻度 | S01: 0-10 | high |
| `rightRearWindowPercent` | 右后车窗位置刻度 | S01: 0-10 | high |
| `leftFrontTirePressure` | 左前轮胎压力 | `kPa (S01 verified)` | high |
| `rightFrontTirePressure` | 右前轮胎压力 | `kPa (S01 verified)` | high |
| `leftRearTirePressure` | 左后轮胎压力 | `kPa (S01 verified)` | high |
| `rightRearTirePressure` | 右后轮胎压力 | `kPa (S01 verified)` | high |
| `leftFrontTirePressureState` | 左前轮胎压力告警状态 | boolean-like | high |
| `speed` | 车辆速度 | `km/h (likely)` | medium |
| `totalMileage` | 车辆总里程 | `km (likely)` | medium |
| `minSingleTemp` | 动力电池最低单体温度 | `°C (likely)` | medium |
| `ptcPowerSettingValue` | PTC 功率/加热设定原始值 | — | medium |
| `ptcState` | PTC 加热原始状态 | — | low |

---

### 3.10 `leap_mileage`

**描述**：查询所选车辆的里程和能耗详情。不是单纯的里程，而是**里程和能耗详情一起返回**。

**参数**：无

**接口**：`/carownerservice/v3/api/drivingrecord/mileage/energy/detail`

**返回字段**：
- `raw`（原始响应）
- `session`

---

### 3.11 `leap_location`

**描述**：查询车辆位置（旧 chassis/location 端点）。注意：实时状态 `leap_state` 中的经纬度可能更准确。

**参数**：无

**接口**：`/carownerservice/v3/api/chassis/query`

**返回字段**：
- `raw`（原始响应）
- `session`

---

### 3.12 `leap_overview`

**描述**：综合查询，并行获取路由、实时状态、里程和位置。

**参数**：无

**内部调用**：
- `getVehicleRoute()`
- `getVehicleState()`
- `getMileageEnergy()`
- `getLocation()`

**返回字段**：
```
{
  route: ...,
  carState: ...,
  mileage: ...,
  location: ...,
  session: { ... }
}
```

---

### 3.13 `leap_commands`

**描述**：列出当前 SDK 支持的所有远控命令预设。

**参数**：无

**返回字段**：
- `commands`（对象，key 为命令名，value 为预设详情）

---

## 4. 控车类指令详细说明

### 4.1 `leap_control_preview`

**描述**：构建控车请求，但**不会发送**，仅返回 `dryRun` 摘要，用于查看将要发送的指令。

**参数**：

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `command` | string | 是 | 命令预设名称 |
| `windowPosition` | integer | 否 | S01 前窗位置 0-10；0=最大打开，8=微开，10=关闭；仅 `command=window` 时使用 |
| `value` | string | 否 | 旧版自定义值；`windowPosition` 优先级更高 |
| `cmdid` | string | 否 | 自定义命令 ID；仅 `command=custom` 时使用 |
| `stateJson` | string | 否 | 自定义 state JSON；仅 `command=custom` 或 `command=ac` 时使用 |
| `operationPassword` | string | 否 | 操作密码；配置了 `LEAP_OPERATION_PASSWORD` 环境变量时可省略 |

**返回字段**：
```
{
  dryRun: true,
  appointment: boolean,
  method: "POST",
  url: string,
  command: { cmdid, state, label, acceptsOperationPassword, requiresOperationPassword },
  body: { ... },
  session: { ... }
}
```

---

### 4.2 `leap_control_send`

**描述**：发送真实控车命令，**会触发车辆动作**。

**安全条件（必须同时满足）**：

1. 启动 MCP 服务时设置环境变量 `LEAP_ALLOW_WRITE=1`
2. tool 参数 `allowWrite=true`
3. tool 参数 `confirm=true`

不满足任一条件都会拒绝发送。

**参数**：

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `command` | string | 是 | 命令预设名称 |
| `windowPosition` | integer | 否 | S01 前窗位置 0-10 |
| `value` | string | 否 | 旧版自定义值 |
| `cmdid` | string | 否 | 自定义命令 ID |
| `stateJson` | string | 否 | 自定义 state JSON |
| `operationPassword` | string | 条件必填 | 需要操作密码的命令必须提供；或配置 `LEAP_OPERATION_PASSWORD` |
| `allowWrite` | boolean | 是 | 必须为 `true` |
| `confirm` | boolean | 是 | 必须为 `true` |

**返回字段**：
```
{
  dryRun: false,
  command: { cmdid, state, label, acceptsOperationPassword, requiresOperationPassword },
  raw: 原始响应,
  session: { ... }
}
```

---

### 4.3 `leap_control_query`

**描述**：根据 `msgID` 查询控车执行结果。

**参数**：

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `msgID` | string | 是 | 控车返回的 msgID/eventId |

**接口**：`GET /app/app-control-service/v3/api/appremotectl/query`

**返回字段**：
- `raw`
- `session`

---

## 5. 全部控车命令预设

SDK 内置的 `commandPresets` 定义了所有支持的控车命令：

| 命令名 | 标签 | cmdid | state / 说明 | 需要操作密码 |
|--------|------|-------|--------------|--------------|
| `lock` | 上锁 | `110` | `{"value":"lock"}` | 是 |
| `unlock` | 解锁 | `110` | `{"value":"unlock"}` | 是 |
| `trunk` | 后备箱/尾门触发 | `130` | `{"value":"true"}` | 是 |
| `trunkOpen` | 后备箱/尾门打开 | `130` | `{"value":"true"}` | 是 |
| `trunkClose` | 后备箱/尾门关闭 | `130` | `{"value":"false"}` | 是 |
| `frunkOpen` | 前备箱打开 | `131` | `{"value":"100"}` | 是 |
| `frunkClose` | 前备箱关闭 | `131` | `{"value":"0"}` | 是 |
| `horn` | 鸣笛寻车 | `120` | `{"value":"true"}` | 是 |
| `batteryPreheat` | 电池/座舱预热开启 | `160` | `{"value":"ptcon"}` | 是 |
| `batteryPreheatOff` | 电池/座舱预热关闭 | `160` | `{"value":"ptcoff"}` | 是 |
| `window` | 车窗控制 | `230` | 根据车型和模式计算 value；见下方车窗特殊说明 | 是 |
| `windowClose` | 车窗关闭 | `230` | 根据车型计算 close 值 | 是 |
| `windowVent` | 车窗通风 | `230` | 根据车型计算 vent 值 | 是 |
| `windowOpen` | 车窗打开 | `230` | 根据车型计算 open 值 | 是 |
| `sunshadeOpen` | 天幕/遮阳帘打开 | `240` | `{"value":"10"}` | 是 |
| `sunshadeClose` | 天幕/遮阳帘关闭 | `240` | `{"value":"0"}` | 是 |
| `sunroofOpen` | 天窗/车顶打开 | `300` | `{"value":"1"}` | 是 |
| `sunroofClose` | 天窗/车顶关闭 | `300` | `{"value":"0"}` | 是 |
| `ac` | 空调自定义 | `170` | 需传入完整 `stateJson` | 是 |
| `acOn` | 空调开启 | `170` | `{"operate":"manual","temperature":"24","windlevel":"3","mode":"cold","circle":"in","wshld":"0","position":"all"}` | 是 |
| `acOff` | 空调关闭 | `170` | `{"operate":"off","temperature":"24","windlevel":"3","mode":"nohotcold","circle":"out","wshld":"0","position":"all"}` | 是 |
| `quickCool` | 极速降温 | `170` | `{"operate":"manual","temperature":"18","windlevel":"7","mode":"cold","circle":"in","wshld":"0","position":"all"}` | 是 |
| `quickHeat` | 极速升温 | `170` | `{"operate":"manual","temperature":"32","windlevel":"7","mode":"hot","circle":"in","wshld":"0","position":"all"}` | 是 |
| `windshieldDefrost` | 前挡除雾 | `170` | `{"operate":"manual","temperature":"32","windlevel":"7","mode":"hot","circle":"in","wshld":"1","position":"all"}` | 是 |
| `custom` | 自定义命令 | 需指定 | 需传入 `cmdid` 和 `stateJson` | 是 |

---

## 6. 车窗控制特殊规则

车窗命令 `cmdid=230` 的值与车型相关：

| 车型家族 | close | vent | open |
|----------|-------|------|------|
| `T03` | `0` | `20` | `50` |
| `S01` | `10` | `8` | `0` |
| 其他 | `0` | `2` | `5` |

**S01 特别说明**：
- S01 车窗值为 `0–10` 的位置刻度，仅两个前窗参与远控
- `windowOpen=0` 最大打开
- `windowVent=8` 微开
- `windowClose=10` 关闭
- 自定义 `window --value <0..10>` 可用于中间位置
- 后窗开度在该车上始终为 `0`

MCP 参数兼容规则：
- `windowPosition` 整数参数优先级高于旧版 `value`
- 两者同时提供时以 `windowPosition` 为准

---

## 7. 预约类命令特殊路由

以下 `cmdid` 属于预约类命令，会使用不同的接口路径：

```js
export const appointmentCmdIds = new Set(['161', '171', '361', '392']);
```

- 预约类命令发送到：`/carownerservice/v3/api/appremotectl/appointment`
- 普通命令发送到：`/app/app-control-service/v3/api/appremotectl`

---

## 8. 安全条件汇总

### 8.1 真实控车三条件

`leap_control_send` 必须同时满足：

| 条件 | 位置 | 说明 |
|------|------|------|
| `LEAP_ALLOW_WRITE=1` | MCP 服务启动环境变量 | SDK 初始化时读取 |
| `allowWrite=true` | tool 参数 | 每次调用时传入 |
| `confirm=true` | tool 参数 | 每次调用时传入 |

### 8.2 操作密码规则

- 所有真实控车命令都需要操作密码
- 可通过 `LEAP_OPERATION_PASSWORD` 环境变量配置（MCP 启动时读取）
- 也可通过 tool 参数 `operationPassword` 传入
- 参数优先级高于环境变量
- 配置 `LEAP_OPERATION_PASSWORD` 后，MCP 会从 tool schema 中隐藏 `operationPassword` 字段，避免客户端重复询问

### 8.3 操作密码加密方式

SDK 使用旧 token 按 App 规则加密操作密码为 `oppwd`：
- key = `shortMd5(oldAuth.token.slice(0, 32))`
- iv = `shortMd5(oldAuth.token.slice(32, 64))`
- 算法：`AES-128-CBC`

### 8.4 Token 自动续期

| Token 类型 | 续期条件 | 续期接口 |
|------------|----------|----------|
| 新网关 access token | 剩余不足 5 分钟 | `POST /base/base-user/token/v1/refresh` |
| 旧 App token | 剩余不足 60 秒 或 `result=39` | `GET /app-user/appuseroperate/getnewtoken` |

旧 token 续期需要 `phone`（session 中已有或参数传入）。

---

## 9. 环境变量

| 环境变量 | 说明 | 默认值 |
|----------|------|--------|
| `LEAP_SESSION_FILE` | session 文件路径 | `./.leap-session.json` |
| `LEAP_ALLOW_WRITE` | 允许真实控车 | `0`（未设置） |
| `LEAP_OPERATION_PASSWORD` | 操作密码；配置后隐藏 tool schema 中的 `operationPassword` | 空 |
| `LEAP_DEVICE_ID` | 设备 ID；**仅首次登录/新 session 时指定** | 随机 UUID |
| `LEAP_APP_VERSION` | App 版本号 | `1.22.87` |
| `LEAP_SUB_VERSION` | App 子版本号 | `3.19.2-2` |

---

## 10. MCP 服务配置示例

```json
{
  "mcpServers": {
    "leapmotor-cn": {
      "command": "npx",
      "args": ["--yes", "--package", "leap-cn-mcp", "leap-cn-mcp"],
      "env": {
        "LEAP_SESSION_FILE": "/absolute/path/to/.leap-session.json",
        "LEAP_OPERATION_PASSWORD": "<操作密码>"
      }
    }
  }
}
```

---

## 11. 已知限制和注意事项

1. **不支持哨兵模式**：当前 MCP tools 中没有哨兵模式、摄像头、行车记录、停车监控相关命令。
2. **不支持能耗查询独立接口**：能耗数据通过 `leap_mileage` 返回，或通过 `leap_state` 中的 `fieldDefinitions` 获取实时信号。
3. **位置接口说明**：`leap_location` 使用旧 chassis 端点；实时状态 `leap_state` 中的经纬度可能更准确。
4. **deviceId 敏感性**：更换 `deviceId` 后车辆列表/实时状态仍正常，但旧 `carownerservice` 里程接口可能返回 `result=39`。应重新短信登录，不要复用旧 session。
5. **置信度标记**：`leap_state` 返回的 `fieldDefinitions` 包含 `confidence` 字段，`unknown` 字段不得自行猜测枚举或单位。
6. **车窗 S01 特殊值**：S01 关闭车窗为 `value=10`，与其他车型不同；自定义 `windowPosition` 范围 `0-10`。
7. **预约命令路由**：`cmdid` 为 `161`、`171`、`361`、`392` 时使用预约接口路径。

---

## 12. 接口 Host

| Host 类型 | 地址 |
|-----------|------|
| 旧 App 服务 | `https://appuser.leapmotor.cn` |
| 新网关 | `https://app-gw-global-master.leapmotor.com` |
| 信号服务 | 根据 `appRegion` 路由动态决定 |
| 预约服务 | 根据 `appRegion` 或 `appCenter` 路由动态决定 |
