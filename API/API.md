# 零跑智控（LeapAuto）API 接口与协议规范文档

> **版本**：v3.6.8  
> **文档属性**：零跑车联网底层协议完整权威技术白皮书（Single Source of Truth）。  
> **适用范围**：零跑中国区全系车型（C16、C11、C01、T03，涵盖纯电 EV 与增程式 REV）。  
> **源码基准**：严格对应 `app/src/main/java/com/leapauto/app/LeapmotorApi.kt`、`Models.kt`、`SignalTable.kt`、`Crypto.kt`。

---

## 目录
1. [全局架构与主机域设计](#一全局架构与主机域设计)
2. [底层密码学算法与签名规范](#二底层密码学算法与签名规范)
3. [用户认证与会话生命周期 API](#三用户认证与会话生命周期-api)
4. [车辆元数据与 3D 渲染资产 API](#四车辆元数据与-3d-渲染资产-api)
5. [车辆实时遥测与 signalMap 权威字典](#五车辆实时遥测与-signalmap-权威字典)
6. [远程控车指令全集与轮询协议](#六远程控车指令全集与轮询协议)
7. [高级车控与特色车主服务 API](#七高级车控与特色车主服务-api)
8. [行驶里程与能耗数据分析 API](#八行驶里程与能耗数据分析-api)
9. [蓝牙钥匙协议体系（BLE Key）](#九蓝牙钥匙协议体系ble-key)
10. [第三方集成 Web API](#十第三方集成-web-api)
11. [状态码与异常策略速查表](#十一状态码与异常策略速查表)

---

## 一、全局架构与主机域设计

零跑车联网采用“微服务架构 + 动态区域网关”的双层设计，客户端按业务属性与不同网关通信：

```text
                               ┌────────────────────────────────────────────────┐
                               │             零跑车联网云端微服务                │
                               └────────────────────────────────────────────────┘
                                  ▲              ▲                 ▲
                                  │              │                 │
         旧版用户中心 (appuser)    │              │                 │ 动态专属网关 (appRegion)
    ┌─────────────────────────────┴─┐            │                 └──────────────────────────────┐
    │ 短信发送 / 短信登录 / Token 续期 │            │ 新网关中心 (global-master)                    │ 车况查询 / 远程控车 / 充电控制
    │ 域名: appuser.leapmotor.cn    │            │ 凭据交换 / 车辆列表 / 路由获取                 │ 域名: 由 getCarRoute 动态下发
    └───────────────────────────────┘            │ 域名: app-gw-global-master.leapmotor.com     │ (如: app-gw-global-master)
                                                 └──────────────────────────────────────────────┘
```

### 1.1 主机域清单

| 标识 | 基础 URL | 用途说明 | 认证与加密特征 |
| :--- | :--- | :--- | :--- |
| `APP_USER_HOST` | `https://appuser.leapmotor.cn` | 验证码下发、短信登录、旧 Token 续期 | RSA PKCS#1v1.5 + MD5 截断签名 |
| `GLOBAL_HOST` | `https://app-gw-global-master.leapmotor.com` | 新网关登录交换、车辆列表、路由查询、3D资产元数据 | HMAC-SHA256 签名 (`x-api-signature-version=2.0`) |
| `DRIVING_RECORD_HOST` | `https://appgateway.leapmotor.com` | 行驶能耗明细、周能耗排行、FOTA 查询、操作密码校验 | 混编签名 (`oldSignedParams` + 网关头) |
| `IOV_API_HOST` | `https://iov-api.leapmotor.com` | 驻车实景环视照片元数据查询 | 旧签名参数 + 网关身份头 |
| `appRegion` (动态) | `getCarRoute.appRegion` | 实时车况信号查询、远程控车指令、健康充电设置 | 依车辆所属大区动态决定（通常与 GLOBAL_HOST 相同） |
| `appCenter` (动态) | `getCarRoute.appCenter` | 蓝牙钥匙证书同步、预约充电备用路由 | 依车辆所属中心动态下发 |

---

## 二、底层密码学算法与签名规范

### 2.1 手机号 RSA 加密规范
* **算法**: `RSA/ECB/PKCS1Padding`
* **公钥常数 (X.509 ASN.1 Base64)**:
  ```text
  MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQDHUIQKhkwNqJFTZPe98mC1lmpbY9r/+7PEWZg8ebqYXT3sumKRaQ0zcoTx42x0iybmCRXy4CcZrgGAbwKzwqwNw0rFquJ6c7mgQA6k3lZU3p96qBlzK7DSkoFR6mO9pjcd2hlJ8wH+IwI5b8IWWZhwVN/4cM7npG0S0zeRn3soEwIDAQAB
  ```
* **输出格式**: 加密后的字节流转换为 **URL-Safe Base64** 编码，并彻底剔除末尾的 `=` 填充符（`Base64.URL_SAFE or Base64.NO_WRAP`，`trimEnd('=')`）。
* **代码实现**: `Crypto.rsaEncryptPhone(phone: String): String`

### 2.2 旧链路 MD5 截断签名（`oldSignedParams`）
用于 `appuser.leapmotor.cn` 与 `appgateway.leapmotor.com` 的请求：
1. **参数字典集合**:
   包含业务参数，以及自动附加的公共安全参数：
   - `timespan`: 当前时间戳毫秒数（部分特定接口不带）；
   - `nonce`: `10000..10000000` 随机数；
   - `deviceID`: 32 位去横杠设备 UUID；
   - `token` 或 `refreshtoken`: 当前有效会话凭证。
2. **字典排序拼接**:
   所有键值非 null 的参数（空字符串保留参与签名），按键名 **ASCII 升序** 排序，拼接为纯文本串：
   $$\text{signBase} = \sum_{k \in \text{sortedKeys}} k + v$$
3. **哈希与截断**:
   $$\text{signStr} = \text{MD5}(\text{signBase})[8..24] \quad (\text{取第 8 到第 23 位的 16 位大写 Hex})$$
4. **清理与输出**:
   从请求参数字典中剔除明文 `token` / `refreshtoken`，存入计算出的 `signStr` 输出。
* **代码实现**: `LeapmotorApi.oldSignedParams()`

### 2.3 新网关 HMAC-SHA256 签名机制（`newGatewayHeaders`）
用于所有微服务 API（`x-api-signature-version=2.0`）：
1. **`signKey` 派生算法**:
   - 网关登录成功时下发 JWT `accessToken` 及 `signParam: {"r2": "...", "r3": "..."}`；
   - 提取 `accessToken` 的第 3 段（Base64URL 签名段）解码为字节数组 `tokenTail`；
   - 解码 `Base64.decode(r2)` 与 `Base64.decode(r3)`；
   - 取三者最短长度进行逐字节异或运算：
     $$\text{signKey}[i] = \text{tokenTail}[i] \oplus r2[i] \oplus r3[i]$$
2. **参与签名的头部与业务参数集合**:
   - 固定签名头集合：`acceptLanguage=zh-CN`、`channel=1`、`deviceId`、`deviceType=android`、`nonce`、`source=leapmotor`、`timestamp`、`version`；
   - 加上所有业务 Query / Form 参数（或 POST 请求体参数）。
3. **拼接与哈希**:
   - 键名 ASCII 升序排序，拼接为连续字符串 `signBase`；
   - **登录态**: $\text{sign} = \text{HmacSHA256}(\text{signKey}, \text{signBase})\text{.toHex()}$
   - **未登录态（如换网关凭证）**: $\text{sign} = \text{SHA256}(\text{signBase})\text{.toHex()}$
* **代码实现**: `LeapmotorApi.newGatewayHeaders()` / `Crypto.deriveSignKey()`

### 2.4 车控操作密码（PIN）AES-128-CBC 动态加解密
* **算法**: `AES/CBC/PKCS5Padding`
* **密钥与 IV 派生规则**:
  - 输入 Token: 优先使用网关 JWT `accessToken`，若不足 64 位则自动拼接自身回退；
  - $\text{Key} = \text{Crypto.md5Short}(\text{Token}[0..32])\text{.toByteArray(UTF-8)}$（16 字节）
  - $\text{IV}  = \text{Crypto.md5Short}(\text{Token}[32..64])\text{.toByteArray(UTF-8)}$（16 字节）
* **加密输出**: 4 位数字明文 PIN 加密后执行标准 Base64 编码，作为控车指令的 `oppwd` 字段。
* **代码实现**: `Crypto.encryptOperationPassword(password: String, token: String): String`

---

## 三、用户认证与会话生命周期 API

### 接口 1：发送短信验证码
* **URL**: `GET https://appuser.leapmotor.cn/app-user/applogin/compliance/sendmessagecode`
* **认证要求**: 无
* **Query 参数**:
  | 参数名 | 类型 | 必选 | 说明与示例 |
  | :--- | :--- | :--- | :--- |
  | `phoneNo` | String | 是 | 经 RSA PKCS#1v1.5 加密后的 Base64-URL 字符串 |
* **Request Headers**:
  `User-Agent: okhttp/4.9.3`、`APPPlatform: Android`、`APPVersion: 1.5.50`、`APPImei: {deviceId}`、`C-VERSIONS: APP`、`XFX-CDN-VRS: v4`
* **Response 示例**:
  ```json
  {
    "code": 0,
    "result": 0,
    "message": "请求成功",
    "data": null
  }
  ```
* **异常码**: `100115` 验证码发送过于频繁，`100116` 手机号格式非法。
* **客户端方法**: `LeapmotorApi.sendSms(phone: String)`

---

### 接口 2：手机号短信登录（含数美设备风控）
* **URL**: `POST https://appuser.leapmotor.cn/app-user/applogin/check_login_with_phone`
* **请求特征**: 业务参数走 URL Query，POST Body 保持为空。
* **Query 参数**:
  | 参数名 | 类型 | 必选 | 说明与示例 |
  | :--- | :--- | :--- | :--- |
  | `phoneNoCiphertext` | String | 是 | RSA 加密的手机号密文 |
  | `smsCode` | String | 是 | 6 位短信数字验证码（如 `"123456"`） |
  | `deviceID` | String | 是 | 设备唯一标识符（32 位 Hex） |
  | `smDeviceId` | String | 是 | 数美 SDK 本地采样的设备风控指纹字符串 |
  | `os` | String | 是 | 固定值 `"android"` |
  | `pageUrl` | String | 否 | 固定传空字符串 `""` |
  | `lot_number` | String | 条件 | 触发极验二次验证时的验证凭据 |
  | `captcha_output` | String | 条件 | 极验验证输出 token |
  | `pass_token` | String | 条件 | 极验通行凭据 |
  | `gen_time` | String | 条件 | 极验时间戳 |
* **Response 示例**:
  ```json
  {
    "code": 0,
    "result": 0,
    "message": "请求成功",
    "data": {
      "appLoginVO": {
        "accountId": "11987654321",
        "token": "d7a8c39e2b1f4a568c0d1e2f3a4b5c6d",
        "refreshToken": "e8b9c40f3c2a5b679d1e2f3a4b5c6d7e",
        "tokenExpired": 21600
      }
    }
  }
  ```
* **客户端方法**: `LeapmotorApi.loginWithSms(...)`

---

### 接口 3：旧版 Token 自动续期
* **URL**: `GET https://appuser.leapmotor.cn/app-user/appuseroperate/getnewtoken`
* **Request Headers**:
  `XFX-CDN-CROSS-NODE: {oldToken}`、`XFX-CDN-CROSS-REFRESH-NODE: {refreshToken}`
* **Query 参数**: `timespan`, `nonce`, `deviceID`, `refreshtoken`, `accountId`, `accountNumber`（RSA加密手机号）, `signStr`（MD5截断签名）。
* **Response 示例**:
  ```json
  {
    "result": 0,
    "data": {
      "token": "new_token_string...",
      "tokenExpired": 21600
    }
  }
  ```
* **客户端方法**: `LeapmotorApi.refreshOldToken()`

---

### 接口 4：换取新网关凭证（Gateway Exchange）
* **URL**: `POST https://app-gw-global-master.leapmotor.com/base/base-user/account/v1/login`
* **Request Headers**: 新网关公共头（`needLogin = false`，`sign = SHA256(signBase)`）
* **Content-Type**: `application/json; charset=utf-8`
* **POST JSON Body**:
  ```json
  {
    "identifier": "11987654321",
    "identifierType": "1",
    "security": "d7a8c39e2b1f4a568c0d1e2f3a4b5c6d"
  }
  ```
* **Response 示例**:
  ```json
  {
    "code": 0,
    "data": {
      "accountId": "11987654321",
      "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
      "refreshToken": "d8e9f0a1b2c3d4e5f6a7b8c9d0e1f2a3",
      "tokenExpireTime": 1727088000000,
      "signParam": {
        "r2": "A1b2C3d4E5f6...",
        "r3": "G7h8I9j0K1l2..."
      }
    }
  }
  ```
* **客户端方法**: `LeapmotorApi.exchangeNewGateway()`

---

### 接口 5：新网关 Token 自动续期
* **URL**: `POST https://app-gw-global-master.leapmotor.com/base/base-user/token/v1/refresh`
* **Request Headers**: 新网关 HMAC-SHA256 签名头
* **POST JSON Body**: `{"refreshToken": "{gatewayRefreshToken}"}`
* **Response 示例**: 返回新 `accessToken`、新 `refreshToken` 与新 `signParam{r2, r3}`。
* **客户端方法**: `LeapmotorApi.refreshGatewayToken()`

---

### 接口 6：操作密码前置校验
* **URL**: `POST https://appgateway.leapmotor.com/carownerservice/v3/api/appoperate/verifyoperatepwdnew`
* **Request Headers**: 包含旧 Token 身份头与网关头
* **Form Body**:
  - `vin`: 车辆 VIN
  - `oprpwd`: 使用 AES-128-CBC 加密后的 4 位操作密码
* **Response 示例**:
  ```json
  {
    "code": 0,
    "result": 0,
    "message": "操作密码正确"
  }
  ```
* **异常处理**: 若返回 `code != 0`，抛出 `ApiException("操作密码错误")`，本地触发错误计数加一。
* **客户端方法**: `LeapmotorApi.verifyOperatePassword(opPassword: String): Boolean`

---

## 四、车辆元数据与 3D 渲染资产 API

### 接口 7：用户绑定车辆列表
* **URL**: `GET https://app-gw-global-master.leapmotor.com/app/app-global-service/v1/vehicle/list`
* **Request Headers**: 新网关 HMAC-SHA256 签名头
* **Response 示例**:
  ```json
  {
    "code": 0,
    "data": [
      {
        "vin": "LP1234567890ABCDE",
        "carType": "C16",
        "licensePlate": "沪A·D12345",
        "vehicleName": "我的 C16",
        "nickName": "小零",
        "isOwner": true,
        "funcConfig": {
          "HVAC": {
            "temperature": { "min": 16, "max": 32 },
            "fan": { "min": 1, "max": 7 }
          },
          "FRIDGE": { "supported": true }
        }
      }
    ]
  }
  ```
* **客户端方法**: `LeapmotorApi.listVehicles(): List<Vehicle>`

---

### 接口 8：车辆动态路由查找
* **URL**: `GET https://app-gw-global-master.leapmotor.com/app/app-global-service/v1/vehicle/getCarRoute?vin={vin}`
* **Response 示例**:
  ```json
  {
    "code": 0,
    "data": {
      "appRegion": "https://app-gw-global-master.leapmotor.com",
      "appCenter": "https://app-gw-global-master.leapmotor.com"
    }
  }
  ```
* **客户端方法**: `LeapmotorApi.getCarRoute(): RouteData`

---

### 接口 9：车辆外观图片与 3D 车模元数据
* **URL**: `POST https://app-gw-global-master.leapmotor.com/carownerservice/vehicle/v1/carpicture/key`
* **Form Body**: `vin={vin}&deviceID={deviceID}`
* **Response 示例**:
  ```json
  {
    "code": 0,
    "data": {
      "h5Key": "3D-702ef381-d7ed-49cd-8d69-fad6f5f9f397",
      "srcKey": "3D-d3e0fbce-0755-441e-8381-7512fd49bc70",
      "modelType": 3,
      "shareBindUrl": "https://lp-carnet.oss-cn-hangzhou.aliyuncs.com/carModel3D/3D-2809d7c4.png",
      "modelParam": {
        "carType": "C16",
        "year": 2026,
        "carTypeCode": "630激光雷达智尊版 6座",
        "colorCode": 3
      }
    }
  }
  ```
* **客户端方法**: `LeapmotorApi.getVehiclePictureMeta(...)`

---

### 接口 10：3D 车模 H5 运行时与专属资产包下载
* **URL**: `http://lp-carnet.oss-cn-hangzhou.aliyuncs.com/carModel3D/{KEY}.zip`
* **下载与合并架构**:
  1. 通过 `downloadCarModelPackage(h5Key)` 下载通用 Three.js 驱动外壳，解压释放 `index.html`；
  2. 通过 `downloadCarModelPackage(srcKey)` 下载该车型的高精模型、四轮贴图、车灯着色器，合并覆盖；
  3. 写入 `.ready` 标识，`CarModel3DView` 在 WebView 内部启动 `index.html` 渲染。
* **客户端方法**: `CarModel3DManager.syncModelPackage(...)`

---

## 五、车辆实时遥测与 signalMap 权威字典

### 接口 11：车辆实时车况查询（原始 signalMap）
* **URL**: `POST {appRegion}/app/app-signal-service/signal/info/query`
* **Request Headers**: 新网关 HMAC-SHA256 签名头
* **POST JSON Body**: `{"vin": "{vin}"}`
* **Response 结构**:
  ```json
  {
    "code": 0,
    "data": {
      "signalMap": {
        "1204": "85",
        "100003": "85.2",
        "3257": "530",
        "1149": "0",
        "1318": "12845.6",
        "1319": "0",
        "1010": "P",
        "1944": "0",
        "1298": "1",
        "1938": "0",
        "1349": "22.5",
        "2646": "2.4",
        "2653": "2.4",
        "2660": "2.4",
        "2667": "2.4",
        "3724": "120.123456",
        "3725": "30.123456"
      }
    }
  }
  ```
* **客户端方法**: `LeapmotorApi.getVehicleStateRaw()` / `getVehicleState()`

---

### 5.1 `signalMap` 数字信号权威对照字典表

| 业务域 | 信号 ID | 解码命名标识 | 取值类型 | 信号具体取值规范与业务含义 |
| :--- | :--- | :--- | :--- | :--- |
| **电量与续航** | `1204` | `soc` | Int (0..100) | 动力电池剩余电量百分比（纯电/增程通用） |
| | `100003` | `preciseSoc` | Float | 高精电量（如 `85.4`），用于仪表与小组件 |
| | `3235` | `fuelSoc` | Int (0..100) | 增程车型燃油剩余百分比 |
| | `3257` | `electricRangeStandard` | Int (km) | CLTC 标准纯电剩余续航 |
| | `3260` | `expectedMileage` | Int (km) | WLTC 动态纯电剩余续航 |
| | `3256` | `fuelRangeStandard` | Int (km) | CLTC 标准燃油剩余续航（增程） |
| | `3259` | `fuelRangeDynamic` | Int (km) | WLTC 动态燃油剩余续航（增程） |
| | `3258` | `combinedRangeStandard` | Int (km) | CLTC 综合总续航（增程） |
| | `3261` | `combinedRangeDynamic` | Int (km) | WLTC 动态综合总续航（增程） |
| | `2188` | `liveRemainingRange` | Int (km) | 车机仪表盘当前主显示的剩余续航 |
| | `3262` | `rangeMode` | String | 续航模式：`"0"`=标准续航模式，`"1"`=动态续航模式 |
| **充电状态** | `1149` | `chargeState` | Int | `1`=充电中, `2`=充电已完成, `3`=充电故障, `0`=未充电/插枪未充 |
| | `1200` | `chargeRemainTime` | Int (分钟) | 预计充满剩余倒计时（分） |
| | `1177` | `batteryVoltage` | Double (V) | 动力电池包总端电压 |
| | `1178` | `batteryCurrent` | Double (A) | 充放电实时电流（负值代表电网输入充电电流） |
| | `1197` | `dcInputFastCharge` | Int / Boolean| `1`/`true`=直流超级快充中，`0`/`false`=交流慢充 |
| | `47` | `acInputSlowCharge` | Int / Boolean| 交流充电桩充电枪插入物理到位状态 |
| | `3736` | `chargeCompleted` | Int / Boolean| 动力电池包充满自动截断保护标志 |
| | `48` | `healthyChargeEnabled` | Int / Boolean| 健康充电模式是否开启（限制最高充至 80%~90%） |
| | `3737` | `chargeScheduleCancelledOnce` | Int | 预约充电临时单次跳过标志 |
| **行驶与动力** | `1318` | `totalMileage` | Double (km) | 整车累计行驶里程 |
| | `1319` | `speed` | Double (km/h)| 当前车辆行驶速度（用于车身 3D 车轮旋转判定） |
| | `1010` | `gearStatus` | String | 车辆当前挂挡：`P`、`R`、`N`、`D`（用于刹车灯/行车判定） |
| | `1944` | `vehicleState` | Int | 整车运行状态：`1`=Ready/行车状态，`0`=下电休眠 |
| | `1480` | `parkingBrakeState` | Int | 电子驻车制动 EPB：`1`=手刹已拉起，`0`=手刹已释放 |
| | `6048` | `speedLimit` | Int (km/h) | 道路限速抓取数值 |
| | `6047` | `speedLimitUnit` | String | 限速单位（`km/h`） |
| | `12054` | `speedLimitActive` | Int / Boolean| 限速预警激活状态 |
| **车辆位置** | `2` / `3724` | `longitude` | Double | 车辆当前 GPS 经度（GCJ-02 火星坐标系） |
| | `3` / `3725` | `latitude` | Double | 车辆当前 GPS 纬度（GCJ-02 火星坐标系） |
| **车门车锁** | `1298` | `driverDoorLockStatus` | Int | 主车门锁状态：`0`=已解锁，`1`=已上锁锁定 |
| | `1277` | `lbcmDriverDoorStatus` | Int | 主驾驶左前门：`0`=关闭，`1`=开启敞开 |
| | `1278` | `rbcmDriverDoorStatus` | Int | 副驾驶右前门：`0`=关闭，`1`=开启敞开 |
| | `1279` | `lbcmLeftRearDoorStatus` | Int | 左后排车门：`0`=关闭，`1`=开启敞开 |
| | `1280` | `rbcmRightRearDoorStatus`| Int | 右后排车门：`0`=关闭，`1`=开启敞开 |
| | `1281` | `bbcmBackDoorStatus` | Int | 电动后备箱尾门：`0`=关闭，`1`=开启未关闭 |
| **车窗天幕** | `1693` | `driverWindowStatus` | Int | 左前车窗状态（`0`=全关，`1`=开启） |
| | `1694` | `rightFrontWindowStatus`| Int | 右前车窗状态 |
| | `1695` | `leftRearWindowStatus` | Int | 左后车窗状态 |
| | `1696` | `rightRearWindowStatus` | Int | 右后车窗状态 |
| | `1724` | `roofOpening` | Int (0..100) | 全景电动天幕/遮阳帘开度百分比 |
| **座舱空调** | `1938` | `acSwitch` | Int | 空调总电源状态：`0`=关闭，`1`=运行中 |
| | `2183` | `acSetting` | Double (℃) | 主驾设定目标温度（如 `24.0`） |
| | `2184` | `acSettingRight` | Double (℃) | 副驾设定目标温度 |
| | `1349` | `interiorTemp` | String/Double| 座舱内当前实测温度（℃） |
| | `1943` | `recirculationMode` | Int | 空调循环方式：`0`=外循环，`1`=内循环 |
| | `1941` | `acAirVolume` | Int (1..7) | 空调送风风量档位 |
| | `1945` | `windshieldDefrost` | Int | 前挡风玻璃加热除霜：`0`=关闭，`1`=开启 |
| | `1946` | `rearWindowHeating` | Int | 后风挡及外后视镜电加热除雾：`0`=关闭，`1`=开启 |
| | `3713` | `climateMode` | Int | 运行模式：`0`=自然通风，`1`=制冷降温，`2`=制热采暖 |
| | `2669` | `rapidCooling` | Int | 极速降温工作标志 |
| | `2681` | `rapidHeating` | Int | 极速升温工作标志 |
| **座椅舒适** | `2100` | `driverSeatHeating` | Int (0..3) | 主驾座椅加热：`0`=关，`1`=低档，`2`=中档，`3`=高档 |
| | `2101` | `driverSeatVentilation` | Int (0..3) | 主驾座椅通风：`0`=关，`1`=低档，`2`=中档，`3`=高档 |
| | `2118` | `passengerSeatHeating` | Int (0..3) | 副驾座椅加热：`0`=关，`1..3` 对应档位 |
| | `2119` | `passengerSeatVentilation`| Int (0..3) | 副驾座椅通风：`0`=关，`1..3` 对应档位 |
| | `1879` | `leftRearSeatHeating` | Int (0..3) | 二排左后座（主驾后）座椅加热档位 |
| | `3727` | `leftRearSeatVentilation`| Int (0..3) | 二排左后座座椅通风档位 |
| | `1880` | `rightRearSeatHeating` | Int (0..3) | 二排右后座（副驾后）座椅加热档位 |
| | `3728` | `rightRearSeatVentilation`| Int (0..3) | 二排右后座座椅通风档位 |
| | `1816` | `steeringWheelHeating` | Int (0..2) | 方向盘电加热档位：`0`=关，`1`=弱档，`2`=强档 |
| | `1624` | `steeringWheelHeaterMinutes`| Int | 方向盘加热持续运行剩余分钟数 |
| | `49` / `50`| `left/rightMirrorHeating`| Int (0..1) | 左/右外后视镜加热状态 |
| **四轮胎压** | `2646` | `leftFrontTirePressure` | Double (bar) | 左前轮胎压数值（如 `2.4 bar`） |
| | `2653` | `rightFrontTirePressure`| Double (bar) | 右前轮胎压数值 |
| | `2660` | `leftRearTirePressure` | Double (bar) | 左后轮胎压数值 |
| | `2667` | `rightRearTirePressure` | Double (bar) | 右后轮胎压数值 |
| | `2641/2648/2662/2655` | `...TirePressureState` | Int | 胎压状态：`0`=正常, `1`=欠压告警, `2`=过压告警 |
| **车载冰箱** | `10709` | `fridgeSwitch` | Int (0/1) | 冰箱电源开关：`0`=已关机，`1`=开机运行 |
| | `10708` | `fridgeMode` | Int (0/1) | 运行模式：`0`=冷藏制冷，`1`=保温暖食（恒定 50℃） |
| | `10707` | `fridgeTargetTemp` | Int (℃) | 制冷目标设定温度：范围 `-6℃ ~ 15℃` |
| | `10711` | `fridgeStyle` | Int (0/1) | 制冷风格：`0`=标准节能，`1`=急速强劲 |
| | `10712` | `fridgeFault` | Int | 冰箱硬件自检故障码（`0`=正常运行） |
| | `11190` | `fridgeParkSwitch` | Int (0/1) | 离车持续保温开关：`0`=锁车随关，`1`=离车持续工作 |
| | `11189` | `fridgeParkDurationHours`| Int | 离车工作持续时间（1..24 小时） |
| | `11191` | `fridgeParkCycles` | Int (0/1) | 离车保温周期模式：`0`=仅本次离车，`1`=每次离车 |
| | `11260` | `fridgeParkEndTime` | Long (秒) | 离车保温截止时间点秒级时间戳 |
| **热管理安防**| `1182` | `minBatteryTemp` | Int (℃) | 动力电池电芯最低实测温度 |
| | `1186` | `batteryThermalRequest` | Int | 电池加热请求状态：`4`=预热运行中，`0`=未激活 |
| | `1255` | `vehicleSecurityActive` | Int / Boolean| 原厂防盗电子围栏设防状态 |
| | `3636` | `sentryMode` | Int | 哨兵模式警戒状态：`0`=已关闭，`1`=开启警戒守护 |

---

## 六、远程控车指令全集与轮询协议

### 6.1 指令发送核心契约
* **URL**: `POST {appRegion}/app/app-control-service/v3/api/appremotectl`
* **Content-Type**: `application/x-www-form-urlencoded; charset=utf-8`
* **Form Body**:
  ```text
  cmdid={cmdid}&carvin={vin}&state={stateJson}&oppwd={encryptedPin}&timespan={ts}&nonce={nonce}&deviceID={deviceId}&signStr={sign}
  ```
* **业务响应**: `{"result": 0, "data": "<msgID>"}`，其中 `data` 即为轮询用的 `msgID`。

### 6.2 指令结果轮询机制
* **URL**: `GET {appRegion}/app/app-control-service/v3/api/appremotectl/query?msgID={msgID}`
* **轮询退避算法**:
  1. 首次下发后，休眠等待 1000 毫秒；
  2. 随后每隔 500 毫秒发起一次轮询；
  3. App 内部最长等待 24 秒；桌面小组件最长等待 12 秒；
  4. 轮询返回 `{"result": 0}` 时表示车机执行成功，随后发起 `getVehicleState()` 遥测刷新二次确认。

---

### 6.3 35+ 远程车控指令集权威矩阵表

| 指令名称 (`name`) | `cmdid` | `state` 参数体完整 JSON 示例 | 业务说明 | 交互安全门禁 |
| :--- | :--- | :--- | :--- | :--- |
| `lock` | `110` | `{"value":"lock"}` | 远程车门全局上锁 | 4位操作密码 |
| `unlock` | `110` | `{"value":"unlock"}` | 远程车门全局解锁 | 4位操作密码 + 生物识别确认 |
| `trunkOpen` | `130` | `{"value":"true"}` | 开启电动后备箱 | 4位操作密码 + 1.2s长按蓄力 |
| `trunkClose` | `130` | `{"value":"false"}` | 关闭电动后备箱 | 4位操作密码 |
| `frunkOpen` | `131` | `{"value":"100"}` | 开启前备箱（D19车型支持） | 4位操作密码 |
| `frunkClose` | `131` | `{"value":"0"}` | 关闭前备箱 | 4位操作密码 |
| `windowOpen` | `230` | `{"value":"5"}` | 车窗半开（四窗下降 50%） | 4位操作密码 |
| `windowVent` | `230` | `{"value":"2"}` | 车窗微开通风（四窗下降留缝） | 4位操作密码 |
| `windowClose` | `230` | `{"value":"0"}` | 车窗一键全关升顶 | 4位操作密码 |
| `sunshadeOpen` | `240` | `{"value":"10"}` | 全景电动天幕遮阳帘全开 | 4位操作密码 |
| `sunshadeClose` | `240` | `{"value":"0"}` | 全景电动天幕遮阳帘全关 | 4位操作密码 |
| `horn` | `120` | `{"value":"true"}` | 鸣笛闪灯寻车 | 4位操作密码 |
| `batteryPreheat` | `160` | `{"value":"ptcon"}` | 开启电池电芯加热预热 | 4位操作密码 |
| `batteryPreheatOff`| `160` | `{"value":"ptcoff"}` | 停止电池加热预热 | 4位操作密码 |
| `acOn` | `170` | `{"operate":"auto","temperature":"24","windlevel":"3","mode":"cold","circle":"in","wshld":"1","position":"all"}` | 开启空调（自动温控 24℃ 制冷内循环） | 4位操作密码 |
| `acOff` | `170` | `{"operate":"off","temperature":"24","windlevel":"3","mode":"nohotcold","circle":"in","wshld":"1","position":"all"}` | 关闭座舱空调系统 | 4位操作密码 |
| `quickCool` | `170` | `{"operate":"manual","temperature":"18","windlevel":"7","mode":"cold","circle":"in","wshld":"0","position":"all"}` | 极速降温（18℃最大风内循环） | 4位操作密码 |
| `quickHeat` | `170` | `{"operate":"manual","temperature":"32","windlevel":"7","mode":"hot","circle":"in","wshld":"0","position":"all"}` | 极速升温（32℃暖气最大风） | 4位操作密码 |
| `defrost` | `170` | `{"operate":"manual","temperature":"24","windlevel":"5","mode":"cold","circle":"out","wshld":"1","position":"wshld"}` | 前挡风玻璃强效加热除霜 | 4位操作密码 |
| `deodorize` | `170` | `{"operate":"manual","temperature":"24","windlevel":"7","mode":"nohotcold","circle":"out","wshld":"0","position":"all"}` | 快速外循环空气净化除味 | 4位操作密码 |
| `customAc` | `170` | `{"operate":"manual","temperature":"22","windlevel":"4","mode":"cold","circle":"in","wshld":"0","position":"all"}` | 空调全参数多维定制控制 | 4位操作密码 |
| `driverSeatHeating_1..3` | `301` | `{"position":"left_front","level":"3"}` | 主驾座椅加热档位控制 | 4位操作密码 |
| `passengerSeatHeating_1..3` | `301` | `{"position":"right_front","level":"3"}`| 副驾座椅加热档位控制 | 4位操作密码 |
| `leftRearSeatHeating_1..3` | `301` | `{"position":"left_rear","level":"3"}` | 二排左后座加热档位控制 | 4位操作密码 |
| `rightRearSeatHeating_1..3` | `301` | `{"position":"right_rear","level":"3"}` | 二排右后座加热档位控制 | 4位操作密码 |
| `driverSeatVentilation_1..3` | `370` | `{"position":"left_front","level":"3"}` | 主驾座椅通风档位控制 | 4位操作密码 |
| `passengerSeatVentilation_1..3` | `370` | `{"position":"right_front","level":"3"}`| 副驾座椅通风档位控制 | 4位操作密码 |
| `leftRearSeatVentilation_1..3` | `370` | `{"position":"left_rear","level":"3"}` | 二排左后座通风档位控制 | 4位操作密码 |
| `rightRearSeatVentilation_1..3` | `370` | `{"position":"right_rear","level":"3"}` | 二排右后座通风档位控制 | 4位操作密码 |
| `steeringWheelHeating_1..2` | `320` | `{"level":"2"}` | 方向盘电加热档位控制（1弱/2强/0关） | 4位操作密码 |
| `rearviewMirrorHeating_on/off` | `440` | `{"value":"2"}` | 后视镜电加热开关（2开/1关） | 4位操作密码 |
| `fridgeOn` | `500` | `{"cycles":"1","duration":3600,"enable":1,"mode":"cold","parkEnable":0,"style":"normal","temp":4,"value":"false"}` | 车载冰箱开机制冷（默认4℃） | 4位操作密码 |
| `fridgeOff` | `500` | `{"cycles":"1","duration":3600,"enable":0,"mode":"cold","parkEnable":0,"style":"normal","temp":4,"value":"false"}` | 车载冰箱关机 | 4位操作密码 |
| `fridgeControl` | `500` | `{"cycles":"0","duration":7200,"enable":1,"mode":"cold","parkEnable":1,"style":"fast","temp":-2,"value":"false"}` | 冰箱全功能深度设置与离车保温 | 4位操作密码 |
| `sentryOn` | `400` | `{"operation":"on"}` | 开启原厂哨兵模式警戒 | 4位操作密码 |
| `sentryOff` | `400` | `{"operation":"off"}` | 关闭原厂哨兵模式 | 4位操作密码 |
| `startCharging` | `193` | `{"value":"start"}` | 远程启动即时充电 | 4位操作密码 |
| `stopCharging` | `193` | `{"value":"stop"}` | 远程停止当前充电 | 4位操作密码 |
| `unlockCharger` | `192` | `{"operation":"unlock"}` | 解锁充电枪物理锁止销 | 4位操作密码 |
| `fotaDownload` | `390` | `{"taskId":"{taskId}"}` | 触发车机开始下载新版固件包 | 4位操作密码 |
| `fotaInstall` | `391` | `{"taskId":"{taskId}"}` | 触发车机就地刷写安装固件 | 4位操作密码 |
| `fotaSchedule` | `392` | `{"taskId":"{taskId}","scheduleTime":"2026-09-24 03:00:00"}` | 预约车机在凌晨静默升级 | 4位操作密码 |

---

## 七、高级车控与特色车主服务 API

### 接口 12：驻车实景环视照片查询与解密渲染
* **URL**: `GET https://iov-api.leapmotor.com/carownerservice/v3/api/chassis/query?vin={vin}&timespan=...&nonce=...&deviceID=...&signStr=...`
* **Response 示例**:
  ```json
  {
    "code": 0,
    "result": 0,
    "data": {
      "fileUrl": "https://lp-iov-user-picture.oss-cn-hangzhou.aliyuncs.com/chassis/...",
      "uploadTime": 1727076800000
    }
  }
  ```
* **客户端下载与手势**:
  - `LeapmotorApi.downloadParkingPhotoBitmap()` 拉取图像流，解码为原图并缓存在内存；
  - 界面弹窗（`FullScreenImagePreviewDialog`）支持 `0.8x ~ 5.0x` 双指平移捏合缩放预览，左下角结合手机定位实时计算直线距车距离。

---

### 接口 13：车机 FOTA 固件升级查询与控制
* **URL**: `GET https://appgateway.leapmotor.com/carownerservice/v3/api/fota/getCurrentVersion?vin={vin}&...`
* **Response 示例**:
  ```json
  {
    "code": 0,
    "data": {
      "hasNewVersion": true,
      "currentVersion": "2.02.80",
      "targetVersion": "2.03.10",
      "releaseNotes": "1. 优化座舱空调控制逻辑\n2. 增强辅助驾驶感知能力",
      "status": 3,
      "taskId": "fota_task_987654321",
      "scheduleTime": "2026-09-24 02:00:00"
    }
  }
  ```
* **状态机说明 (`status`)**:
  `0`=已是最新版本, `1`=待下载, `2`=下载固件中, `3`=下载完成待安装, `4`=固件安装刷写中, `5`=升级完成。
* **重置状态机**: `POST {appRegion}/app/app-fota-service/v1/fota/resetStatus`（用于升级遇阻时释放锁）。
* **客户端方法**: `LeapmotorApi.getVehicleOtaInfo()` / `fotaResetStatus()`

---

### 接口 14：健康充电上限管理
* **设置充电上限**: `POST {appRegion}/carownerservice/v3/api/healthyCharging/control`
  - Body: `vin={vin}&carvin={vin}&state=1&targetSoc=80`（支持 50%~100% 自定义设定）。
* **查询策略推送状态**: `GET {appRegion}/carownerservice/v3/api/healthyCharging/queryPushState?vin={vin}&carvin={vin}`
* **客户端方法**: `LeapmotorApi.setHealthyCharging()` / `queryHealthyChargingPushState()`

---

### 接口 15：谷电预约充电计划（`cmdid=190`，三通道容灾）
* **指令 Payload**:
  ```json
  {
    "chargeEnable": 1,
    "chargesoc": 80,
    "circulation": 1,
    "cycles": "1,2,3,4,5,6,7",
    "starttime": "23:00",
    "endtime": "07:00",
    "recharge": 1
  }
  ```
* **三通道容灾路由机制**:
  1. 首选通道：`POST {appRegion}/app/app-control-service/v3/api/appremotectl`；
  2. 备选通道：`POST {appCenter}/carownerservice/v3/api/appremotectl/appointment`；
  3. 日程微服务保底通道：`POST {appRegion}/carownerservice/v3/api/schedule/operate`。
* **客户端方法**: `LeapmotorApi.setScheduledCharging(...)`

---

### 接口 16：预约电池预热计划（`cmdid=161`）
* **指令 Payload**:
  ```json
  {
    "controls": [
      {
        "on": "1",
        "set_id": "ptc_set_1727000000000",
        "start_time": "2026-09-24 07:30:00",
        "update_time": "1727000000000",
        "days": [1, 2, 3, 4, 5, 6, 7]
      }
    ]
  }
  ```
* **客户端方法**: `LeapmotorApi.setScheduledBatteryPreheat(...)`

---

## 八、行驶里程与能耗数据分析 API

### 接口 17：历史累计行驶里程与能耗明细
* **URL**: `GET https://appgateway.leapmotor.com/carownerservice/v3/api/drivingrecord/mileage/energy/detail`
* **Query 参数**: `begintime` (提车日0点秒级时间戳), `endtime` (当前秒级时间戳), `vin`, 旧MD5签名串。
* **业务数据**: 历史累计电耗与油耗、总里程、百公里综合能耗结构。
* **客户端方法**: `LeapmotorApi.getMileageEnergy()`

---

### 接口 18：近 7 日行驶里程明细（毫秒级时间戳）
* **URL**: `GET https://appgateway.leapmotor.com/carownerservice/v3/api/drivingrecord/mileage/energy/detail`
* **Query 参数**: `begintime` (7天前0点毫秒戳), `endtime` (当前毫秒戳), `vin`, 旧MD5签名串。
* **Response 示例**:
  ```json
  {
    "code": 0,
    "data": {
      "detail": [
        { "day": "2026-09-17", "accumulatedMileage": 34.5 },
        { "day": "2026-09-18", "accumulatedMileage": 52.1 },
        { "day": "2026-09-19", "accumulatedMileage": 12.8 },
        { "day": "2026-09-20", "accumulatedMileage": 48.0 },
        { "day": "2026-09-21", "accumulatedMileage": 65.2 },
        { "day": "2026-09-22", "accumulatedMileage": 30.7 },
        { "day": "2026-09-23", "accumulatedMileage": 41.3 }
      ]
    }
  }
  ```
* **客户端方法**: `LeapmotorApi.getRecentMileageEnergy()`

---

### 接口 19：近 6 周百公里能耗与同车型排行
* **URL**: `GET https://appgateway.leapmotor.com/carownerservice/v3/api/drivingrecord/getLastNweeks100kmECAndRank`
* **Query 参数**: `carvin={vin}`（**注意必须为 carvin**）+ 旧MD5签名串。
* **业务响应**: 近 6 周周能耗柱状图序列、同车型全国节油/电耗排行前百分比。
* **客户端方法**: `LeapmotorApi.getLastNWeeks100kmEcAndRank()`

---

## 九、蓝牙钥匙协议体系（BLE Key）

### 接口 20：蓝牙钥匙 X.509 证书拉取
* **URL**: `POST {appCenter}/carownerservice/v3/api/bluetoothkey/combine/syncBluetoothKeys`
* **表单参数**: `vin`, `timespan`, `nonce`, `deviceID`, `signStr`。
* **响应解析**: `data.certificate`，返回 X.509 DER 证书格式公钥与会话凭证，密钥类型 `keyType=0` 为 P-256，`keyType=1` 为纯国密 SM2。
* **客户端方法**: `LeapmotorApi.fetchBluetoothKeyCertificate()`

---

### 接口 21：车辆蓝牙广播配置与 MAC 查询
* **URL**: `POST https://appgateway.leapmotor.com/carownerservice/v3/api/vehicleinfo/commonConfig`
* **Form Body**: `vin={vin}&type=4`
* **返回数据**: 车辆蓝牙广播物理 MAC 地址、广播主次协议版本号。
* **客户端方法**: `LeapmotorApi.getBluetoothVehicleMetadata()`

---

### 接口 22：无感蓝牙解闭锁偏好云端同步
* **URL**: `POST https://app-gw-global-master.leapmotor.com/app/app-global-service/v3/api/commoninfo/transparent/conf/upload`
* **POST JSON Body**:
  ```json
  {
    "vin": "{vin}",
    "bleKeySwitch": "1",
    "bleKeyUnlock": "1",
    "bleKeyLock": "1",
    "bleKeyBtn": "0"
  }
  ```
* **客户端方法**: `LeapmotorApi.uploadBluetoothConfiguration(...)`

---

### 9.1 蓝牙 GATT 物理传输与加密控锁规范
* **GATT 服务 UUID**: `0000FFFE-0000-1000-8000-00805F9B34FB`
* **GATT 特征 UUID**: `0000FFF2-0000-1000-8000-00805F9B34FB`（具备 Write 与 Notify 属性）
* **会话交互流**:
  1. **广播扫描**: 过滤包含 Leapmotor 特征的 BLE 广播帧，比对车辆 MAC；
  2. **双向协商**: 手机与车机交换 16 字节真随机数，通过 ECDH 协商主密钥；
  3. **密文控车**: 组装 `cmdid=1`（锁控）数据包，使用协商密钥通过 AES-128-GCM / SM4 加密发送；
  4. **事件回执**: 车机通过 Notify 回传 `AA AC` 事件帧，验证成功则向用户提示“锁止成功”。
* **代码实现**: `BluetoothKeyController.kt` / `BleKeyProtocol.kt`

---

## 十、第三方集成 Web API

### 接口 23：高德地图逆地理编码 Web API
* **URL**: `GET https://restapi.amap.com/v3/geocode/regeo`
* **Query 参数**:
  | 参数名 | 类型 | 说明与示例 |
  | :--- | :--- | :--- |
  | `key` | String | 高德开放平台 Web 服务 Key（动态反混淆保护） |
  | `location` | String | 格式化经纬度 `"{longitude},{latitude}"`（6位小数，GCJ-02） |
  | `extensions` | String | 固定值 `"all"` |
  | `output` | String | 固定值 `"json"` |
* **Response 关键字段解析**:
  - `regeocode.formatted_address`: 标准中文路名及兴趣点（如 `"上海市闵行区申昆路1500号"`）；
  - `regeocode.addressComponent.city`: 城市名（如 `"上海市"`）；
  - `regeocode.addressComponent.adcode`: 6 位行政区划编码（如 `"310112"`，供天气接口直连）。
* **客户端方法**: `VehicleLocationGeocoder.reverseGeocode()`

---

### 接口 24：高德地图实况天气 Web API
* **URL**: `GET https://restapi.amap.com/v3/weather/weatherInfo`
* **Query 参数**:
  | 参数名 | 类型 | 说明与示例 |
  | :--- | :--- | :--- |
  | `key` | String | 高德开放平台 Web 服务 Key |
  | `city` | String | 车辆所在地的 6 位行政区划代码（由逆地理接口输出的 `adcode`） |
  | `extensions` | String | 固定值 `"base"`（查询实况） |
* **Response 关键字段解析**:
  - `lives[0].weather`: 实时天气现象（如 `"晴"`、`"多云"`、`"小雨"`、`"雾"`）；
  - `lives[0].temperature`: 实况室外温度（如 `"22"` ℃）；
  - `lives[0].winddirection`: 风向描述（如 `"西北"`）；
  - `lives[0].windpower`: 风力级别（如 `"≤3"` 级）；
  - `lives[0].humidity`: 空气相对湿度百分比（如 `"58"` %）；
  - `lives[0].reporttime`: 观测台发布时间。
* **业务联动**: 驱动驻车详情弹窗的一体化微晶气象卡片，并通过 `CarWashRecommendationPolicy` 智能测算洗车适宜指数。
* **缓存优化**: 内存持有 45 分钟 TTL 缓存，避免重复冗余请求。
* **客户端方法**: `AmapWeatherService.getLiveWeather()`

---

### 接口 25：蒲公英应用版本更新检测与静默下载
* **HTML 检测 URL**: `GET https://www.pgyer.com/lingpaozhikong`
  - 零鉴权直接解析官方发布页的 `<title>` 与版本节点，提取最新 `versionName`、`buildNumber` 与更新日志。
* **OpenAPI v2 直链获取**: `POST https://www.pgyer.com/apiv2/app/install`
  - 参数: `_api_key`, `buildKey`；
  - 提取高带宽 CDN 直链与临时 Token。
* **断点续传与安装**:
  - 流式下载至 `context.cacheDir/updates/零跑智控-{version}.apk`；
  - 通过 `AppUpdateInstaller.triggerInstall()` 调用 Android `FileProvider` 发起 `ACTION_INSTALL_PACKAGE`。
* **客户端方法**: `VersionUpdate.checkForUpdate()` / `AppUpdateInstaller.downloadApk()`

---

## 十一、状态码与异常策略速查表

| 返回码 / 判定标志 | 归属服务 | 业务语义 | 客户端自动容灾与恢复策略 |
| :--- | :--- | :--- | :--- |
| `result = 0` / `code = 0` | 全链路 | 操作完全成功 | 正常进入后续业务与 UI 驱动 |
| `100115` | `app-user` | 短信验证码错误或已失效 | 提示用户并重置验证码输入框 |
| `100117` | `app-user` | 短信发送频次超限 | 启动 60 秒倒计时冷却，拦截重复点击 |
| `result = 39` | `app-user` | 旧版 Token 过期失效 | 自动触发 `refreshOldToken()`，重试一次既有请求 |
| `HTTP 401 / 403` | 新网关 | 网关访问令牌失效或签名错误 | 自动触发 `refreshAccessTokenWithFallback()` 续期，续期失败返回登录 |
| `"操作频繁"` | 控车服务 | 车机或云端限流保护 | 提示车主稍后再试，不盲目高频重发 |
| `10712 != 0` | 车载冰箱 | 冰箱硬件保护或通信故障 | 详情页标红警告，自动展示故障码排查指引 |
| `code = 9` (BLE) | 蓝牙车控 | 车辆拒绝当前蓝牙认证凭证 | 提示钥匙已失效，引导车主重新拉取云端证书同步 |
