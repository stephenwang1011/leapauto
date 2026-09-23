# 零跑智控（LeapAuto）API 接口与协议规范文档

> **版本**：v3.6.8  
> **文档定位**：零跑智控 Android 客户端完整底层接口、协议签名、遥测信号映射与指令集规范（Single Source of Truth）。  
> **验证基准**：零跑中国官方车联网云端接口，在 2026 款 C16（国内版纯电/增程）及 C11/C01/T03 全系车型上验证可用。

---

## 目录
1. [主机域架构与动态路由](#一主机域架构与动态路由)
2. [登录、认证与签名体系](#二登录认证与签名体系)
3. [车辆信息与 3D 渲染资产](#三车辆信息与-3d-渲染资产)
4. [车况遥测与 signalMap 解码表](#四车况遥测与-signalmap-解码表)
5. [行驶里程与能耗分析](#五行驶里程与能耗分析)
6. [远程控车指令全集](#六远程控车指令全集)
7. [高级车控与特色车主服务](#七高级车控与特色车主服务)
8. [蓝牙钥匙协议体系（BLE Key）](#八蓝牙钥匙协议体系ble-key)
9. [第三方集成 Web API](#九第三方集成-web-api)
10. [安全凭据与加密速查表](#十安全凭据与加密速查表)

---

## 一、主机域架构与动态路由

零跑车联网服务端采用新老双架构并存演进模式，主要包含以下主机域：

| 主机域 | 架构用途 | 协议与签名特征 |
| :--- | :--- | :--- |
| `https://appuser.leapmotor.cn` | **旧用户服务**：短信下发、手机号登录、旧 Token 续期 | RSA PKCS#1v1.5 + MD5 截断签名 |
| `https://app-gw-global-master.leapmotor.com` | **新网关主中心**：网关凭据交换、车辆列表、路由查找、3D车模元数据 | HMAC-SHA256 / SHA256 签名（`x-api-signature-version=2.0`） |
| `https://appgateway.leapmotor.com` | **车主业务网关**：行驶能耗记录、FOTA 升级信息、操作密码校验 | 混编签名（旧签名参数 + 网关鉴权头） |
| `https://iov-api.leapmotor.com` | **车联感知服务**：驻车实景环视照片查询 | 旧签名参数 + 网关鉴权头 |
| `{appRegion}` (动态路由) | **车辆专属区域网关**：车况实时查询、远程车控指令、健康充电设置 | 由 `getCarRoute` 接口动态下发（如 `https://app-gw-global-master.leapmotor.com`） |
| `{appCenter}` (动态路由) | **专属业务中心网关**：蓝牙钥匙证书同步、预约充电备用路由 | 由 `getCarRoute` 接口动态下发 |

---

## 二、登录、认证与签名体系

### 1. 发送短信验证码
* **URL**: `GET https://appuser.leapmotor.cn/app-user/applogin/compliance/sendmessagecode`
* **认证要求**: 无需 Token
* **Query 参数**:
  * `phoneNo`: 手机号，先经 **RSA PKCS#1v1.5** 算法加密，再做 **URL-Safe Base64**（无填充）编码；
* **业务响应**: `{"result": 0, "message": "请求成功"}`，错误码 `100115` 为验证码错误或超频。
* **客户端代码**: `LeapmotorApi.sendSms()`

### 2. 短信验证码登录
* **URL**: `POST https://appuser.leapmotor.cn/app-user/applogin/check_login_with_phone`
* **请求特征**: 业务参数走 URL Query，POST Body 保持为空。
* **Query 参数**:
  * `phoneNoCiphertext`: RSA 加密的手机号密文；
  * `smsCode`: 6 位数字验证码；
  * `deviceID`: 设备唯一标识符（UUID 去横杠，与 Header `APPImei` 保持一致）；
  * `smDeviceId`: 数美 SDK 本地采样的设备风控指纹（必须由合法 SDK 实例采集，不可伪造或用普通 deviceID 代替）；
  * `os`: `android`；
  * `pageUrl`: `""`。
* **业务响应**: 返回 `data.appLoginVO`（或兼容节点 `data.appOneLoginVO`），包含：
  * `accountId`: 用户账号 ID；
  * `token`: 旧版用户 Token（即 `oldAuth.token`）；
  * `refreshToken`: 旧版刷新凭证；
  * `tokenExpired`: 默认 21600 秒（6 小时）。
* **客户端代码**: `LeapmotorApi.loginWithSms()`

### 3. 旧 Token 自动续期
* **URL**: `GET https://appuser.leapmotor.cn/app-user/appuseroperate/getnewtoken`
* **认证要求**: 请求头携带 `XFX-CDN-CROSS-NODE: {token}` 与 `XFX-CDN-CROSS-REFRESH-NODE: {refreshToken}`。
* **Query 签名参数**: `timespan`, `nonce`, `deviceID`, `refreshtoken`, `accountId`, `accountNumber`（RSA 加密手机号）；
* **签名计算**: 参数名按字典升序排序，拼接为 `key1=value1&key2=value2...`，追加静态密钥后进行 MD5 计算，取前 16 位大写十六进制作为 `signStr`。
* **触发时机**: 当旧 Token 剩余有效期小于 60 秒时自动在工作线程续期。
* **客户端代码**: `LeapmotorApi.refreshOldToken()`

### 4. 换取新网关凭证（Gateway Exchange）
登录成功后，必须立即使用旧凭据换取微服务新网关的 JWT 访问令牌：
* **URL**: `POST https://app-gw-global-master.leapmotor.com/base/base-user/account/v1/login`
* **认证要求**: 无需 accessToken，签名采用无密钥的 `sign = SHA256(headerString + bodyString)`。
* **POST JSON Body**:
  ```json
  {
    "identifier": "{accountId}",
    "identifierType": "1",
    "security": "{oldToken}"
  }
  ```
* **业务响应**: 返回 `data` 对象，包含：
  * `accessToken`: 新网关 JWT 访问令牌（即 `newAuth.accessToken`）；
  * `refreshToken`: 新网关刷新凭证；
  * `signParam`: `{"r2": "...", "r3": "..."}`（用于派生网关加密签名密钥）；
  * `tokenExpireTime`: 毫秒级过期时间戳。
* **客户端代码**: `LeapmotorApi.exchangeNewGateway()`

### 5. 新网关 Token 续期
* **URL**: `POST https://app-gw-global-master.leapmotor.com/base/base-user/token/v1/refresh`
* **认证要求**: 使用当前新网关凭证执行 HMAC-SHA256 签名。
* **POST JSON Body**: `{"refreshToken": "{gatewayRefreshToken}"}`
* **触发时机**: 当 `accessToken` 距离过期小于 5 分钟时自动静默刷新；若刷新失败，自动无缝回退至旧链路续期并重新执行凭证交换。
* **客户端代码**: `LeapmotorApi.refreshGatewayToken()`

### 6. 操作密码（PIN）加密与前置校验
* **加密算法**: **AES-128-CBC**，PKCS5/PKCS7 填充。
* **密钥与 IV 派生算法**:
  * 优先使用新网关 JWT `accessToken` 前 64 位派生：
    * `Key = MD5(accessToken[0..32]).substring(8, 24)`（16 字节）
    * `IV  = MD5(accessToken[32..64]).substring(8, 24)`（16 字节）
  * 无网关 Token 时回退使用旧 Token 派生（`Key = MD5(oldToken)[8..24]`, `IV = Key`）。
* **前置密码校验接口**:
  * **URL**: `POST https://appgateway.leapmotor.com/carownerservice/v3/api/appoperate/verifyoperatepwdnew`
  * **表单参数**: `vin`, `oprpwd: {AES加密后的PIN}`
  * **返回结果**: `code = 0` 表示密码正确；密码错误时服务端返回明确错误码，客户端计数保护。
  * **客户端代码**: `LeapmotorApi.verifyOperatePassword()`

---

## 三、车辆信息与 3D 渲染资产

### 7. 车辆列表
* **URL**: `GET https://app-gw-global-master.leapmotor.com/app/app-global-service/v1/vehicle/list`
* **认证要求**: 新网关请求头 + HMAC-SHA256 签名。
* **业务响应**: `data[]` 包含所有绑定车辆：
  * `vin`: 车辆车架号；
  * `carType`: 车型代码（如 `C16`, `C11`, `T03`, `C01`）；
  * `licensePlate`: 车牌号；
  * `vehicleName` / `nickName`: 车辆昵称；
  * `funcConfig`: 车辆硬件能力配置对象（含 `HVAC` 空调温控与风量档位上下限等）。
* **客户端代码**: `LeapmotorApi.listVehicles()`

### 8. 车辆路由查找
* **URL**: `GET https://app-gw-global-master.leapmotor.com/app/app-global-service/v1/vehicle/getCarRoute?vin={vin}`
* **认证要求**: 新网关签名，参数 `vin` 参与签名。
* **业务响应**: `data.appRegion`（车况与车控网关）与 `data.appCenter`（蓝牙与预约网关）。
* **客户端代码**: `LeapmotorApi.getCarRoute()`

### 9. 车辆外观与 3D 车模元数据
* **URL**: `POST https://app-gw-global-master.leapmotor.com/carownerservice/vehicle/v1/carpicture/key`
* **请求格式**: `application/x-www-form-urlencoded`，包含 `deviceID` 与 `vin`。
* **业务响应**:
  ```json
  {
    "code": 0,
    "data": {
      "h5Key": "3D-702ef381-d7ed-49cd-8d69-fad6f5f9f397",
      "srcKey": "3D-d3e0fbce-0755-441e-8381-7512fd49bc70",
      "shareBindUrl": "https://lp-carnet.oss-cn-hangzhou.aliyuncs.com/carModel3D/...",
      "modelType": 3,
      "modelParam": {
        "carType": "C16",
        "year": 2026,
        "carTypeCode": "630激光雷达智尊版 6座",
        "colorCode": 3
      }
    }
  }
  ```
  * `shareBindUrl`: 官方 2D 精修透明底原厂车图 CDN 链接；
  * `h5Key`: Three.js 3D 渲染引擎与 H5 运行时骨架包 Key；
  * `srcKey`: 车辆专属高精度几何模型包 Key（车身、车轮、车灯、材质包）；
  * `modelParam`: 注入给 3D 引擎的车型参数（年款、配置、车漆色值）。
* **客户端代码**: `LeapmotorApi.getVehiclePictureMeta()`

### 10. 3D 车模双包协同下载与解压规范
* **下载地址**: `http://lp-carnet.oss-cn-hangzhou.aliyuncs.com/carModel3D/{KEY}.zip`
* **协同解压机制**:
  1. 先下载并解压 `h5Key.zip` 到本地独立目录，提供 `index.html` 与核心引擎；
  2. 再下载 `srcKey.zip`，解压覆盖合并到同一目录，提供当前车辆专属 GLTF/BIN/纹理；
  3. 写入 `.ready` 标记文件，完成资产就绪闭环。
* **客户端代码**: `CarModel3DManager.syncModelPackage()`

---

## 四、车况遥测与 signalMap 解码表

### 11. 车辆实时状态查询
* **URL**: `POST {appRegion}/app/app-signal-service/signal/info/query`
* **认证要求**: 新网关签名，Body 包含 `{"vin": "{vin}"}`。
* **业务响应**: 返回深层嵌套对象，核心字段在 `signalMap` 中以数字 ID 呈现。
* **客户端代码**: `LeapmotorApi.getVehicleStateRaw()` / `getVehicleState()`

### 12. `signalMap` 数字信号 ID 完整解码映射表

> **规范约束**：严格遵循实车遥测核验证据，严禁未经实车抓包验证随意猜测信号含义。

| 业务分类 | 信号 ID | 解码字段名称 | 数据类型 / 取值规范 | 业务含义说明 |
| :--- | :--- | :--- | :--- | :--- |
| **电量与续航** | `1204` | `soc` | 整数 `0..100` (%) | 动力电池剩余电量百分比（纯电/增程通用） |
| | `100003` | `preciseSoc` | 浮点数 / 整数 | 精确电量（保留小数点一位），用于精密仪表 |
| | `3235` | `fuelSoc` | 整数 `0..100` (%) | 增程车型燃油剩余百分比 |
| | `3257` | `electricRangeStandard` | 整数 (km) | CLTC 标准纯电剩余续航 |
| | `3260` | `expectedMileage` | 整数 (km) | WLTC 动态纯电剩余续航 |
| | `3256` | `fuelRangeStandard` | 整数 (km) | CLTC 标准燃油剩余续航（增程） |
| | `3259` | `fuelRangeDynamic` | 整数 (km) | WLTC 动态燃油剩余续航（增程） |
| | `3258` | `combinedRangeStandard` | 整数 (km) | CLTC 综合总续航（增程） |
| | `3261` | `combinedRangeDynamic` | 整数 (km) | WLTC 动态综合总续航（增程） |
| | `2188` | `liveRemainingRange` | 整数 (km) | 仪表实时显示续航 |
| | `3262` | `rangeMode` | 字符串 `"0"` 或 `"1"` | `0` = 标准续航模式，`1` = 动态续航模式 |
| **充电状态** | `1149` | `chargeState` | 整数 | `1`=充电中, `2`=充电完成, `3`=充电故障, `0`=未充电 |
| | `1200` | `chargeRemainTime` | 整数 (分钟) | 预计充满剩余时间（分） |
| | `1177` | `batteryVoltage` | 字符串/数值 (V) | 动力电池包总电压（如 `398.2V`） |
| | `1178` | `batteryCurrent` | 字符串/数值 (A) | 充放电电流（负值为充电输入电流） |
| | `1197` | `dcInputFastCharge` | 整数 / 布尔 | `1` / `true` 为直流快充，`0` / `false` 为交流慢充 |
| | `47` | `acInputSlowCharge` | 整数 / 布尔 | 交流慢充枪插入连接状态 |
| | `3736` | `chargeCompleted` | 整数 / 布尔 | 动力电池满电充停状态标志 |
| | `48` | `healthyChargeEnabled` | 整数 / 布尔 | 健康充电模式状态（限制上限保护寿命） |
| | `3737` | `chargeScheduleCancelledOnce` | 整数 | 预约充电临时单次跳过标志 |
| **行驶与动力** | `1318` | `totalMileage` | 字符串/浮点 (km) | 整车累计总行驶里程 |
| | `1319` | `speed` | 字符串/浮点 (km/h) | 实时行车车速（用于车身动效判定） |
| | `1010` | `gearStatus` | 字符串 / 整数 | 挡位：`P`=驻车, `R`=倒挡, `N`=空挡, `D`=前进挡 |
| | `1944` | `vehicleState` | 整数 | 整车电源状态：`1`=Ready行车, `0`=休眠熄火 |
| | `1480` | `parkingBrakeState` | 整数 | 电子手刹 EPB 状态：`1`=已拉起驻车, `0`=释放 |
| | `6048` | `speedLimit` | 整数 (km/h) | 道路限速抓取数值 |
| | `6047` | `speedLimitUnit` | 字符串 | 限速单位（`km/h`） |
| | `12054` | `speedLimitActive` | 整数 / 布尔 | 限速预警激活状态 |
| **车辆位置** | `2` / `3724` | `longitude` | 浮点数 | 车辆 GPS 经度（GCJ-02 坐标系） |
| | `3` / `3725` | `latitude` | 浮点数 | 车辆 GPS 纬度（GCJ-02 坐标系） |
| **车门与车锁** | `1298` | `driverDoorLockStatus` | 整数 | 车门锁止状态：`0`=已解锁, `1`=已上锁 |
| | `1277` | `lbcmDriverDoorStatus` | 整数 | 主驾左前门状态：`0`=关闭, `1`=打开 |
| | `1278` | `rbcmDriverDoorStatus` | 整数 | 副驾右前门状态：`0`=关闭, `1`=打开 |
| | `1279` | `lbcmLeftRearDoorStatus` | 整数 | 左后车门状态：`0`=关闭, `1`=打开 |
| | `1280` | `rbcmRightRearDoorStatus`| 整数 | 右后车门状态：`0`=关闭, `1`=打开 |
| | `1281` | `bbcmBackDoorStatus` | 整数 | 电动后备箱尾门状态：`0`=关闭, `1`=打开 |
| **车窗与天幕** | `1693` | `driverWindowStatus` | 整数 | 主驾左前车窗开闭状态（`0`=关，`1`=开） |
| | `1694` | `rightFrontWindowStatus`| 整数 | 副驾右前车窗开闭状态 |
| | `1695` | `leftRearWindowStatus` | 整数 | 左后车窗开闭状态 |
| | `1696` | `rightRearWindowStatus` | 整数 | 右后车窗开闭状态 |
| | `1724` | `roofOpening` | 整数 `0..100` (%) | 电动天幕/遮阳帘开启开度百分比 |
| **座舱空调** | `1938` | `acSwitch` | 整数 | 空调总开关：`0`=关闭, `1`=开启运行 |
| | `2183` | `acSetting` | 整数/浮点 (℃) | 主驾设定目标温度（如 `24`） |
| | `2184` | `acSettingRight` | 整数/浮点 (℃) | 副驾设定目标温度 |
| | `1349` | `interiorTemp` | 字符串/浮点 (℃) | 座舱内实测当前温度（如 `22.5℃`） |
| | `1943` | `recirculationMode` | 整数 | 循环模式：`0`=外循环, `1`=内循环 |
| | `1941` | `acAirVolume` | 整数 `1..7` | 空调出风风量档位 |
| | `1945` | `windshieldDefrost` | 整数 | 前风挡加热除霜：`0`=关, `1`=开启 |
| | `1946` | `rearWindowHeating` | 整数 | 后风挡及后视镜电加热除雾：`0`=关, `1`=开 |
| | `3713` | `climateMode` | 整数 | 空调模式：`0`=自然风, `1`=制冷, `2`=制热 |
| | `2669` | `rapidCooling` | 整数 | 极速降温运行状态标志 |
| | `2681` | `rapidHeating` | 整数 | 极速升温运行状态标志 |
| **座椅舒适** | `2100` | `driverSeatHeating` | 整数 `0..3` | 主驾座椅加热档位（`0`=关, `1`=低, `2`=中, `3`=高） |
| | `2101` | `driverSeatVentilation` | 整数 `0..3` | 主驾座椅通风档位 |
| | `2118` | `passengerSeatHeating` | 整数 `0..3` | 副驾座椅加热档位 |
| | `2119` | `passengerSeatVentilation`| 整数 `0..3` | 副驾座椅通风档位 |
| | `1879` | `leftRearSeatHeating` | 整数 `0..3` | 二排左座加热档位 |
| | `3727` | `leftRearSeatVentilation`| 整数 `0..3` | 二排左座通风档位 |
| | `1880` | `rightRearSeatHeating` | 整数 `0..3` | 二排右座加热档位 |
| | `3728` | `rightRearSeatVentilation`| 整数 `0..3` | 二排右座通风档位 |
| | `1816` | `steeringWheelHeating` | 整数 `0..2` | 方向盘加热档位（`0`=关, `1`=弱, `2`=强） |
| | `1624` | `steeringWheelHeaterMinutes` | 整数 | 方向盘加热运行倒计时分钟数 |
| | `49` / `50`| `left/rightMirrorHeating` | 整数 `0..1` | 左右外后视镜电加热状态 |
| **四轮胎压** | `2646` | `leftFrontTirePressure` | 浮点数 (bar) | 左前轮胎压（C16 实车核验映射） |
| | `2653` | `rightFrontTirePressure`| 浮点数 (bar) | 右前轮胎压 |
| | `2660` | `leftRearTirePressure` | 浮点数 (bar) | 左后轮胎压 |
| | `2667` | `rightRearTirePressure` | 浮点数 (bar) | 右后轮胎压 |
| | `2641/2648/2662/2655` | `...TirePressureState` | 整数 | 对应轮胎健康状态（`0`=正常, `1`=低压, `2`=高压） |
| **车载冰箱** | `10709` | `fridgeSwitch` | 整数 | 冰箱电源开关：`0`=关机, `1`=开机 |
| | `10708` | `fridgeMode` | 整数 | 运行模式：`0`=制冷, `1`=制热 (50℃保温) |
| | `10707` | `fridgeTargetTemp` | 整数 (℃) | 制冷目标设定温度（-6℃ ~ 15℃） |
| | `10711` | `fridgeStyle` | 整数 | 制冷风格：`0`=标准节能, `1`=急速强劲 |
| | `10712` | `fridgeFault` | 整数 | 冰箱硬件故障状态码（`0`=正常） |
| | `11190` | `fridgeParkSwitch` | 整数 | 离车持续运行开关：`0`=关, `1`=开 |
| | `11189` | `fridgeParkDurationHours`| 整数 | 离车运行持续时长（小时） |
| | `11191` | `fridgeParkCycles` | 整数 | 离车运行频次模式（`0`=单次, `1`=每次） |
| | `11260` | `fridgeParkEndTime` | 秒级时间戳 | 离车保温截止时间点 |
| **热管理与安防**| `1182` | `minBatteryTemp` | 整数 (℃) | 动力电池电芯最低温度 |
| | `1186` | `batteryThermalRequest` | 整数 | 电池热管理请求：`4`=预热加热中, `0`=未开启 |
| | `1255` | `vehicleSecurityActive` | 整数 / 布尔 | 原厂防盗防入侵警戒设防状态 |
| | `3636` | `sentryMode` | 整数 | 哨兵模式工作状态：`0`=关闭, `1`=开启警戒 |

---

## 五、行驶里程与能耗分析

### 13. 行驶里程与累计能耗明细
* **URL**: `GET https://appgateway.leapmotor.com/carownerservice/v3/api/drivingrecord/mileage/energy/detail`
* **认证要求**: 旧 Token + MD5 签名参数。
* **Query 参数**: `begintime`（车辆提车日 0 点秒级时间戳）、`endtime`（当前秒级时间戳）、`vin`。
* **业务数据**: 累计总里程、电耗能耗分布、百公里平均能耗。
* **客户端代码**: `LeapmotorApi.getMileageEnergy()`

### 14. 近 7 日里程趋势明细
* **URL**: `GET https://appgateway.leapmotor.com/carownerservice/v3/api/drivingrecord/mileage/energy/detail`
* **Query 参数**: `begintime`（7天前 0 点**毫秒级**时间戳）、`endtime`（当前**毫秒级**时间戳）、`vin`、旧签名串。
* **业务响应**: `data.detail[]` 数组，每项包含 `day: "yyyy-MM-dd"` 与 `accumulatedMileage`。
* **客户端代码**: `LeapmotorApi.getRecentMileageEnergy()`

### 15. 近 6 周百公里能耗与同车型排行
* **URL**: `GET https://appgateway.leapmotor.com/carownerservice/v3/api/drivingrecord/getLastNweeks100kmECAndRank`
* **Query 参数**: `carvin`（**注意该接口严格使用 `carvin` 而非 `vin`**）及旧签名参数。
* **业务数据**: 近 6 周每周能耗折线数据、百公里电耗、同车型全国车主节油/省电排行榜位。
* **客户端代码**: `LeapmotorApi.getLastNWeeks100kmEcAndRank()`

### 16. 上周电耗摘要
* **URL**: `GET https://appgateway.leapmotor.com/carownerservice/v3/api/drivingrecord/getLastweekEC`
* **客户端代码**: `LeapmotorApi.getLastWeekEc()`

---

## 六、远程控车指令全集

### 17. 控车指令发送规范
* **URL**: `POST {appRegion}/app/app-control-service/v3/api/appremotectl`
* **请求头**: 携带完整旧版客户端身份头 + 动态派生网关 JWT 头。
* **表单参数 (Form Body)**:
  * `cmdid`: 指令编号（字符串）；
  * `state`: 指令业务 JSON 负载（字符串）；
  * `carvin`: 当前操作车辆 VIN；
  * `oppwd`: 本地使用 AES-128-CBC 加密后的 4 位操作密码。
* **业务响应**: `{"result": 0, "data": "<msgID>"}`，其中 `msgID` 用于结果轮询。
* **客户端代码**: `LeapmotorApi.sendControl()`

### 18. 控车结果轮询规范
* **URL**: `GET {appRegion}/app/app-control-service/v3/api/appremotectl/query?msgID={msgID}`
* **轮询策略**: 首次等待 1.0 秒，之后每 0.5 秒查询一次。主 App 超时窗口为 24 秒，桌面小组件超时窗口为 12 秒。返回 `result = 0` 且车辆遥测数据确认变更后，判定控车彻底完成。
* **客户端代码**: `LeapmotorApi.queryControlResult()`

### 19. 控车指令集完整速查表（基于 `Commands.build()`）

| 指令名称 | `cmdid` | `state` JSON 参数体 | 操作说明 | 安全验证门禁 |
| :--- | :--- | :--- | :--- | :--- |
| `lock` | `110` | `{"value":"lock"}` | 远程车门上锁 | 4位操作密码 |
| `unlock` | `110` | `{"value":"unlock"}` | 远程车门解锁 | 4位操作密码 + 生物识别确认 |
| `trunkOpen` | `130` | `{"value":"true"}` | 开启电动后备箱 | 4位操作密码 + 长按1.2s蓄力 |
| `trunkClose` | `130` | `{"value":"false"}` | 关闭电动后备箱 | 4位操作密码 |
| `frunkOpen` | `131` | `{"value":"100"}` | 开启前备箱（D19支持） | 4位操作密码 |
| `frunkClose` | `131` | `{"value":"0"}` | 关闭前备箱 | 4位操作密码 |
| `windowOpen` | `230` | `{"value":"5"}` | 车窗半开 (降窗50%) | 4位操作密码 |
| `windowVent` | `230` | `{"value":"2"}` | 车窗通风 (微开缝隙) | 4位操作密码 |
| `windowClose` | `230` | `{"value":"0"}` | 车窗一键全关升顶 | 4位操作密码 |
| `sunshadeOpen`| `240` | `{"value":"10"}` | 全景遮阳帘全开 | 4位操作密码 |
| `sunshadeClose`| `240` | `{"value":"0"}` | 全景遮阳帘全关 | 4位操作密码 |
| `horn` | `120` | `{"value":"true"}` | 鸣笛闪灯寻车 | 4位操作密码 |
| `batteryPreheat`| `160` | `{"value":"ptcon"}` | 开启电池加热预热 | 4位操作密码 |
| `batteryPreheatOff`| `160` | `{"value":"ptcoff"}` | 关闭电池预热 | 4位操作密码 |
| `acOn` | `170` | `{"operate":"manual","temperature":"24","windlevel":"3","mode":"cold","circle":"in","wshld":"0","position":"all"}` | 开启座舱空调（默认24℃） | 4位操作密码 |
| `acOff` | `170` | `{"operate":"off","temperature":"24","windlevel":"3","mode":"nohotcold","circle":"out","wshld":"0","position":"all"}` | 关闭座舱空调 | 4位操作密码 |
| `quickCool` | `170` | `{"operate":"manual","temperature":"18","windlevel":"7","mode":"cold","circle":"in","wshld":"0","position":"all"}` | 极速降温（18℃最大风内循环） | 4位操作密码 |
| `quickHeat` | `170` | `{"operate":"manual","temperature":"32","windlevel":"7","mode":"hot","circle":"in","wshld":"0","position":"all"}` | 极速升温（32℃暖风最大风） | 4位操作密码 |
| `defrost` | `170` | `{"operate":"manual","temperature":"24","windlevel":"5","mode":"cold","circle":"out","wshld":"1","position":"wshld"}` | 前风挡强力除霜除雾 | 4位操作密码 |
| `deodorize` | `170` | `{"operate":"manual","temperature":"24","windlevel":"7","mode":"nohotcold","circle":"out","wshld":"0","position":"all"}` | 座舱快速外循环除味 | 4位操作密码 |
| `customAc` | `170` | `{"operate":"manual","temperature":"{16..32}","windlevel":"{1..7}","mode":"cold/hot","circle":"in/out","wshld":"0/1","position":"all/wshld"}` | 空调全参数多维定制控制 | 4位操作密码 |
| `seatHeat` | `301` | `{"position":"left_front/right_front/left_rear/right_rear","level":"{0..3}"}` | 独立座椅加热档位控制 | 4位操作密码 |
| `seatVentilation`| `370` | `{"position":"left_front/right_front/left_rear/right_rear","level":"{0..3}"}` | 独立座椅通风档位控制 | 4位操作密码 |
| `steeringWheelHeat`| `320`| `{"level":"{0..2}"}` | 方向盘加热（0关/1弱/2强） | 4位操作密码 |
| `rearviewMirrorHeat`| `440`| `{"value":"{1/2}"}` | 后视镜加热（1关/2开） | 4位操作密码 |
| `fridgeOn` | `500` | `{"cycles":"1","duration":3600,"enable":1,"mode":"cold","parkEnable":0,"style":"normal","temp":4,"value":"false"}` | 车载冰箱开机制冷 | 4位操作密码 |
| `fridgeOff` | `500` | `{"cycles":"1","duration":3600,"enable":0,"mode":"cold","parkEnable":0,"style":"normal","temp":4,"value":"false"}` | 车载冰箱关机 | 4位操作密码 |
| `fridgeControl`| `500` | `{"cycles":"{1/0}","duration":{秒},"enable":{0/1},"mode":"cold/hot","parkEnable":{0/1},"style":"normal/fast","temp":{℃},"value":"false"}` | 车载冰箱深度温控与离车保温 | 4位操作密码 |
| `sentryOn` | `400` | `{"operation":"on"}` | 开启哨兵模式 | 4位操作密码 |
| `sentryOff` | `400` | `{"operation":"off"}` | 关闭哨兵模式 | 4位操作密码 |
| `startCharging`| `193` | `{"value":"start"}` | 远程启动即时充电 | 4位操作密码 |
| `stopCharging` | `193` | `{"value":"stop"}` | 远程停止当前充电 | 4位操作密码 |
| `unlockCharger`| `192` | `{"operation":"unlock"}` | 解锁交流/直流充电枪锁止销 | 4位操作密码 |
| `fotaDownload` | `390` | `{"taskId":"{taskId}"}` | 车机开始下载新版固件 | 4位操作密码 |
| `fotaInstall` | `391` | `{"taskId":"{taskId}"}` | 车机开始就地刷写安装固件 | 4位操作密码 |
| `fotaSchedule` | `392` | `{"taskId":"{taskId}","scheduleTime":"yyyy-MM-dd HH:mm:ss"}` | 预约车机在夜间静默升级 | 4位操作密码 |

---

## 七、高级车控与特色车主服务

### 20. 驻车实景环视照片
* **元数据查询**: `GET /carownerservice/v3/api/chassis/query?vin={vin}&signStr=...`
  * 优先请求 `https://iov-api.leapmotor.com` 或 `{appRegion}`。
  * 响应: `data.fileUrl`（OSS 加密图片链接）、`data.uploadTime`（车辆停车回传时间戳毫秒）。
* **实景照片解密下载**:
  * 客户端直接通过原生网络通道拉取 `fileUrl`，图片格式为标准图像流，解码后在弹窗中支持双指 0.8x~5.0x 无级缩放平移预览。
* **客户端代码**: `LeapmotorApi.getChassisParkingPhoto()` / `downloadParkingPhotoBitmap()`

### 21. 车机 OTA 固件升级与状态
* **升级信息查询**: `GET /carownerservice/v3/api/fota/getCurrentVersion?vin={vin}&signStr=...`
  * 请求主机: 优先 `https://appgateway.leapmotor.com`。
  * 业务响应:
    * `currentVersion`: 当前车机系统版本（如 `2.02.80`）；
    * `targetVersion`: 待升级目标版本；
    * `hasNewVersion`: 是否有更新推送（`true`/`false`）；
    * `releaseNotes`: 官方版本更新日志说明；
    * `status`: 升级生命周期状态（`0`=已是最新, `1`=待下载, `2`=下载中, `3`=下载完成待安装, `4`=安装中, `5`=升级成功）；
    * `taskId`: 固件升级任务 ID；
    * `scheduleTime`: 已预约的夜间升级时间。
* **升级状态重置**: `POST {appRegion}/app/app-fota-service/v1/fota/resetStatus`（遇升级失败时重置状态机）。
* **客户端代码**: `LeapmotorApi.getVehicleOtaInfo()` / `fotaResetStatus()`

### 22. 健康充电管理
* **设置充电上限**: `POST {appRegion}/carownerservice/v3/api/healthyCharging/control`
  * 参数: `vin`, `state: 1`, `targetSoc: 80`（支持 50%~100% 自定义设定）。
* **查询策略推送状态**: `GET {appRegion}/carownerservice/v3/api/healthyCharging/queryPushState?vin={vin}`
* **客户端代码**: `LeapmotorApi.setHealthyCharging()` / `queryHealthyChargingPushState()`

### 23. 谷电预约充电计划（cmdid=190）
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
* **容灾路由机制**:
  1. 首选走核心控车通道：`POST {appRegion}/app/app-control-service/v3/api/appremotectl`；
  2. 若失败，回退预约专用通道：`POST {appCenter}/carownerservice/v3/api/appremotectl/appointment`；
  3. 若依然失败，回退日程微服务网关：`POST {appRegion}/carownerservice/v3/api/schedule/operate`。
* **客户端代码**: `LeapmotorApi.setScheduledCharging()`

### 24. 预约电池预热计划（cmdid=161）
* **指令 Payload**:
  ```json
  {
    "controls": [
      {
        "on": "1",
        "set_id": "ptc_set_1727000000000",
        "start_time": "2026-09-23 07:30:00",
        "update_time": "1727000000000",
        "days": [1, 2, 3, 4, 5, 6, 7]
      }
    ]
  }
  ```
* **通道**: `POST {appRegion}/app/app-control-service/v3/api/appremotectl`
* **客户端代码**: `LeapmotorApi.setScheduledBatteryPreheat()`

---

## 八、蓝牙钥匙协议体系（BLE Key）

### 25. 证书与元数据云端同步
* **证书拉取**: `POST {appCenter}/carownerservice/v3/api/bluetoothkey/combine/syncBluetoothKeys`
  * 表单: `vin`, `timespan`, `nonce`, `deviceID`, `signStr`。
  * 响应: 返回 X.509 格式的车辆专属蓝牙身份证书及密钥类型（`keyType: 0` 为标准国密/国际 P-256，`keyType: 1` 为纯国密 SM2）。
* **车辆广播与标定参数读取**: `POST /carownerservice/v3/api/vehicleinfo/commonConfig`
  * 读取项 `"4"` 获取车辆蓝牙广播 MAC 与协议主次版本号。
* **云端无感闭锁配置上传**: `POST /app/app-global-service/v3/api/commoninfo/transparent/conf/upload`
  * 保存 `bleKeySwitch`, `bleKeyUnlock`, `bleKeyLock`, `bleKeyBtn` 四大无感解闭锁偏好。
* **天线雷达标定参数上传**: `POST /app/app-global-service/v3/api/bluetoothkey/uploadAutonomyCalibrateParams`
* **客户端代码**: `LeapmotorApi.fetchBluetoothKeyCertificate()` / `BluetoothKeyController`

### 26. GATT 物理交互与加密控制
* **GATT 服务 UUID**: `0000FFFE-0000-1000-8000-00805F9B34FB`（Notify 与 Write）
* **GATT 特征 UUID**: `0000FFF2-0000-1000-8000-00805F9B34FB`
* **会话协商**:
  * 阶段一：握手建立，双向交换 16 字节真随机数；
  * 阶段二：通过 ECDH（P-256 或 SM2）派生会话主密钥；
  * 阶段三：使用 AES-128-GCM 或 SM4 组装加密车控帧，帧头携带滚动序列号计数器（Counter）防重放攻击；
  * 阶段四：车端执行完毕后通过 Notify 返回 `AA AC` 认证事件。

---

## 九、第三方集成 Web API

### 27. 高德地图逆地理编码 Web API
* **用途**: 将车辆实时上报的 GCJ-02 经纬度坐标解析为可读的行政区划、城市、路名及兴趣点（POI）。
* **URL**: `GET https://restapi.amap.com/v3/geocode/regeo`
* **Query 参数**:
  * `key`: 高德开放平台 Web 服务 Key（混淆保存在客户端 native/obfuscated 常量池）；
  * `location`: 格式化经纬度 `"{longitude},{latitude}"`（保留 6 位小数）；
  * `extensions`: `all`；
  * `output`: `json`。
* **响应解析**: 提取 `regeocode.formatted_address`、`addressComponent.adcode` 与 `addressComponent.city`。
* **客户端代码**: `VehicleLocationGeocoder.reverseGeocode()`

### 28. 高德地图实况天气 Web API
* **用途**: 查询车辆所在地实时天气现象、实时温度、风力风向与湿度，驱动首卡与驻车详情一体化气象卡片，智能推导洗车适宜指数。
* **URL**: `GET https://restapi.amap.com/v3/weather/weatherInfo`
* **Query 参数**:
  * `key`: 高德开放平台 Web 服务 Key；
  * `city`: 逆地理编码提取出的 6 位行政区划编码 `adcode`；
  * `extensions`: `base`（查询实时天气）。
* **响应解析**:
  * `weather`: 实况天气（“晴”、“多云”、“小雨”、“雾”等，映射到精美微晶矢量图标）；
  * `temperature`: 实时室外气温（℃）；
  * `winddirection`: 风向描述；
  * `windpower`: 风力级别（如 `≤3级`）；
  * `humidity`: 空气相对湿度百分比（`0..100`）；
  * `reporttime`: 气象台发布时间戳。
* **缓存策略**: 内存单例持有 45 分钟 TTL 缓存，避免车辆静止时高频发起多余请求。
* **客户端代码**: `AmapWeatherService.getLiveWeather()`

### 29. 蒲公英应用版本更新检测与静默下载
* **页面检测 URL**: `GET https://www.pgyer.com/lingpaozhikong`
  * 爬取公开 HTML 页面中的版本号（如 `3.6.8`）、Build 编号与更新说明，零鉴权零成本感知新版发布。
* **OpenAPI v2 直链获取**: `POST https://www.pgyer.com/apiv2/app/install`
  * 参数: `_api_key`, `buildKey`
  * 获取官方 CDN 直链与下载鉴权 Token。
* **文件下载与安装**: 下载到私有缓存目录 `cache/updates/`，通过 Android `FileProvider` 调用系统 PackageInstaller 静默拉起更新。
* **客户端代码**: `VersionUpdate.kt` / `AppUpdateInstaller.kt`

---

## 十、安全凭据与加密速查表

| 安全领域 | 算法规范 | 密钥来源 / 存储保护机制 | 攻击防范与生命周期说明 |
| :--- | :--- | :--- | :--- |
| **手机号加密** | RSA / ECB / PKCS1Padding | 车厂内置 RSA 2048 位公钥证书 | 短信发送与登录时单向加密，防明文泄露 |
| **网关请求签名** | HMAC-SHA256 (十六进制输出) | 由 `accessToken + r2 + r3` 派生 `signKey` | 签名头包含 `timestamp`、`nonce`、`deviceId`，防请求重放与篡改 |
| **旧接口请求签名** | MD5 (前 16 位大写 Hex) | 参数 ASCII 排序拼接 + 固定混淆盐 | 兼容车机老服务车控与能耗接口 |
| **4位操作密码 (PIN)**| AES-128-CBC (PKCS5Padding) | 本地由 JWT `accessToken` 动态派生 Key/IV | 控车时动态加密发送，仅保存在 Android Keystore 硬件隔离安全区 |
| **本地凭证存储** | AES-256-GCM | Android Keystore 硬件主密钥（MasterKey） | 加密存储 Token、操作 PIN、账号 ID，系统 root 亦无法脱机逆向 |
| **第三方 API Key** | 变长动态双向异或混淆 (XOR) | DEX 内置动态算子 + 官方签名防篡改校验 | 阻断反编译静态搜索与二次打包劫持盗刷 |
| **车辆 GPS 坐标** | GCJ-02 国测局火星坐标系 | 仅在 Activity 内存中维持最新快照 | **绝不持久化落盘，不写日志，不写小组件快照**，保障车主隐私 |
