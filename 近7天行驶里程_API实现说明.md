# 近 7 天行驶里程 API 实现说明

## 1. 交接目标

请在目标项目中实现“近 7 天行驶里程”查询与展示。

实现前先检查目标项目现有的登录会话、旧车主服务请求、双重签名、Token 刷新和错误映射代码。必须复用已有的请求与签名能力，不要另写一套猜测性的签名算法，也不要硬编码 Token、VIN、签名密钥或设备标识。

本文结论来自恢复后的 APK/DEX 静态源码。尚未使用真实账号和车辆请求服务端，因此“服务端当前仍可用”和“真实响应是否已经变化”必须在有授权的测试账号上单独验证。

## 2. 核心结论

存在可用于近 7 天里程的接口，但它不是直接返回“7 天总里程”的专用接口，而是一个按时间范围查询里程和能耗明细的接口：

```http
GET https://appgateway.leapmotor.com/carownerservice/v3/api/drivingrecord/mileage/energy/detail
```

客户端行为如下：

1. 按上海时区生成开始和结束时间。
2. 请求时间范围内的逐日里程明细。
3. 网络层最多保留响应中的最后 8 条日数据。
4. 页面再取最后 7 条日数据。
5. 对最后 7 条的 `accumulatedMileage` 求和。
6. 将总和转换为整数并显示为 `近7天 N km`。

因此，近 7 天总里程应由客户端计算，而不是从响应中读取一个假设存在的 `last7DaysMileage` 字段。

## 3. 已确认的请求契约

### 3.1 HTTP 方法和 URL

```text
Method: GET
URL: https://appgateway.leapmotor.com/carownerservice/v3/api/drivingrecord/mileage/energy/detail
```

### 3.2 业务查询参数

| 参数 | 类型 | 含义 | 静态确认状态 |
|---|---:|---|---|
| `vin` | String | 当前车辆 VIN | Confirmed |
| `begintime` | Long | 开始时间，Unix 毫秒时间戳 | Confirmed |
| `endtime` | Long | 结束时间，Unix 毫秒时间戳 | Confirmed |

### 3.3 动态签名查询参数

| 参数 | 含义 | 注意事项 |
|---|---|---|
| `deviceID` | 旧车主服务使用的设备标识 | 从现有会话/设备标识提供方取得 |
| `nonce` | 每次请求生成的随机数 | 不得固定复用 |
| `timespan` | 请求时间戳 | 当前实现使用毫秒时间戳字符串 |
| `signStr` | 查询参数签名结果 | 必须复用已有签名实现 |

最终查询字符串的字段集合至少包括：

```text
vin
begintime
endtime
deviceID
nonce
timespan
signStr
```

示意请求如下，示例中的值均为占位符：

```http
GET /carownerservice/v3/api/drivingrecord/mileage/energy/detail
    ?vin=<VIN>
    &begintime=<EPOCH_MILLISECONDS>
    &endtime=<EPOCH_MILLISECONDS>
    &deviceID=<DEVICE_ID>
    &nonce=<NONCE>
    &timespan=<REQUEST_TIMESTAMP>
    &signStr=<QUERY_SIGNATURE>
```

### 3.4 会话与签名请求头

恢复源码还会构造以下旧车主服务请求头：

```text
cartype
userId
token
carvin
digest
sign
```

同时会带公共客户端请求头，例如：

```text
acceptLanguage
deviceType
source
version
channel
timestamp
nonce
deviceId
x-subversion
x-api-signature-version
x-region
Content-Type
User-Agent
```

实现要求：

- 优先调用目标项目现有的 `v3DoubleSignGet`、旧车主服务 GET 请求器或等价封装。
- 签名输入必须包含本次请求的业务参数和动态元数据。
- Header `sign` 和查询参数 `signStr` 是两个构造位置，不要只实现其中一个。
- Token 过期时复用现有刷新和单次重试流程，避免无限重试。
- 不要把任何真实会话值写入源码、日志、测试快照或本文档。

## 4. 近 7 天时间范围

恢复源码固定使用：

```text
ZoneId = Asia/Shanghai
endtime = 当前 Unix 毫秒时间戳
begintime = 当前上海日期减 7 天后的 00:00:00 对应的 Unix 毫秒时间戳
```

Kotlin 参考实现：

```kotlin
private val vehicleZone: ZoneId = ZoneId.of("Asia/Shanghai")

fun recentMileageRange(now: Instant = Instant.now()): LongRange {
    val endMs = now.toEpochMilli()
    val beginMs = now
        .atZone(vehicleZone)
        .toLocalDate()
        .minusDays(7)
        .atStartOfDay(vehicleZone)
        .toInstant()
        .toEpochMilli()

    return beginMs..endMs
}
```

