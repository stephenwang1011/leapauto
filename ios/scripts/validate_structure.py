#!/usr/bin/env python3
"""Portable structure checks. These do not claim an Xcode or SwiftUI build."""
from pathlib import Path
import json, plistlib, re, subprocess, sys
import xml.etree.ElementTree as ET

root=Path(__file__).resolve().parents[1]
project=root/'LeapAuto.xcodeproj/project.pbxproj'
text=project.read_text()
errors=[]
for path in sorted((root/'LeapAuto').rglob('*.swift')):
    rel=str(path.relative_to(root))
    if f'path = "{rel}";' not in text: errors.append(f'Not included in Xcode project: {rel}')
for folder in ['LeapAutoTests','LeapAutoUITests']:
    for path in (root/folder).glob('*.swift'):
        if f'path = "{path.relative_to(root)}";' not in text: errors.append(f'Missing test source: {path.name}')
for path in root.rglob('*.plist'):
    if '.build' not in path.parts: plistlib.loads(path.read_bytes())
privacy=plistlib.loads((root/'LeapAuto/PrivacyInfo.xcprivacy').read_bytes())
assert privacy['NSPrivacyTracking'] is False
for path in (root/'LeapAuto/Assets.xcassets').rglob('*.json'): json.loads(path.read_text())
scheme=root/'LeapAuto.xcodeproj/xcshareddata/xcschemes/LeapAuto.xcscheme'
ET.parse(scheme)
ET.parse(root/'LeapAuto.xcodeproj/project.xcworkspace/contents.xcworkspacedata')
info=plistlib.loads((root/'LeapAuto/Info.plist').read_bytes())
assert 'NSAppTransportSecurity' not in info, 'Do not add broad transport exceptions'
assert 'UIBackgroundModes' not in info, 'No unimplemented background modes'
assert 'NSBluetoothAlwaysUsageDescription' not in info, 'No unimplemented BLE permission request'
assert 'NSFaceIDUsageDescription' in info
assert (root/'Packages/LeapCore/Package.swift').exists()
for file in (root/'LeapAuto').rglob('*.swift'):
    data=file.read_text()
    assert 'print(' not in data and 'debugPrint(' not in data, f'No sensitive logging: {file}'
    assert 'UserDefaults' not in data or file.name == 'SessionLifecycleStore.swift', f'Preferences are restricted to the non-sensitive logout marker: {file}'
if errors: print('\n'.join(errors),file=sys.stderr); sys.exit(1)
print('PASS: project source membership, XML/plists/assets, local package, and permission/configuration boundaries.')
print('NOT TESTED by this check: iOS type checking/linking, SwiftUI rendering, signing, Keychain, Face ID or live vehicle behavior.')
