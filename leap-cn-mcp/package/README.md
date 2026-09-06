# Leap CN MCP

`leap-cn-mcp` 是零跑中国 App 链路的 MCP stdio 服务，供支持 MCP 的 AI 客户端调用。npm 包只发布 MCP 入口和它运行所需的核心 SDK 文件。

本仓库目录还保留了本地调试用 CLI、SDK 示例和 HTML demo，包含：

- 发送短信验证码
- 验证码登录
- 旧登录 token 换新网关 `accessToken/signKey`
- 查询车辆列表、车辆路由、车况、电池、里程、门锁、车窗、位置、空调状态
- 构造/发送控车命令和查询控车结果

本地实现分三层：

```text
src/leapmotor-cn-sdk.js   SDK 核心，供 CLI、服务或后续 MCP 调用
bin/leap-cn.js            CLI，默认 JSON 输出，适合 AI/脚本调用
server.js + public/       原 HTML demo，保留给人工调试
```

## CLI

```bash
cd /Users/qian/project/lingpao/demo
npm run cli -- session
npm run cli -- vehicles
npm run cli -- state
npm run cli -- mileage
npm run cli -- location
npm run cli -- overview
```

登录：

```bash
npm run cli -- send-sms --phone <手机号>
npm run cli -- login --phone <手机号> --code <验证码>
```

车辆选择：

```bash
npm run cli -- vehicles
npm run cli -- select --vin <VIN> --car-type S01
```

远控预览，不会触发真实车辆动作：

```bash
npm run cli -- control-preview --command lock
npm run cli -- control-preview --command window --value 0
npm run cli -- control-preview --command acOn
```

真实远控需要同时给出两个安全开关：

```bash
npm run cli -- control-send --command lock --allow-write --confirm
```

如果命令需要操作密码，CLI 提供两个等价参数：

```bash
npm run cli -- control-send --command unlock --pin <操作密码> --allow-write --confirm
npm run cli -- control-send --command unlock --operation-password <操作密码> --allow-write --confirm
```

查询控车结果：

```bash
npm run cli -- control-query --msgID <msgID>
```

支持的远控命令可查看：

```bash
npm run cli -- commands
```

常用命令名：

```text
lock, unlock, trunk, trunkOpen, trunkClose, frunkOpen, frunkClose,
horn, batteryPreheat, batteryPreheatOff,
window, windowClose, windowVent,
sunshadeOpen, sunshadeClose, sunroofOpen, sunroofClose,
ac, acOn, acOff, quickCool, quickHeat, windshieldDefrost, custom
```

`custom` 示例：

```bash
npm run cli -- control-preview \
  --command custom \
  --cmdid 170 \
  --state-json '{"operate":"manual","temperature":"24","windlevel":"3","mode":"cold","circle":"in","wshld":"0","position":"all"}'
```

## SDK

```js
import { LeapmotorChinaClient } from './src/leapmotor-cn-sdk.js';

const client = new LeapmotorChinaClient({
  sessionFile: '/Users/qian/project/lingpao/demo/.leap-session.json'
});

await client.loadSession();
const vehicles = await client.listVehicles();
const status = await client.getVehicleState();
const preview = await client.previewControl({ command: 'lock' });
```

真实远控默认禁用。SDK 层需要：

```js
const client = new LeapmotorChinaClient({ allowWrite: true });
await client.sendControl({
  command: 'unlock',
  operationPassword: '<操作密码>',
  confirm: true
});
```

## MCP

给 AI 调用时，推荐使用 MCP stdio 服务：

```bash
npx --yes --package leap-cn-mcp leap-cn-mcp
```

本仓库内的 MCP 入口文件：

```text
bin/leap-cn-mcp.js
```

示例 MCP 配置：

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

`LEAP_SESSION_FILE` 请改成你自己的本地 session 文件路径。`LEAP_OPERATION_PASSWORD` 可选；tool 参数里的 `operationPassword` 优先级更高。
配置 `LEAP_OPERATION_PASSWORD` 后，MCP 会从远控工具 schema 中隐藏 `operationPassword`，避免客户端重复询问；修改环境变量后需要重启或重新加载 MCP 服务。

S01 关闭车窗已按 App 真实请求适配为 `cmdid=230`、`state={"value":"10"}`。所有真实控车发送都要求操作密码，纯查询和 `leap_control_preview` 不需要；MCP 启动环境建议配置 `LEAP_OPERATION_PASSWORD`，否则发送工具会在请求前给出明确错误。

S01 2020 真实测试确认车窗值为 0–10 的位置刻度（仅两个前窗参与）：`windowOpen=0` 最大打开、`windowVent=8` 微开、`windowClose=10` 关闭。自定义 `window --value <0..10>` 可用于中间位置；后窗开度在该车上始终为 0。

MCP 的 `leap_control_preview` 和 `leap_control_send` 提供 `windowPosition` 整数参数。调用时使用 `command: "window"`；旧的 `value` 参数继续兼容，但同时提供时以 `windowPosition` 为准。

