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
| `API/`                                                                       | Consolidated API documentation and third-party reference libraries: `API/API.md` (verified spec), `API/leap-api/` (Python SDK & 未实现指令清单), `API/hack_lingpao_app/` (reverse-engineering notes), `API/leap-cn-mcp/` (MCP server & JS SDK). |
| `scripts/package-release.ps1`                                                | Signed Release packaging with a hard 10 MiB delivery-size gate.                                                                                                                                                          |
| `scripts/publish-pgyer.ps1`                                                  | Local signed Release packaging and PGYER CLI upload.                                                                                                                                                                     |

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
- Release packaging requires implementation evidence, test evidence (including a mandatory full pass of all unit tests via `.\gradlew.bat :app:testDebugUnitTest`), and product
  acceptance. External PGYER publication still requires explicit user authorization.
- Handoff order is: product requirements/acceptance criteria -> specialist
  implementation -> testing evidence -> product acceptance -> release packaging or
  publishing when requested -> project-manager consolidated report.
## UI and Design Rules (UI与设计规范强制约束)

**所有界面、卡片、弹窗、抽屉与桌面小组件的新增与重构开发，必须严格参考并遵守项目设计规范：**
👉 **`design/零跑智控-设计规范.md`**（智能座舱级·真微晶毛玻璃 Liquid Crystal Glassmorphism 规范）。

### 核心执行细则：
- **微晶毛玻璃材质与环境极光**：所有卡片容器必须使用 `Modifier.frostedGlassCard(...)`（内置双对角微晶磨砂渐变、0.5dp 顶层折射晶线与底层环境极光），边框统一使用三阶高光切边 `glassCardBorder()`；弹窗与半屏抽屉必须使用高对比度纯实色背景（`solidDialogModifier()` / `pageBgColor`），严禁透明化以杜绝背景杂光干扰文字阅读；
- **排版与尺寸统一对齐**：核心续航大字 `26sp Bold`、单位 `12sp Bold`、电量百分比 `13sp Bold`（间距 `2~3dp`），纯电续航能量槽统一为 `110dp × 4.5dp`（圆角 `2dp`）；
- **座舱三原色约束**：严格使用 `MaterialTheme.colorScheme.primary`（零跑蓝 `#0066FF`）、`MaterialTheme.statusGood`（能量绿 `#00C853`）和警告红（`#FF3B30`），严禁在界面 Compose 代码中硬编码 `"LeapBlue"` 或 `"EnergyGreen"` 字符串，确保 100% 通过 `GlassSurfaceStyleTest` 门禁；
- **严禁脏黑阴影与厚重色块**：保持 `shadowElevation = 0.dp`，不使用纯色无渐变大灰块或未羽化的生硬遮罩；
- **性能与轻量化底线**：严禁引入外部重度实时高斯模糊库，100% 采用纯 Compose Canvas/Brush 硬件加速高效渲染，保持 10 MiB 包体限制与老设备 60/120 FPS 流畅度。

The current baseline is a warm neutral-grey background, restrained translucent information surfaces,
Leap Blue `#0066FF` for brand and primary actions, Energy Green `#00C853` for energy,
healthy, and success states, and Alert Red `#FF3B30` for faults and security
warnings. Use linear icons, restrained borders, stable touch targets, and text plus
icon/color for safety states. Keep light and dark theme behavior intact.

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
.\gradlew.bat :app:testDebugUnitTest     # JVM unit tests
.\gradlew.bat :app:lintRelease           # UI/resource/manifest/config checks
.\scripts\package-release.ps1            # Signed 3D Release delivery APK
```

The repository currently contains 40 JVM test files under `app/src/test/`, covering
commands, status/range/charge parsing, location validation, session expiry, version
updates, navigation, appearance, support reminders, and widget mapping/security.
There is currently no checked-in `app/src/androidTest/` source set. A build passing
does not replace device/emulator inspection for Compose, widgets, notifications,
biometric prompts, map rendering, login, or real vehicle behavior.

For a UI or configuration change, normally run `:app:testDebugUnitTest`,
`:app:assembleRelease`, and `:app:lintRelease`. For parser/model-only changes, run the
relevant unit tests and `:app:assembleRelease`. Record commands, results, unavailable
device coverage, and residual risk in the handoff.

### Unit Testing Constraints (单元测试强制约束)

- **新功能必须添加单元测试 (New Features Require Unit Tests)**:
  凡是新增功能、数据模型、信号解析、控制指令或业务逻辑，必须在 `app/src/test/` 下同步编写对应的单元测试，覆盖正常路径、边界条件与异常处理分支。
- **修改功能必须同步修改测试 (Modified Features Require Test Updates)**:
  凡是修改既有功能、调整业务逻辑、重构模型或修改 UI 展示映射，必须同步检查、修改并补充现有的单元测试，确保测试用例准确反映最新行为且无任何回归。
- **打包前必须全量执行单元测试 (Full Test Suite Must Pass Before Packaging)**:
  在执行任何打包流程（包括运行 `.\scripts\package-release.ps1`、`.\scripts\publish-pgyer.ps1` 或生成 Release 交付 APK）之前，必须**全量执行并通过全部单元测试**：
  ```powershell
  .\gradlew.bat :app:testDebugUnitTest
  ```
  全量单元测试 100% 通过是打包与发布的前置硬性门禁。严禁在测试未执行或存在任何测试失败的情况下进行打包或发布。

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
```

The script reads `PGYER_API_KEY` from `local.properties`, requires the `pgyer` CLI
(`npm install -g @pgyer/cli`), uploads only the standardized signed Release APK,
and restores the process environment after upload. Verify the public page
`https://www.pgyer.com/lingpaozhikong` after an authorized publish. Never print API
keys, User Keys, signing passwords, tokens, VINs, PINs, raw responses, or locations
in documentation or logs.

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

This workspace currently has no `.git` directory. If Git is later initialized, keep
commits scoped and use concise imperative messages, preferably Chinese for this
project’s delivery work. Do not reset or discard unrelated user changes.

## Android Development Guidelines

- refer to Kotlin Rules.md

## 发布约定

- 用户说“发布”时，默认指将最新签名 Release APK 发布到蒲公英；除非用户明确指定其他平台或仅要求打包。
- 用户说“打包”或“3D 包”时，生成唯一受支持的 3D 签名 Release APK。Lite 构建已移除。
- 用户说“发布”时，默认发布最新 3D 签名 Release APK 到蒲公英。
- **打包与发布前测试门禁**：在执行打包（`.\scripts\package-release.ps1`）或发布（`.\scripts\publish-pgyer.ps1`）前，必须**全量执行并通过全部单元测试**（`.\gradlew.bat :app:testDebugUnitTest`），确保测试全部通过（100% SUCCESS）；严禁在测试未执行或有任何测试失败的情况下进行打包或发布。
- **功能与测试同步约束**：
  - 新增功能必须同步添加对应的单元测试；
  - 修改既有功能或业务逻辑必须同步更新已有单元测试，确保测试覆盖度并防止逻辑回归。
- 每次蒲公英发布必须附带简洁、面向用户的更新日志。
- 更新日志必须使用带编号的条目，并且每条单独换行显示，例如：
  `1. 适配增程车型，显示燃油和纯电续航`
  `2. 优化空调卡片，显示制冷/制热及内外循环状态`
- 发布前确认 Release APK、版本号和发布内容一致；发布后核对蒲公英页面和上传结果。