注意：该区间从“7 天前零点”到“现在”，最多可能覆盖 8 个自然日期。原客户端有意先保留最后 8 条，再在页面取最后 7 条。若目标产品希望严格定义为“今天加前 6 个完整自然日”，时间范围会不同，不能在未确认产品口径时擅自修改。默认应先复现 APK 行为。

## 5. 响应结构与数据模型

按恢复源码的解析逻辑，业务响应形状如下。该 JSON 是依据解析字段整理的结构示意，不是真实流量样本：

```json
{
  "code": 0,
  "success": true,
  "data": {
    "detail": [
      {
        "day": "2026-08-13",
        "accumulatedMileage": 12.5
      },
      {
        "day": "2026-08-14",
        "accumulatedMileage": 30.2
      }
    ],
    "totalEnergy": 45.6,
    "deliveryDays": 365
  }
}
```

已确认的解析规则：

- 顶层状态码兼容字段 `code` 和 `result`。
- 当响应存在 `success` 且为 `false` 时，应视为请求失败。
- `data` 必须存在。
- `data.detail` 必须存在并且是数组。
- 每条有效日数据读取 `day` 和 `accumulatedMileage`。
- `data.totalEnergy` 和 `data.deliveryDays` 可选；它们不是计算近 7 天里程的必要字段。

建议模型：

```kotlin
data class DailyMileage(
    val day: String,
    val mileageKm: Double,
)

data class MileageEnergyDetail(
    val mileage: List<DailyMileage>,
    val totalEnergyKwh: Double?,
    val deliveryDays: Int?,
)
```

字段映射：

```text
data.detail[].day                  -> DailyMileage.day
data.detail[].accumulatedMileage  -> DailyMileage.mileageKm
data.totalEnergy                  -> MileageEnergyDetail.totalEnergyKwh
data.deliveryDays                 -> MileageEnergyDetail.deliveryDays
```

## 6. 汇总规则

要复现 APK 行为，应执行：

```kotlin
val networkRows = parsedRows.takeLast(8)
val displayedRows = networkRows.takeLast(7)
val totalMileageKm = displayedRows.sumOf { it.mileageKm }
val displayedTotalKm = totalMileageKm.toInt()
```

说明：

- `takeLast` 基于服务端返回顺序；静态源码没有在求和前显式按日期排序。
- 不要未经实测就假定响应一定是升序或降序。
- 首次有授权联调时，应记录脱敏后的日期顺序并确认服务端排序契约。
- 若服务端顺序不稳定，再基于解析后的 `day` 明确排序，并为该行为补测试。
- 原页面通过转换为 `Int` 丢弃小数部分，不是四舍五入。若产品需要保留一位小数，应作为明确的产品变更处理。

## 7. 建议实现步骤

1. 定位现有登录会话模型，确认能取得 VIN、旧 Token、账户 ID、车型、设备 ID 和签名密钥。
2. 定位旧车主服务的 GET 请求和双重签名封装。
3. 新增 `DailyMileage` 与 `MileageEnergyDetail` 数据模型。
4. 新增按 `begintime/endtime` 请求里程能耗明细的方法。
5. 新增上海时区的近 7 天时间范围生成器。
6. 解析 `data.detail`，过滤无效行，网络层保留最后 8 条。
7. 业务/UI 层取最后 7 条并求和。
8. 接入现有刷新流程、加载态、空数据态和错误提示。
9. 添加单元测试；有授权测试账号后再做真实接口联调。

建议的业务接口形式：

```kotlin
suspend fun fetchMileageEnergyDetail(
    beginTimeMs: Long,
    endTimeMs: Long,
): MileageEnergyDetail

suspend fun fetchRecentSevenDayMileage(): MileageEnergyDetail
```

不要让 UI 直接拼 URL、生成签名或解析原始 JSON。

## 8. 错误和边界处理

- 未登录或会话字段缺失：在发请求前失败，并返回明确的登录态错误。
- Token 过期：刷新一次后重试原请求；刷新失败则结束，不循环。
- `data` 或 `detail` 缺失：作为协议错误处理，不伪造为 0 km。
- `detail` 为空：展示无数据状态；是否显示 0 km由产品口径决定。
- 少于 7 条：对实际存在的有效条目求和，并保留“数据不足 7 天”的内部状态或日志。
- `day` 为空：丢弃该条。
- `accumulatedMileage` 缺失、NaN 或无限值：丢弃该条，不参与求和。
- 负里程：静态源码没有明确处理；建议视为异常行并在脱敏日志中记录。
- 网络错误和服务端拒绝：复用现有用户可理解的错误映射，不展示签名、Token 或原始敏感响应。

