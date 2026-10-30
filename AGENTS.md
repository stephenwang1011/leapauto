<!-- CODEGRAPH_START -->

## CodeGraph

If a `.codegraph/` directory exists at the repository root, use CodeGraph before
`rg`, `find`, or direct source reading when locating code or understanding call
paths. The shell fallback is:

```powershell
codegraph explore "<symbol names or question>"
```

This checkout currently has no `.codegraph/` directory, so normal repository
inspection tools are the source-navigation fallback.

<!-- CODEGRAPH_END -->

# LeapAuto Repository Instructions

## Project Profile

LeapAuto is a single-module Kotlin Android application built with Jetpack Compose.
It is an unofficial, personal-use client for verified Zero Run China App vehicle
status and remote-control flows. The package is `com.leapauto.app`, the displayed
application name is `零跑智控`, and the current code baseline is:

- `apkVersionName`: `1.5.50`
- `versionCode`: `1005050`
- `compileSdk` / `targetSdk`: `35`
- `minSdk`: `26`
- Java and Kotlin JVM target: `17`
- Gradle wrapper: `8.11.1`
- Android Gradle Plugin: `8.7.3`
- Kotlin: `2.1.0`

The source code, `API/API.md` (and the `API/` knowledge base), and the current tests are the authority for implemented
behavior. `功能规划.md` and `车辆位置未完成功能.md` describe roadmap and follow-up
work; do not describe a planned item as available unless the code and tests support
it. Do not edit generated files under `app/build/`, `.gradle/`, or `.kotlin/`.

## Repository Map

| Area                                                                         | Current responsibility                                                                                                                                                                                                   |
| ---------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| `app/src/main/java/com/leapauto/app/MainActivity.kt`                         | Activity lifecycle, login, status refresh, vehicle-location snapshot handling, remote-control orchestration, session expiry, version checks, and Compose state wiring.                                                   |
| `app/src/main/java/com/leapauto/app/ui/LeapAutoScreen.kt`                    | Login, 我的座驾, 车况, 充电与续航, 车辆位置, 空调, 支持作者, 我的, and signal-debug Compose screens.                                                                                                                                            |
| `app/src/main/java/com/leapauto/app/ui/theme/`                               | Light/dark theme, typography, colors, and local theme state.                                                                                                                                                             |
| `LeapmotorApi.kt`                                                            | Blocking HTTP client for the old app-user service and new gateway, request signing, login, token refresh, vehicle list/route lookup, signal reads, command submission, and result polling. Call it from a worker thread. |
| `Models.kt`                                                                  | Session/auth models, control command models, climate-control state, optimistic UI policies, and the verified command presets.                                                                                            |
| `SignalTable.kt`                                                             | Numeric `signalMap` ID to named-field decoding. Only verified IDs may be used for user-facing behavior.                                                                                                                  |
| `VehicleStatusMapper.kt`, `ChargeStatus.kt`, `EnergyStatusFormatter.kt`      | Shared vehicle model selection, range/SOC mapping, charging-state interpretation, power calculation, and display formatting.                                                                                             |
| `VehicleLocation.kt`                                                         | Coordinate selection, validation, freshness, stale/expired handling, jump rejection, and external-map navigation state gates.                                                                                           |
| `ExternalMapLauncher.kt`                                                     | Explicit user-triggered handoff of a trusted vehicle coordinate to an installed external map application.                                                                                                               |
| `SessionStore.kt`, `SecureValueStore.kt`, `Crypto.kt`, `SessionExpiry.kt`    | App-private persistence, Android Keystore encryption for credentials/PIN, protocol cryptography, session generation invalidation, and expiry classification.                                                             |
| `ControlWidget.kt`, `WidgetSyncWorker.kt`                                    | Home-screen widget rendering, WorkManager synchronization, local snapshot fallback, cadence changes, and widget click routing. The current layout displays status text, not the cached charging-power value.             |
| `ControlService.kt`, `ControlConfirmActivity.kt`, `WidgetControlSecurity.kt` | Foreground-service widget commands, notification feedback, and optional biometric/operation-password confirmation for sensitive widget actions.                                                                          |
| `ChargeNotificationManager.kt`                                               | Local notifications for charging completion, interruption, and fault transitions.                                                                                                                                        |
| `VersionUpdate.kt`, `AppReleaseInfo.kt`, `ExternalLinks.kt`                  | Public PGYER HTML update check, release notes shown in-app, and feedback/download links.                                                                                                                                 |
| `app/src/main/res/`                                                          | Compose support drawables, authorized vehicle images, launcher PNGs, widget XML/layouts, themes, colors, and strings.                                                                     |
| `design/零跑智控-设计规范.md`                                                | 全局设计规范：智能座舱级·真微晶毛玻璃（Liquid Crystal Glassmorphism）规范，涵盖双对角材质、极光折射、边框高光、排版字号、弹窗抽屉与桌面小组件标准。所有界面开发必须严格遵守。 |
| `API/`                                                                       | Consolidated API documentation: `API/API.md` (verified spec).                                                                                            |
| `scripts/package-release.ps1`                                                | Signed Release packaging with a hard 10 MiB delivery-size gate.                                                                                                                                                          |
| `scripts/publish-pgyer.ps1`                                                  | Local signed Release packaging and PGYER CLI upload, with environment-first API key resolution, release-note fallback, and idempotent version deduplication. Also used by CI publishing. |