`leap_state` 返回 `raw`、`summary` 和 `fieldDefinitions`。智能体必须以 `fieldDefinitions` 中的 `meaning`、`unit`、`valueMeaning`、`confidence` 为准；`confidence=unknown` 的字段不得自行猜测枚举或单位。接口会为当前 `signalMap` 的每一个字段生成定义，尚未整理的新增车型字段也会明确标记为原始透传/待验证。

MCP tools：

```text
leap_session
leap_refresh_token
leap_refresh_old_token
leap_send_sms
leap_login_sms
leap_vehicles
leap_select_vehicle
leap_route
leap_state
leap_mileage
leap_location
leap_overview
leap_commands
leap_control_preview
leap_control_send
leap_control_query
```

MCP 真实远控比 CLI 更保守，需要同时满足：

```text
1. 启动 MCP 服务时设置 LEAP_ALLOW_WRITE=1
2. tool 参数 allowWrite=true
3. tool 参数 confirm=true
```

不满足任一条件都会拒绝发送。`leap_control_preview` 永远只生成 dryRun 摘要。

## Web Demo

Web demo 现在也复用同一个 SDK 文件：

```text
server.js -> src/leapmotor-cn-sdk.js
```

页面里的操作密码输入框会透传为 SDK 的 `operationPassword`，由 SDK 加密成 `oppwd` 后发送。页面仍要求勾选确认后才会触发真实控车。

## 运行

```bash
cd /Users/qian/project/lingpao/demo
npm start
```

默认地址：

```text
http://localhost:8787
```

可选环境变量：

```bash
PORT=8787 LEAP_DEVICE_ID=<deviceId> LEAP_APP_VERSION=1.22.87 LEAP_SUB_VERSION=3.19.2-2 npm start
```

CLI 也支持：

```bash
LEAP_SESSION_FILE=/Users/qian/project/lingpao/demo/.leap-session.json npm run cli -- session
LEAP_ALLOW_WRITE=1 npm run cli -- control-send --command lock --confirm
```

`deviceId` 注意事项：

```text
LEAP_DEVICE_ID 只应在首次登录/新 session 时指定。
如果 session 文件已存在，SDK 会优先使用 session 内保存的 deviceId。
不要把已有 session 里的 deviceId 手工替换成新值。
```

实测把现有 session 的 `deviceId` 改成随机值后：

```text
车辆列表/实时状态：仍返回 code/result=0
旧 carownerservice 里程接口：返回 result=39 信息校验失败
```

远控也依赖旧签名链路，因此换 `deviceId` 后应重新短信登录，不要复用旧 session。

## 安全说明

- 短信验证码接口会在服务端按 App 规则将手机号 RSA 加密为 `phoneNo` 后发送，不落盘保存手机号。
- token、signKey、车辆选择会保存到本地 `.leap-session.json`，用于重启后自动恢复登录态。
- 新网关 access token 剩余不足 5 分钟时，SDK/MCP 会使用 refresh token 自动续期；遇到明确的鉴权失效响应时会续期并重试一次。`leap_refresh_token` 可用于手动立即刷新。
- 旧 App token（远控/里程/位置签名）会按登录返回的 `tokenExpired` 计时；剩余不足 60 秒或遇到 `result=39` 时，SDK 会调用 `GET /app-user/appuseroperate/getnewtoken` 自动续期（参数 `accountId` + RSA `accountNumber` + 旧 `refreshToken` 头）。续期成功后常见 `tokenExpired=21600`（约 6 小时）。需要 session 内已有 `phone`（短信登录时写入）或手动 `refresh-old-token --phone` / `leap_refresh_old_token`。
- `.leap-session.json` 已加入 `.gitignore`，文件权限按 `600` 写入；不要提交或分享。
- 页面上的“重置会话”会同时清空内存并删除 `.leap-session.json`。
- 登录接口和调试输出不返回完整 token/signKey。
- CLI 的 `control-preview` 不会触发真实车辆动作。
- CLI 的 `control-send` 必须同时提供 `--allow-write` 和 `--confirm`。
- SDK 的 `sendControl()` 必须 `allowWrite=true` 且 `confirm=true`。
- Web demo 控车接口会触发真实车辆动作，页面要求勾选确认后才会发送。
- 操作密码如填写，会按 App 规则使用旧 token 加密成 `oppwd` 后发送。

## 验证结果

当前 session 下已用 CLI 验证：

```text
npm run cli -- vehicles   -> code/result=0
npm run cli -- state      -> code/result=0，summary 可读电池、门锁、车窗、空调、经纬度
npm run cli -- mileage    -> code/result=0
npm run cli -- location   -> code/result=0，当前响应 data=null；经纬度以 state summary 为准
```

真实远控没有执行。仅验证了：

```text
control-preview 只生成 dryRun 请求摘要
control-send 缺少 --allow-write 会拒绝
control-send 缺少 --confirm 会拒绝
```

## 主要源码

```text
src/leapmotor-cn-sdk.js
bin/leap-cn.js
server.js
public/index.html
public/app.js
public/styles.css
```
