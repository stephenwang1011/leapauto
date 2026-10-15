#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
python3 scripts/validate_structure.py
find LeapAuto LeapAutoTests LeapAutoUITests -name "*.swift" -print0 | xargs -0 swiftc -frontend -parse
if command -v plutil >/dev/null 2>&1; then
  plutil -lint -- LeapAuto.xcodeproj/project.pbxproj LeapAuto/Info.plist LeapAuto/PrivacyInfo.xcprivacy
fi
swift test --package-path Packages/LeapCore
CHECK_DIR="$(mktemp -d)"
trap 'rm -rf "$CHECK_DIR"' EXIT
swiftc LeapAuto/Security/SessionLifecycleStore.swift scripts/check_logout_marker.swift -o "$CHECK_DIR/check_logout_marker"
"$CHECK_DIR/check_logout_marker"
if command -v xcodebuild >/dev/null 2>&1; then
  xcodebuild -project LeapAuto.xcodeproj -scheme LeapAuto -sdk iphonesimulator \
    -destination 'generic/platform=iOS Simulator' -derivedDataPath DerivedData \
    CODE_SIGNING_ALLOWED=NO build
else
  printf '\nXcode unavailable: iOS app build, Apple crypto tests, Keychain/Face ID and simulator tests NOT RUN.\n'
fi