The app has no embedded map SDK. Vehicle-location navigation is delegated to an
installed external map only after an explicit user action.

## Implemented User Journeys

### Login and session

1. The user requests an SMS code with an 11-digit phone number.
2. SMS login obtains the legacy app token, exchanges it for the new gateway access
   token/sign key, lists vehicles, selects the first vehicle, and resolves its
   `appRegion` route.
3. The app refreshes vehicle status after login and automatically refreshes tokens
   near expiry. A terminal authentication failure returns the user to login.
4. Logout clears local session credentials, operation PIN, selected vehicle state,
   and the activity-only vehicle-location snapshot. Device preferences remain
   separate from the session.

### 我的座驾 and detail pages

The logged-in navigation is `爱车`, optional `支持作者`, and `我的`. The support tab
is hidden by default and can be enabled in 我的. The home page contains:

- A model-specific vehicle hero image. Tapping the image opens `车况`.
- Remaining range and battery status. Tapping this panel opens `充电与续航`.
- A paged quick-control surface. The first page exposes unlock, lock, window vent,
  and trunk actions; additional pages expose window, horn, and battery-preheat
  commands.
- Climate and vehicle-location summary cards. They open `空调` and `车辆位置`.

The detail pages currently include:

- `车况`: lock state, climate state, total mileage, cabin temperature, driving
  state, and four-wheel tire pressure/status where the signal is available.
- `充电与续航`: range, SOC, range mode, charging state/type, calculated charging
  power, remaining time, voltage/current, minimum battery temperature, healthy
  charging, and full-charge state.
- `空调`: on/off, 16-32 C integer temperature control, quick cooling, quick heat,
  and windshield defrost. Commands use the existing climate safety and optimistic
  state policies; telemetry remains the source of truth after refresh.
- `车辆位置`: validated current location status and update time, plus an explicit
  handoff to an installed external map application. No in-app map or address is shown.
- `我的`: operation password, appearance mode, widget opacity, widget sensitive
  action verification, signal debugging, support-tab visibility, charge
  notifications, release notes/update check, and logout.

### Vehicle location boundary

The current location path is deliberately narrow:

- `SignalTable` preserves raw signal IDs `2` and `3`; `VehicleLocationDomain`
  interprets `2` as longitude and `3` as latitude, with `sts` used as the source
  timestamp.
- The coordinates are checked for numeric/range/zero validity and obvious jumps.
  Freshness is `fresh` through 5 minutes, `stale` through 30 minutes, and
  `expired` through 60 minutes by the current default policy.
- The last trusted snapshot is held in `MainActivity` memory only. Raw coordinates
  are not written to `SessionStore`, the widget snapshot, logs, or release docs.
- No map tiles or reverse geocoding are loaded in-app. Raw coordinates and addresses
  are not cached; startup removes the legacy `vehicle_location_cache` preference.
- A coordinate is handed to an installed external map only after the user taps
  navigation and chooses an app. Do not add background tracking or sharing implicitly.

Do not infer a WGS-84/GCJ-02 conversion outside the code's verified contract. The
current verified path marks the accepted signal-map coordinates as `GCJ02`; any
change to this assumption requires evidence in `API/API.md`, tests, and product review.

### Remote-control safety

`Commands.build()` currently covers lock/unlock, trunk, frunk, window, sunshade,
horn, battery preheat, AC on/off, custom AC temperature, quick cooling, quick heat,
and windshield defrost. All API command requests require the locally stored
four-digit operation password, which is encrypted with an install-local Android
Keystore key and used only to build the protocol payload.

The main app reports sending, completion, timeout/no-response, and failure states.
For commands that return a polling ID, the main app waits 1 second before the first
query and then polls every 0.5 seconds within the configured time budget. AC command
`cmdid=170` uses the same result-query path when the accepted POST returns a polling
ID, then refreshes vehicle telemetry before reporting completion. Older vehicle
responses without a polling ID retain the accepted-POST compatibility path and are
reported as awaiting vehicle-state confirmation rather than confirmed complete.

The widget uses a foreground service. By default, widget unlock and trunk-open
actions pass through `ControlConfirmActivity`, which offers strong biometric
authentication or the four-digit operation password, with a three-attempt limit and
a short confirmation timeout. This gate can be disabled in 我的; changing that
setting must not remove the in-app operation-password requirement.