## 9. 最低测试清单

- 时间范围使用 `Asia/Shanghai`，不依赖设备当前默认时区。
- `begintime` 是上海日期减 7 天的零点，`endtime` 是当前毫秒时间戳。
- 8 条日数据时只汇总最后 7 条。
- 超过 8 条时网络模型只保留最后 8 条。
- 少于 7 条时对现有有效数据求和。
- 空数组不会崩溃。
- 缺少 `data` 或 `detail` 时返回协议错误。
- 空 `day`、非数字里程、NaN、无限值不会进入汇总。
- 小数总和按当前兼容行为转换为整数，不四舍五入。
- `success=false` 被识别为失败。
- `code/result` 的成功和失败状态能够正确区分。
- Token 过期只刷新并重试一次。
- 请求日志不会输出 VIN、Token、设备 ID、签名值或签名密钥。

## 10. 验收标准

- 能通过已有登录会话查询当前车辆的逐日里程数据。
- 请求复用项目现有旧车主服务签名能力，没有重复或硬编码签名算法。
- 页面展示最后 7 条有效日里程和其汇总值。
- 汇总结果与 `takeLast(7).sumOf(accumulatedMileage).toInt()` 一致。
- 加载、空数据、认证失败、协议错误和普通网络错误均有稳定状态。
- 自动化测试覆盖时间边界、7/8 条截取规则、解析异常和 Token 刷新。
- 真实联调只使用获得授权的测试账号和车辆，证据中对敏感字段脱敏。

## 11. 静态确认与待验证边界

### Confirmed

- URL 和 GET 方法。
- `vin`、`begintime`、`endtime` 查询字段。
- `deviceID`、`nonce`、`timespan`、`signStr` 动态查询字段。
- `token`、`carvin`、`userId`、`cartype`、`digest`、`sign` 等请求头构造位置。
- 上海时区及时间范围算法。
- `data.detail[].day` 和 `data.detail[].accumulatedMileage` 响应字段。
- 网络层取最后 8 条、页面取最后 7 条并求和。
- 页面当前以整数公里显示。

### Unknown / 需要授权联调

- 服务端在当前日期是否仍开放该接口。
- 真实响应顶层字段是否与静态解析兼容。
- 日明细的稳定排序方向。
- 无行驶日期是否返回 0 km 条目，还是省略该日期。
- 服务端时间区间是闭区间、开区间还是半开区间。
- 当前风控、签名版本和客户端版本校验要求。
- 共享车辆是否拥有该接口权限。

## 12. 源码证据

- 接口总表：`base_restored/API_WORKFLOWS.md:28`
- URL 静态解码：`base_restored/decoded_network_strings.json:153`
- URL 常量：`base_restored/jadx/sources/p000/wu1.java:53`
- 业务参数与签名查询参数：`base_restored/jadx/sources/p000/zd1.java:252`
- 请求头和 GET 构造：`base_restored/jadx/sources/p000/zd1.java:281`
- 响应 `data/detail` 解析：`base_restored/jadx/sources/p000/zd1.java:183`
- `day/accumulatedMileage` 解析：`base_restored/jadx/sources/p000/zd1.java:328`
- 网络层保留最后 8 条：`base_restored/jadx/sources/p000/zd1.java:341`
- 上海时区：`base_restored/jadx/sources/p000/sk2.java:31`
- 7 天前零点算法：`base_restored/jadx/sources/p000/sk2.java:502`
- 实际调用时间范围：`base_restored/jadx/sources/p000/sk2.java:573`
- 页面取最后 7 条：`base_restored/jadx/sources/p000/xl0.java:324`
- 页面求和并显示：`base_restored/jadx/sources/p000/xl0.java:365`
- 图表取最后 7 条：`base_restored/jadx/sources/p000/xl0.java:440`

## 13. 给实现 Agent 的最终要求

请先根据目标仓库的现有结构确定 API、Repository/Service、状态管理和 UI 的归属文件，再实施改动。若目标仓库已有 `v3DoubleSignGet`、旧车主服务签名或相同字段的请求，请直接复用并补测试。若没有完整签名能力，不要猜测补齐；先报告缺失的会话字段或签名依赖，并给出代码证据和最小阻塞项。
