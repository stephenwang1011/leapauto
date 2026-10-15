#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
if [[ "$(uname -s)" != Darwin ]] || ! command -v xcodebuild >/dev/null 2>&1; then
  echo '需要安装 Xcode 的 Mac。Linux 不能验证 SwiftUI、Security 或 iOS 模拟器。' >&2
  exit 2
fi
xcodebuild -version
python3 scripts/validate_structure.py
swift test --package-path Packages/LeapCore
xcodebuild -project LeapAuto.xcodeproj -scheme LeapAuto -sdk iphonesimulator \
  -destination 'generic/platform=iOS Simulator' -derivedDataPath DerivedData \
  CODE_SIGNING_ALLOWED=NO build
SIMULATOR_ID="${SIMULATOR_ID:-$(xcrun simctl list devices available -j | python3 -c '
import json, sys
data=json.load(sys.stdin)
phones=[d for runtime, devices in data["devices"].items() if "iOS-" in runtime and int(runtime.split("iOS-")[-1].split("-")[0])>=17 for d in devices if "iPhone" in d["name"] and d.get("isAvailable",False)]
phones.sort(key=lambda d:d.get("state")!="Booted")
print(phones[0]["udid"] if phones else "")
')}"
if [[ -z "$SIMULATOR_ID" ]]; then
  echo '未找到可用 iOS 17+ iPhone 模拟器。请在 Xcode Settings → Platforms 安装 iOS runtime。' >&2
  exit 2
fi
mkdir -p build
xcodebuild -project LeapAuto.xcodeproj -scheme LeapAuto \
  -destination "platform=iOS Simulator,id=$SIMULATOR_ID" -derivedDataPath DerivedData \
  -resultBundlePath "build/OfflineTests-$(date +%Y%m%d-%H%M%S).xcresult" \
  CODE_SIGNING_ALLOWED=NO test