Never send a real vehicle command, SMS, or external location request as a test
without explicit user authorization. A successful HTTP response means the service
accepted the command; only a verified result poll or later vehicle refresh may
support stronger wording.

## API and Protocol Knowledge Base (`API/`)

用户已明确停用壹跑（1PAO）参考资料：后续排查、实现与协议验证不得引用其文档、反编译代码或兼容实现作为依据，包括外部 `hackapp/analysis/payload_src`。协议判断应依据官方资料、经授权的官方抓包及本项目可追溯的实车验证；历史可用功能应优先排查本项目回归。

All verified API specifications, protocol references, reverse-engineering notes, and third-party libraries are consolidated under the root `API/` directory. Future agents must search and consult `API/` before researching, implementing, or modifying any API, telemetry signal, command payload, or protocol behavior:

| Path | Category | Content and Agent Guidance |
| --- | --- | --- |
| `API/API.md` | Core Verified Spec | **Single source of truth** for LeapAuto's verified API endpoints, request signing (old MD5 & new HMAC-SHA256), `signalMap` ID-to-name mapping table, vehicle remote command IDs, result polling rules, and verified protocol constraints. |
| `API/leap-api/` | Python Client Library | Clean-room Python implementation of the Leapmotor API (`markoceri/leapmotor-api`). Key reference files:<br>• `未实现指令清单.md`: Gap analysis of 30 commands (seats, heating, ventilation, auto-park, etc.) between China and overseas implementations.<br>• `API分析.md`: Full architecture, mutual TLS client certs, endpoints, and error handling.<br>• `docs/api.md`: Comprehensive `cmd_id` specs, JSON body formats, and scheduler payloads.<br>• `docs/vehicles.md`: Supported models, features, and configurations. |
| `API/hack_lingpao_app/` | China App Reverse Engineering | Research notes from decompiling China Android App v1.22.93 (`cqrg/hack_lingpao_app`). Key reference files:<br>• `API_REFERENCE.md`: China App endpoint catalog (appuser, carownerservice, signal query, etc.).<br>• `APP_PROTOCOL.md`: Request signing, RSA phone encryption, AES operation PIN encryption.<br>• `REVERSE_NOTES.md`: 360 packer unpacking, frida-dexdump, smali analysis.<br>• `lingpao_client.py`: Reference Python client implementation. |
| `API/leap-cn-mcp/` | MCP Server & Node.js SDK | Model Context Protocol server and JavaScript SDK (`leap-cn-mcp@0.1.7`). Key reference files:<br>• `leap-cn-mcp-commands.md`: Command specifications, parameter rules, and vehicle differences.<br>• `package/src/leapmotor-cn-sdk.js`: Field definitions, telemetry parsers, and command builder. |

When adding or verifying any vehicle command, signal ID, or protocol flow:
1. Always search `API/` first to locate verified parameters, field definitions, and payloads.
2. When a signal is confirmed, update `SignalTable.kt`, the consuming model/formatter, tests, and `API/API.md` together.
3. When a command is confirmed, update `Models.kt`, confirmation/feedback behavior, tests, and `API/API.md` together.

## Collaboration and Ownership

The **项目经理** is the coordination owner. New work should be routed to one
specialist when the scope is clear, or to **产品经理** first when the user journey,
acceptance boundary, or data dependency is unclear. Do not create overlapping
implementation work when an owned specialist already covers it.

