#!/usr/bin/env python3
"""Portable structure checks. These do not claim an Xcode or SwiftUI build."""
from pathlib import Path
import json, plistlib, re, subprocess, sys
import xml.etree.ElementTree as ET

root=Path(__file__).resolve().parents[1]
project=root/'LeapAuto.xcodeproj/project.pbxproj'
text=project.read_text()
errors=[]
for path in sorted(p for folder in ['LeapAuto','LeapAutoWidget','Shared'] for p in (root/folder).rglob('*.swift')):
    rel=str(path.relative_to(root))
    if f'path = "{rel}";' not in text: errors.append(f'Not included in Xcode project: {rel}')
for folder in ['LeapAutoTests','LeapAutoUITests']:
    for path in (root/folder).glob('*.swift'):
        if f'path = "{path.relative_to(root)}";' not in text: errors.append(f'Missing test source: {path.name}')
for path in root.rglob('*.plist'):
    if '.build' not in path.parts: plistlib.loads(path.read_bytes())
privacy=plistlib.loads((root/'LeapAuto/PrivacyInfo.xcprivacy').read_bytes())
assert privacy['NSPrivacyTracking'] is False
widget_privacy=plistlib.loads((root/'LeapAutoWidget/PrivacyInfo.xcprivacy').read_bytes())
assert widget_privacy['NSPrivacyTracking'] is False
assert not widget_privacy['NSPrivacyCollectedDataTypes']
for path in (root/'LeapAuto/Assets.xcassets').rglob('*.json'): json.loads(path.read_text())
scheme=root/'LeapAuto.xcodeproj/xcshareddata/xcschemes/LeapAuto.xcscheme'
ET.parse(scheme)
ET.parse(root/'LeapAuto.xcodeproj/project.xcworkspace/contents.xcworkspacedata')
info=plistlib.loads((root/'LeapAuto/Info.plist').read_bytes())
assert 'NSAppTransportSecurity' not in info, 'Do not add broad transport exceptions'
assert 'UIBackgroundModes' not in info, 'No unimplemented background modes'
assert 'NSBluetoothAlwaysUsageDescription' not in info, 'No unimplemented BLE permission request'
assert 'NSFaceIDUsageDescription' in info
widget_info=plistlib.loads((root/'LeapAutoWidget/Info.plist').read_bytes())
assert widget_info['NSExtension']['NSExtensionPointIdentifier']=='com.apple.widgetkit-extension'
for folder, name in [('LeapAuto','LeapAuto'),('LeapAutoWidget','LeapAutoWidget')]:
    entitlements=plistlib.loads((root/folder/(name+'.entitlements')).read_bytes())
    assert entitlements=={'com.apple.security.application-groups':['group.com.leapauto.ios']}
assert info['CFBundleURLTypes'][0]['CFBundleURLSchemes']==['leapauto']
assert (root/'Packages/LeapCore/Package.swift').exists()
for file in (root/'LeapAuto').rglob('*.swift'):
    data=file.read_text()
    assert 'print(' not in data and 'debugPrint(' not in data, f'No sensitive logging: {file}'
    assert 'UserDefaults' not in data or file.name == 'SessionLifecycleStore.swift', f'Preferences are restricted to the non-sensitive logout marker: {file}'

# The generated project uses a restricted OpenStep plist subset. Parse it rather
# than merely checking filenames exist somewhere in the project text.
tokens=re.findall(r'//[^\n]*|/\*.*?\*/|"(?:\\.|[^"\\])*"|[{}()=;,]|[^\s{}()=;,]+',text,re.S)
tokens=[t for t in tokens if not t.startswith('//') and not t.startswith('/*')]
position=0
def take(expected=None):
    global position
    value=tokens[position]; position+=1
    assert expected is None or value==expected, (value,expected)
    return value
def parse_value():
    token=take()
    if token=='{':
        value={}
        while tokens[position]!='}':
            key=take(); key=json.loads(key) if key.startswith('"') else key
            take('='); value[key]=parse_value(); take(';')
        take('}'); return value
    if token=='(':
        value=[]
        while tokens[position]!=')':
            value.append(parse_value())
            if tokens[position]==',': take(',')
        take(')'); return value
    return json.loads(token) if token.startswith('"') else token
pbx=parse_value(); assert position==len(tokens)
objects=pbx['objects']
targets={o['name']:o for o in objects.values() if o.get('isa')=='PBXNativeTarget'}
assert set(targets)=={'LeapAuto','LeapAutoWidget','LeapAutoTests','LeapAutoUITests'}
def sources(target):
    result=set()
    for phase_id in targets[target]['buildPhases']:
        phase=objects[phase_id]
        if phase['isa']!='PBXSourcesBuildPhase': continue
        for build_id in phase['files']:
            result.add(objects[objects[build_id]['fileRef']]['path'])
    return result
shared={str(p.relative_to(root)) for p in (root/'Shared').glob('*.swift')}
expected_app={str(p.relative_to(root)) for p in (root/'LeapAuto').rglob('*.swift')}|shared
expected_widget={str(p.relative_to(root)) for p in (root/'LeapAutoWidget').rglob('*.swift')}|{s for s in shared if not s.endswith('WidgetSnapshotPublisher.swift')}
assert sources('LeapAuto')==expected_app
assert sources('LeapAutoWidget')==expected_widget
for name in ['LeapAutoTests','LeapAutoUITests']:
    assert sources(name)=={str(p.relative_to(root)) for p in (root/name).rglob('*.swift')}
widget_id=next(k for k,v in objects.items() if v is targets['LeapAutoWidget'])
assert any(objects[d]['target']==widget_id for d in targets['LeapAuto']['dependencies'])
assert any(objects[p]['isa']=='PBXCopyFilesBuildPhase' and objects[p]['dstSubfolderSpec']=='13'
           and any(objects[b].get('fileRef')==targets['LeapAutoWidget']['productReference'] for b in objects[p]['files'])
           for p in targets['LeapAuto']['buildPhases'])
for target in ['LeapAuto','LeapAutoWidget']:
    assert any(objects[d]['productName']=='LeapCore' for d in targets[target]['packageProductDependencies'])
    for config in objects[targets[target]['buildConfigurationList']]['buildConfigurations']:
        settings=objects[config]['buildSettings']
        assert settings['CODE_SIGN_ENTITLEMENTS']==f'{target}/{target}.entitlements'
        if target=='LeapAutoWidget': assert settings['APPLICATION_EXTENSION_API_ONLY']=='YES'
if errors: print('\n'.join(errors),file=sys.stderr); sys.exit(1)
print('PASS: target-specific source membership, widget embedding, App Groups, XML/plists/assets, local package and permission/configuration boundaries.')
print('NOT TESTED by this check: iOS type checking/linking, SwiftUI rendering, signing, Keychain, Face ID or live vehicle behavior.')