| Role              | Owned scope                                                                                                                                                   | Required handoff                                                                                                                                                                          |
| ----------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **产品经理**          | Requirements analysis and product acceptance: journeys, entry points, priority, API/data dependencies, privacy boundary, and acceptance criteria.             | Buildable task breakdown, open decisions, acceptance criteria, and evidence-based acceptance result. Does not authorize sensitive API calls, vehicle commands, or release publishing.     |
| **\[爱车]开发工程师**    | Shared 爱车 home layout, vehicle hero, range entry, quick controls, and cross-module home interactions.                                                         | Changed files, affected control/security path, build result, and device/emulator evidence when available.                                                                                 |
| **\[车况]开发工程师**    | 车况 detail page, lock/climate presentation, odometer/cabin temperature, four-wheel pressure, and related signal mapping.                                       | Changed files, signal/presentation semantics, build and regression evidence, and device/emulator evidence. Does not change remote commands or unrelated home layout without coordination. |
| **\[充电与续航]开发工程师** | Range and charging detail page, energy fields, charging status/power/time/type, battery temperature, healthy charging, and related mapping.                   | Changed files, energy semantics, build and regression evidence, and device/emulator evidence.                                                                                             |
| **\[车辆位置]开发工程师**  | Location summary/detail, coordinate validation/freshness, external-map handoff, and privacy UX.                                                              | Changed files, source/freshness semantics, map/privacy impact, build/test evidence, and device/emulator evidence. No background location sharing or navigation by default.                |
| **\[空调]开发工程师**    | Climate detail page, climate work-mode/circulation presentation, AC commands, and related control feedback.                                                   | Changed files, signal/command semantics, control-safety impact, build/test evidence, and coordination notes for the home climate card and vehicle-status display.                         |
| **\[我的]开发工程师**    | Account/session, operation password, appearance, widget preferences, charge notifications, version information, feedback, support-tab visibility, and logout. | Changed files, session/PIN impact, build result, and regression notes.                                                                                                                    |
| **\[桌面插件]开发工程师**  | Widget UI, WorkManager cadence, background status synchronization, widget commands, feedback, opacity, and light/dark readability.                            | Changed files, refresh behavior, widget evidence, and device-specific residual risks.                                                                                                     |
| **\[支持作者]开发工程师**  | Support page/reminder, local reminder suppression, payment-code display, and any explicitly authorized payment/order integration.                             | Changed files, payment/privacy impact, build result, and confirmation that unrelated vehicle/account/API behavior was unchanged.                                                          |
| **数据分析师**         | Read-only signalMap interpretation, telemetry quality/freshness, command timing, and anonymized evidence.                                                     | Redacted scope, reproducible query/script, findings, confidence/limits, and follow-up owner. Does not change behavior, send commands, publish, or retain raw personal data.               |
| **测试工程师**         | Independent normal, failure, recovery, security, regression, automated, and available device/emulator coverage.                                               | Test record with scope, commands/outcomes, device evidence, defects, residual risks, and unexecuted coverage. Testing does not make product acceptance.                                   |
| **运维工程师**         | Version increment, signed Release-only packaging, signature verification, PGYER publishing, and public-page verification.                                     | Version/code, signed APK path, signature schemes, update notes, public-page result, and publishing failures. Keep credentials local.                                                      |

### Routing rules

- Route any change to `SignalTable`, signal meanings, freshness, command/result
  timing, or raw API interpretation through **数据分析师** before behavior changes.
- A specialist may change shared `Models.kt`, `LeapmotorApi.kt`, `SignalTable.kt`,
  theme, or resources only when required by the owned feature. Name those shared
  files in the handoff and coordinate affected owners.
- Documentation-only, planning-only, analysis-only, and acceptance-only work does
  not trigger Release packaging.
- Release packaging is user-triggered only: run the Release packaging workflow only
  after the user explicitly asks for a package, Release APK, installation package,
  or equivalent delivery artifact. Completing an implementation, test, product
  acceptance, or documentation task does not by itself authorize or trigger a
  package.
- Release packaging requires implementation evidence, passing tests for the affected
  modules, and product acceptance. Reuse applicable completed test results; packaging
  does not trigger the full suite. Run the full unit-test suite only when the user
  requests committing or pushing code to a repository, and require it to pass before
  committing or pushing, as defined below.
  External PGYER publication still requires explicit user authorization.
- Handoff order is: product requirements/acceptance criteria -> specialist
  implementation -> testing evidence -> product acceptance -> release packaging or
  publishing when requested -> project-manager consolidated report.
## UI and Design Rules (UI与设计规范强制约束)

**所有界面、卡片、弹窗、抽屉与桌面小组件的新增与重构开发，必须严格参考并遵守项目设计规范：**
👉 **`design/零跑智控-设计规范.md`**（智能座舱级·真微晶毛玻璃 Liquid Crystal Glassmorphism 规范）。

### 核心执行细则：
- **微晶毛玻璃材质与环境极光**：所有卡片容器必须使用 `Modifier.frostedGlassCard(...)`（内置双对角微晶磨砂渐变、0.5dp 顶层折射晶线与底层环境极光），边框统一使用三阶高光切边 `glassCardBorder()`；弹窗与半屏抽屉必须使用高对比度纯实色背景（`solidDialogModifier()` / `pageBgColor`），严禁透明化以杜绝背景杂光干扰文字阅读；
- **排版与尺寸统一对齐**：核心续航大字 `26sp Bold`、单位 `12sp Bold`、电量百分比 `13sp Bold`（间距 `2~3dp`），纯电续航能量槽统一为 `110dp × 4.5dp`（圆角 `2dp`）；
- **中性色与状态色约束**：基础界面使用黑白灰，`MaterialTheme.colorScheme.primary` 在浅色模式为 `#303030`、深色模式为 `#E4E4E4`，对应 `onPrimary` 分别为白色和 `#202020`；背景、卡片与选中底色采用中性灰白，不再使用蓝色强调、蓝色渐变或蓝色光晕。`MaterialTheme.statusGood`（能量绿 `#00C853`）用于已确认开启、正常和成功状态，警告红（`#FF3B30`）用于异常，橙色用于提醒。状态未确认、发送中、等待回包或仅本地选中时不得标为已生效。使用主题语义令牌并保持 `GlassSurfaceStyleTest` 通过；
- **严禁脏黑阴影与厚重色块**：保持 `shadowElevation = 0.dp`，不使用纯色无渐变大灰块或未羽化的生硬遮罩；
- **性能与轻量化底线**：严禁引入外部重度实时高斯模糊库，100% 采用纯 Compose Canvas/Brush 硬件加速高效渲染，保持 10 MiB 包体限制与老设备 60/120 FPS 流畅度。

The current baseline is a neutral-grey background and restrained translucent information surfaces.
Primary actions use neutral `#303030` with white content in light mode and
`#E4E4E4` with `#202020` content in dark mode. Use Energy Green `#00C853` for
confirmed enabled, healthy, and success states, Alert Red `#FF3B30` for faults
and security warnings, and amber for reminders. Pending, unknown, and locally
selected states must remain distinguishable from confirmed active states.
Use linear icons, restrained borders, stable touch targets, and text plus icon/color
for safety states. Keep light and dark theme behavior intact. Remove blue UI accents
without recoloring authorized vehicle photos, theme backgrounds, or 3D assets.

Do not introduce saturated gradients, orange as a primary color, heavy real-time
background blur, neon effects, copied screenshots/branding, unlicensed vehicle
imagery, marketing banners, or decorative UI that competes with vehicle state.
Restrained semi-transparent cards may use a 1dp soft border and very low elevation,
but must preserve contrast in both themes. Existing authorized vehicle PNGs and
line-icon resources are the asset source. Preserve operation-PIN, biometric,
disabled, loading, error, and result-feedback behavior during visual work.

## Build and Test

Run commands from `D:\young\work\leapauto` in PowerShell:

```powershell
.\gradlew.bat :app:assembleRelease       # Compile/validation build
.\gradlew.bat :app:testDebugUnitTest     # Full suite before user-requested code commit/push
.\gradlew.bat :app:lintRelease           # UI/resource/manifest/config checks
.\scripts\package-release.ps1            # Signed 3D Release delivery APK
```

The repository currently contains 40 JVM test files under `app/src/test/`, covering
commands, status/range/charge parsing, location validation, session expiry, version
updates, navigation, appearance, support reminders, and widget mapping/security.
There is currently no checked-in `app/src/androidTest/` source set. A build passing
does not replace device/emulator inspection for Compose, widgets, notifications,
biometric prompts, map rendering, login, or real vehicle behavior.

For a UI or configuration change, run only the relevant module tests using
`:app:testDebugUnitTest --tests '<relevant test class or pattern>'`, plus
`:app:assembleRelease` and `:app:lintRelease` as appropriate. For parser/model-only
changes, run the relevant unit tests and `:app:assembleRelease`. Routine code changes
and packaging do not require a full-suite run. Record the selected test scope,
commands, results, unavailable device coverage, and residual risk in the handoff.

### Unit Testing Constraints (单元测试强制约束)

- **新功能必须添加单元测试 (New Features Require Unit Tests)**:
  凡是新增功能、数据模型、信号解析、控制指令或业务逻辑，必须在 `app/src/test/` 下同步编写对应的单元测试，覆盖正常路径、边界条件与异常处理分支。
- **修改功能必须同步修改测试 (Modified Features Require Test Updates)**:
  凡是修改既有功能、调整业务逻辑、重构模型或修改 UI 展示映射，必须同步检查、修改并补充现有的单元测试，确保测试用例准确反映最新行为且无任何回归。
- **日常改动只执行相关模块测试**:
  每次代码改动按实际影响范围选择相关模块的测试，通过 `--tests` 指定测试类或模式；不要默认执行全量测试。涉及共享逻辑时覆盖受影响的相关模块。文档修改仅做内容与差异检查。
- **用户要求提交代码时才执行全量测试 (Full Suite Before Code Commit/Push)**:
  只有用户明确要求将代码提交到仓库（包括本地代码提交或推送远程仓库）时，才执行以下全量测试，并在全部通过后进行提交或推送：
  ```powershell
  .\gradlew.bat :app:testDebugUnitTest
  ```
  全量单元测试 100% 通过是代码提交与推送的硬性门禁。测试失败时先修复并重新验证，未通过不得提交或推送。提交前若功能代码、测试、依赖或影响行为的配置又有改动，须取得覆盖最终提交内容的全量通过结果。
- **复用已通过的测试结果，避免重复执行**:
  修改完成后相关模块测试已经通过，且后续代码未变，或仅修改 `apkVersionName`、`versionCode` 和 `AppReleaseInfo.currentReleaseNotes` 时，打包直接复用该结果。打包、安装或发布本身不触发全量测试。
  后续功能代码、测试、依赖或影响行为的配置有改动时，只补跑受影响模块的测试；用户同时要求代码提交时，适用上述全量门禁。
  复用时核对后续差异，并记录所复用的测试命令、结果和覆盖的代码状态；结果缺失或无法确认覆盖当前代码时，补跑相关模块测试。此规则不免除必要的 Release 构建、签名校验和安装验证，也不自动修改现有 CI 流程。

Never use a Debug APK as a deliverable. A user-requested package delivery builds
the signed Release variant and copies it to:

```text
app/build/distributions/release/零跑智控-<versionName>.apk
```

The `packageApks` Gradle task remains a compatibility alias for the signed Release
delivery APK. Use `scripts/package-release.ps1` for the standard delivery workflow.

## Versioning and Release

Before every user-requested delivery batch, update both `apkVersionName` and `versionCode` in
`app/build.gradle.kts`, and update `currentReleaseNotes` in
`app/src/main/java/com/leapauto/app/AppReleaseInfo.kt`. Use a three-part version:

```text
versionCode = major * 1_000_000 + minor * 1_000 + patch
```

Treat `1.0` as `1.0.0`; increment the patch for each package and roll `1.0.10` to
`1.1.0` according to the repository's established convention.

Release signing is conditional on these local-only `local.properties` keys:
`RELEASE_STORE_FILE`, `RELEASE_STORE_PASSWORD`, `RELEASE_KEY_ALIAS`, and
`RELEASE_KEY_PASSWORD`. No embedded map key or map AAR is required.

Do not package after every completed task. The project manager may coordinate
implementation, testing, and product acceptance without invoking the operations
release workflow; only an explicit user packaging request starts that workflow.

Before handoff, verify the actual APK rather than trusting the Gradle signing
intent:

```powershell
apksigner verify --verbose app/build/distributions/release/零跑智控-<version>.apk
jarsigner -verify -certs app/build/distributions/release/零跑智控-<version>.apk
```

Report the signature schemes actually present. Keep keystores and passwords local
or in CI secrets. Use the [蒲公英 Android signing guide](https://www.pgyer.com/doc/view/android_signing)
for signing compatibility requirements.

The integrated PGYER entrypoint is:

```powershell
.\scripts\publish-pgyer.ps1
.\scripts\publish-pgyer.ps1 -UpdateDescription "更新内容"
.\scripts\publish-pgyer.ps1 -SkipBuild -UpdateDescription "已验证的更新内容"
.\scripts\publish-pgyer.ps1 -SkipBuild -ApkPath "app/build/distributions/release/零跑智控-<version>.apk" -Force
```

The script resolves `PGYER_API_KEY` from the environment first and falls back to
`local.properties`; it requires the `pgyer` CLI (`npm install -g @pgyer/cli`),
uploads only the standardized signed Release APK, and restores the process
environment after upload. When `-UpdateDescription` is omitted it falls back to
`PGYER_UPDATE_DESCRIPTION` and then to `currentReleaseNotes` in
`AppReleaseInfo.kt`. Publishing is idempotent: if the shortcut already exposes
the local `apkVersionName`/`versionCode`, the upload is skipped unless `-Force`
is passed. Verify the public page `https://www.pgyer.com/lingpaozhikong` after
an authorized publish. Never print API keys, User Keys, signing passwords,
tokens, VINs, PINs, raw responses, or locations in documentation or logs.

### GitHub Actions PGYER publishing

`.github/workflows/release.yml` publishes the signed Release APK to PGYER after
the unit-test gate, the packaging step, the signature verification, and the
GitHub Release creation succeed. The workflow installs `@pgyer/cli@0.2.0`
(pinned, because `0.1.x` resets the install type to public) and calls
`.\scripts\publish-pgyer.ps1 -SkipBuild -ApkPath <apk> -Shortcut lingpaozhikong`.

Required repository secrets:

| Secret | Purpose |
| --- | --- |
| `RELEASE_STORE_FILE_BASE64`, `RELEASE_STORE_PASSWORD`, `RELEASE_KEY_ALIAS`, `RELEASE_KEY_PASSWORD` | Release signing material |
| `PGYER_API_KEY` | PGYER upload credential; when it is missing the workflow warns and skips the publish step |

The publish step only runs for a signed build, skips silently when the same
version is already published, and fails the job on upload errors so the run can
be retried safely. CI retains its configured full-suite gate; local packaging and
publishing use the affected-module test rule above. Release-note independence
applies to both workflows.

## Security and Data Boundaries

- `local.properties`, `keystore/`, tokens, refresh tokens, operation PINs, raw API
  responses, and personal vehicle data are local-only and ignored where applicable.
- `SecureValueStore` encrypts phone/auth/PIN values with AES-GCM using an
  install-local Android Keystore key. Non-secret preferences and the widget's last
  known-good snapshot remain in app-private storage.
- Do not persist raw coordinates or reverse-geocoded addresses. Current location
  coordinates stay in activity memory and are shared with an external map only
  after an explicit navigation action.
- Debug-only signal and vehicle-list screens can display or copy raw responses.
  They must keep the existing warning and explicit-copy boundary and must not be
  used to collect or commit real account/vehicle data.
- Network, SMS, vehicle-control, payment, navigation, location sharing, and
  external publication are sensitive actions. Test them with fixtures or pure
  request construction unless the user explicitly authorizes a real operation.
- `API.md` records only verified protocol facts. Unknown signal IDs remain unknown;
  do not turn a speculative mapping into user-facing status or a control command.

## Code Style and Handoff

Use four-space Kotlin indentation, immutable-first state, focused composables, and
`PascalCase` for types/composables, `camelCase` for functions/properties, and lower
snake case for Android resources. When adding a signal, update `SignalTable.kt`, the
consuming model/formatter, tests, and `API.md` together. When adding a command,
update `Models.kt`, confirmation/feedback behavior, tests, and `API.md` together.
Every new feature requires corresponding unit tests; every modified feature requires updating existing unit tests to ensure behavior consistency and prevent regressions.

Every implementation handoff must include:

1. Changed files and affected module/ownership boundary.
2. Behavior and security impact, including PIN/biometric/location implications.
3. Commands run and their outcomes.
4. Device/emulator evidence, or an explicit statement that no device was available.
5. Defects, residual risks, and unexecuted coverage.

When Git is available, keep commits scoped and use concise imperative messages,
preferably Chinese for this project's delivery work. Commit only with explicit
user authorization. Do not reset or discard unrelated user changes.

## 防回归开发约束（所有 Agent 必须执行）

本节适用于主 Agent、受委派的实现者和审核者。目标是开发 A 时保护已完成的 B；
以下要求补充现有测试、安全、授权和交付规则，不替代任何门禁。
纯文档修改执行内容与差异检查即可，不触发应用测试、版本递增或打包。

### 1. 修改前建立影响清单

- 开始功能修改前，向用户简要说明需求、验收条件、计划修改的文件、受影响的旧功能及必须保持的行为。
- 先检查工作区状态和已有差异，区分本次改动与用户已有工作，保留未提交改动。
- 修改共享函数、模型、存储、主题、刷新或队列之前，必须查清调用方；不能只检查当前页面。
- 涉及控车、会话或异步状态时，影响清单必须包含操作密码、登录退出、切换车辆、结果反馈及相关小组件。
- 历史实车可用功能优先对照本项目历史排查回归。缺少证据时明确区分已确认缺陷与假设，禁止猜测替换协议。

### 2. 限制实现范围

- 只修改当前需求必需的代码，禁止顺手重构、批量改名和调整无关行为。
- 优先局部实现；必须修改公共逻辑时，记录修改原因和受影响调用方，并协调对应模块负责人。
- 超出用户确认的行为范围时，先解释影响并征求确认；已有明确授权的范围内不反复索取同意。
- 不得覆盖、回退或删除他人的工作。多个实现者不得同时编辑同一模块；共享文件必须明确责任人。
- 不得通过关闭安全校验、减少结果确认或隐藏错误来制造响应更快、测试通过的表象。

### 3. 先保护旧行为，再修改实现

- 可自动复现的缺陷，先添加或调整行为测试，确认修改前能暴露问题，再验证修复后通过；无法复现时说明原因和替代证据。
- 修改共享逻辑前，检查相关调用方的旧行为测试，缺失的关键覆盖应补齐。
- 测试验证输入与可观察结果，覆盖正常、异常、未知值和关键边界，不只重复实现细节。
- 异步改动必须按影响覆盖：交错回包、重复点击、切车、退出登录、退后台、请求失败与恢复、后台任务和控车并发。
- 不得为通过门禁删除有效断言、跳过失败测试、吞掉异常或无依据地修改预期结果。
- 需求确实改变旧行为或删除功能时，可更新或删除对应测试，但必须说明契约变化，并保留其他旧功能的回归覆盖。

### 4. 区分验证证据

- 源码字符串或结构检查只证明接线与结构，不能证明真实点击、异步执行或界面可用；关键业务必须有行为测试。
- 单元测试验证规则，流程或集成测试验证模块协作，设备验证检查触摸、弹窗、外观、小组件及实际交互。
- 编译和单测通过不能替代实机或实车验证。没有执行的项目必须写明“未验证”，不得宣称已彻底修复或完全无回归。
- 真实短信、控车、支付、定位分享和发布仍需明确授权；不得为了验证自行操作。
- 测试数量和成功率只从实际报告提取，不能沿用上一轮数字；测试证据必须对应最终代码。

### 5. 交付前审核完整差异

- 审核本次交付包含的所有累计改动，包括已暂存、未暂存、新增未跟踪文件和删除文件，不能只审核最后一个功能。
- 核查越界修改、旧行为变化、会话隔离、权限校验、错误处理、回写时序、生命周期、资源释放及测试缺口。
- 逐项核对修改前的影响清单，记录受影响旧功能的回归结果；新增功能通过不等于旧功能通过。
- 审核发现问题后，修复并重跑受影响模块的检查；打包复用有效的相关模块测试结果。只有用户要求代码提交或推送时，才执行全量测试门禁。
- 审核以问题和证据为先，发现项需包含严重性、文件位置、触发条件和影响；未发现问题也须列明验证边界。
- 不得把请求委派、工具调用失败或尚未返回结果的检查记为已审核、已通过；独立审核不可用时明确实际审核方式。

### 6. 阻断条件与自动门禁

- 必需测试失败、已知回归未处理、存在未解决的高严重性缺陷，或关键协议依赖猜测时，不得宣布验收通过、打包或发布。
- 关键验证无法执行时必须说明阻塞原因及影响，不能静默略过；不属于现有硬门禁的未验证项，须由用户知情决定是否接受交付限制。
- 打包、安装、发布按现有用户授权执行；功能完成或文档修改不构成这些操作的自动授权。
- CI 应将全量单测、Release 编译和 lint 作为可执行检查；分支保护应要求相应检查通过后才能合并。
- 修改 CI 或分支保护须有对应授权，且核查实际配置；仅把规则写入本文件不能声称自动门禁已启用。

### 7. 交付记录与稳定基线

- 按现有 Code Style and Handoff 清单交接，并明确回答：改了什么、哪些旧功能受影响、实际验证结果、未验证项及残余风险。
- 对多功能交付记录每项需求、改动范围和对应证据，保持可追溯；未经设备验证不得标记为实机稳定基线。
- 经用户授权提交时按功能或依赖关系组织小而清晰的提交，不混入无关改动；不得自行提交、推送或回滚。

## Android Development Guidelines

- refer to Kotlin Rules.md

## 用户提问与修改交互准则（铁律）

- **先答原因，后征同意**：每次用户提问、咨询问题原因、反馈实车异常或探讨某种现象时，必须先排查并清晰、直接、客观地说明原因与证据。尚不能确定根因时，明确已确认事实、待验证假设和证据缺口，不得为满足流程编造确定结论。严禁在尚未说明分析依据与方案前擅自修改代码或直接自动打包安装。
- **方案确认与授权门禁**：在向用户解释清楚根本原因与分析依据后，应给出拟修改的方案与影响面，并**主动、明确地征求用户同意**（例如：“是否确认按此方案进行修改与验证？”）。
- **用户授权后再动手**：只有在用户明确确认并同意后，才可开始修改代码、运行单元测试门禁与打包安装，严禁未经用户许可抢跑修改。

## 发布约定

- 用户说“发布”时，默认指将最新签名 Release APK 发布到蒲公英；除非用户明确指定其他平台或仅要求打包。
- 用户说“打包”或“3D 包”时，生成唯一受支持的 3D 签名 Release APK。Lite 构建已移除。
- 用户说“发布”时，默认发布最新 3D 签名 Release APK 到蒲公英。
- **打包与发布前测试门禁**：打包或发布前要求受影响模块测试通过，并按“复用已通过的测试结果”规则复用；不得仅因打包或发布执行全量测试。只有用户要求代码提交到仓库时，才执行全量单测并在全部通过后提交或推送。已知测试失败须先处理，不能以缩小测试范围掩盖失败。
- **CI 自动发布到蒲公英**：`.github/workflows/release.yml` 在单测门禁、签名打包、签名校验与 GitHub Release 全部成功后，会自动将签名 Release APK 发布到蒲公英（需在仓库 Secrets 配置 `PGYER_API_KEY`）。仅签名包可发布；线上已是同版本时自动跳过；发布失败会让流水线标红以便重跑。
- **功能与测试同步约束**：
  - 新增功能必须同步添加对应的单元测试；
  - 修改既有功能或业务逻辑必须同步更新已有单元测试，确保测试覆盖度并防止逻辑回归。
- 每次蒲公英发布必须附带简洁、面向用户的更新日志。
- **【更新日志独立性约束（铁律）】**：每次发布的更新内容**仅填写当前版本本次实际新增与优化的内容**，严禁追加、回锅或累加历史版本的更新说明，保持当前版本更新记录的纯粹与准确。
- 更新日志必须使用带编号的条目，并且每条单独换行显示，例如：
  `1. 适配增程车型，显示燃油和纯电续航`
  `2. 优化空调卡片，显示制冷/制热及内外循环状态`
- 发布前确认 Release APK、版本号和发布内容一致；发布后核对蒲公英页面和上传结果。
