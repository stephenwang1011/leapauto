#!/usr/bin/env python3
"""Generate the checked-in Xcode project using only Python's standard library.
No package manager, XcodeGen, CocoaPods, or generated credentials required.
"""
from pathlib import Path
import hashlib
import json
import plistlib

ROOT = Path(__file__).resolve().parents[1]
OBJECTS = {}
def ident(name): return hashlib.sha256(name.encode()).hexdigest()[:24].upper()
def add(name, value):
    key = ident(name)
    if key in OBJECTS: raise ValueError(name)
    OBJECTS[key] = value
    return key

def q(value): return json.dumps(value, ensure_ascii=False)
def ref(value): return value

def serialize(value, depth=0):
    if isinstance(value, dict):
        return '{\n' + ''.join('\t'*(depth+1)+str(k)+' = '+serialize(v, depth+1)+';\n' for k,v in value.items()) + '\t'*depth+'}'
    if isinstance(value, list): return '(' + ', '.join(serialize(x,depth) for x in value) + (',' if value else '') + ')'
    return q(str(value))

def configs(name, base):
    ids=[]
    for mode in ['Debug', 'Release']:
        settings = dict(base)
        settings['SWIFT_OPTIMIZATION_LEVEL'] = '-Onone' if mode=='Debug' else '-O'
        settings['DEBUG_INFORMATION_FORMAT'] = 'dwarf' if mode=='Debug' else 'dwarf-with-dsym'
        if mode=='Debug': settings['ENABLE_TESTABILITY']='YES'; settings['SWIFT_ACTIVE_COMPILATION_CONDITIONS']='DEBUG $(inherited)'
        ids.append(add(name+mode, {'isa':'XCBuildConfiguration', 'buildSettings':settings, 'name':mode}))
    return add(name+'Configs',{'isa':'XCConfigurationList','buildConfigurations':ids,'defaultConfigurationIsVisible':'0','defaultConfigurationName':'Release'})

project = ident('project')
app_target=ident('appTarget'); test_target=ident('testTarget'); ui_target=ident('uiTarget'); widget_target=ident('widgetTarget')
app_product = add('appProduct', {'isa':'PBXFileReference','explicitFileType':'wrapper.application','includeInIndex':'0','path':'LeapAuto.app','sourceTree':'BUILT_PRODUCTS_DIR'})
test_product = add('testProduct', {'isa':'PBXFileReference','explicitFileType':'wrapper.cfbundle','includeInIndex':'0','path':'LeapAutoTests.xctest','sourceTree':'BUILT_PRODUCTS_DIR'})
ui_product = add('uiProduct', {'isa':'PBXFileReference','explicitFileType':'wrapper.cfbundle','includeInIndex':'0','path':'LeapAutoUITests.xctest','sourceTree':'BUILT_PRODUCTS_DIR'})
widget_product = add('widgetProduct', {'isa':'PBXFileReference','explicitFileType':'wrapper.app-extension','includeInIndex':'0','path':'LeapAutoWidget.appex','sourceTree':'BUILT_PRODUCTS_DIR'})
package = add('localPackage', {'isa':'XCLocalSwiftPackageReference','relativePath':'Packages/LeapCore'})
core = add('coreProduct', {'isa':'XCSwiftPackageProductDependency','package':package,'productName':'LeapCore'})
core_build=add('coreBuild', {'isa':'PBXBuildFile','productRef':core})
core_test_build=add('coreTestBuild', {'isa':'PBXBuildFile','productRef':core})
core_widget_build=add('coreWidgetBuild', {'isa':'PBXBuildFile','productRef':core})
file_refs=[]; app_sources=[]; test_sources=[]; ui_sources=[]; widget_sources=[]; resources=[]; widget_resources=[]
for folder, dest in [('LeapAuto',app_sources),('LeapAutoTests',test_sources),('LeapAutoUITests',ui_sources),('LeapAutoWidget',widget_sources)]:
    for path in sorted((ROOT/folder).rglob('*.swift')):
        rel=str(path.relative_to(ROOT))
        fr=add('file:'+rel, {'isa':'PBXFileReference','lastKnownFileType':'sourcecode.swift','path':rel,'sourceTree':'<group>'})
        file_refs.append(fr)
        dest.append(add('build:'+rel, {'isa':'PBXBuildFile','fileRef':fr}))
for path in sorted((ROOT/'Shared').rglob('*.swift')):
    rel=str(path.relative_to(ROOT))
    fr=add('file:'+rel, {'isa':'PBXFileReference','lastKnownFileType':'sourcecode.swift','path':rel,'sourceTree':'<group>'})
    file_refs.append(fr)
    app_sources.append(add('appBuild:'+rel, {'isa':'PBXBuildFile','fileRef':fr}))
    if path.name != 'WidgetSnapshotPublisher.swift':
        widget_sources.append(add('widgetBuild:'+rel, {'isa':'PBXBuildFile','fileRef':fr}))
for rel,kind,is_resource in [('LeapAuto/Info.plist','text.plist.xml',False),('LeapAuto/LeapAuto.entitlements','text.plist.entitlements',False),('LeapAuto/PrivacyInfo.xcprivacy','text.xml',True),('LeapAuto/Assets.xcassets','folder.assetcatalog',True),('LeapAutoWidget/Info.plist','text.plist.xml',False),('LeapAutoWidget/LeapAutoWidget.entitlements','text.plist.entitlements',False),('LeapAutoWidget/PrivacyInfo.xcprivacy','text.xml',True)]:
    fr=add('file:'+rel, {'isa':'PBXFileReference','lastKnownFileType':kind,'path':rel,'sourceTree':'<group>'})
    file_refs.append(fr)
    if is_resource: (widget_resources if rel.startswith('LeapAutoWidget/') else resources).append(add('build:'+rel,{'isa':'PBXBuildFile','fileRef':fr}))
products = add('products',{'isa':'PBXGroup','children':[app_product,test_product,ui_product,widget_product],'name':'Products','sourceTree':'<group>'})
main = add('mainGroup',{'isa':'PBXGroup','children':file_refs+[products],'sourceTree':'<group>'})
def phase(name,isa,files): return add(name,{'isa':isa,'buildActionMask':'2147483647','files':files,'runOnlyForDeploymentPostprocessing':'0'})
base={'IPHONEOS_DEPLOYMENT_TARGET':'17.0','SWIFT_VERSION':'5.0','SDKROOT':'iphoneos','CLANG_ENABLE_MODULES':'YES','CLANG_ENABLE_OBJC_ARC':'YES','SWIFT_STRICT_CONCURRENCY':'complete','CODE_SIGN_STYLE':'Automatic','TARGETED_DEVICE_FAMILY':'1,2','SUPPORTED_PLATFORMS':'iphoneos iphonesimulator','ENABLE_USER_SCRIPT_SANDBOXING':'YES'}
app_settings={**base,'PRODUCT_BUNDLE_IDENTIFIER':'com.leapauto.ios','PRODUCT_NAME':'$(TARGET_NAME)','INFOPLIST_FILE':'LeapAuto/Info.plist','GENERATE_INFOPLIST_FILE':'NO','CODE_SIGN_ENTITLEMENTS':'LeapAuto/LeapAuto.entitlements','MARKETING_VERSION':'0.2.0','CURRENT_PROJECT_VERSION':'2','ASSETCATALOG_COMPILER_GLOBAL_ACCENT_COLOR_NAME':'AccentColor','LD_RUNPATH_SEARCH_PATHS':['$(inherited)','@executable_path/Frameworks'],'SUPPORTS_MACCATALYST':'NO','ENABLE_PREVIEWS':'YES'}
widget_proxy=add('widgetProxy',{'isa':'PBXContainerItemProxy','containerPortal':project,'proxyType':'1','remoteGlobalIDString':widget_target,'remoteInfo':'LeapAutoWidget'})
widget_dep=add('widgetDependency',{'isa':'PBXTargetDependency','target':widget_target,'targetProxy':widget_proxy})
widget_embed=add('widgetEmbed',{'isa':'PBXBuildFile','fileRef':widget_product,'settings':{'ATTRIBUTES':['RemoveHeadersOnCopy']}})
embed_phase=add('embedExtensions',{'isa':'PBXCopyFilesBuildPhase','buildActionMask':'2147483647','dstPath':'','dstSubfolderSpec':'13','files':[widget_embed],'name':'Embed App Extensions','runOnlyForDeploymentPostprocessing':'0'})
add('appTarget',{'isa':'PBXNativeTarget','buildConfigurationList':configs('app',app_settings),'buildPhases':[phase('appSources','PBXSourcesBuildPhase',app_sources),phase('appFrameworks','PBXFrameworksBuildPhase',[core_build]),phase('appResources','PBXResourcesBuildPhase',resources),embed_phase],'buildRules':[],'dependencies':[widget_dep],'name':'LeapAuto','packageProductDependencies':[core],'productName':'LeapAuto','productReference':app_product,'productType':'com.apple.product-type.application'})
widget_settings={**base,'PRODUCT_BUNDLE_IDENTIFIER':'com.leapauto.ios.widget','PRODUCT_NAME':'$(TARGET_NAME)','INFOPLIST_FILE':'LeapAutoWidget/Info.plist','GENERATE_INFOPLIST_FILE':'NO','CODE_SIGN_ENTITLEMENTS':'LeapAutoWidget/LeapAutoWidget.entitlements','MARKETING_VERSION':'0.2.0','CURRENT_PROJECT_VERSION':'2','SKIP_INSTALL':'YES','APPLICATION_EXTENSION_API_ONLY':'YES','LD_RUNPATH_SEARCH_PATHS':['$(inherited)','@executable_path/Frameworks','@executable_path/../../Frameworks'],'SUPPORTS_MACCATALYST':'NO'}
add('widgetTarget',{'isa':'PBXNativeTarget','buildConfigurationList':configs('widget',widget_settings),'buildPhases':[phase('widgetSources','PBXSourcesBuildPhase',widget_sources),phase('widgetFrameworks','PBXFrameworksBuildPhase',[core_widget_build]),phase('widgetResources','PBXResourcesBuildPhase',widget_resources)],'buildRules':[],'dependencies':[],'name':'LeapAutoWidget','packageProductDependencies':[core],'productName':'LeapAutoWidget','productReference':widget_product,'productType':'com.apple.product-type.app-extension'})
for key,name,sources,product,ptype in [('test','LeapAutoTests',test_sources,test_product,'com.apple.product-type.bundle.unit-test'),('ui','LeapAutoUITests',ui_sources,ui_product,'com.apple.product-type.bundle.ui-testing')]:
    proxy=add(key+'Proxy',{'isa':'PBXContainerItemProxy','containerPortal':project,'proxyType':'1','remoteGlobalIDString':app_target,'remoteInfo':'LeapAuto'})
    dep=add(key+'Dependency',{'isa':'PBXTargetDependency','target':app_target,'targetProxy':proxy})
    settings={**base,'PRODUCT_BUNDLE_IDENTIFIER':'com.leapauto.ios.'+key+'tests','PRODUCT_NAME':'$(TARGET_NAME)','GENERATE_INFOPLIST_FILE':'YES'}
    if key=='test': settings.update({'TEST_HOST':'$(BUILT_PRODUCTS_DIR)/LeapAuto.app/LeapAuto','BUNDLE_LOADER':'$(TEST_HOST)'})
    else: settings['TEST_TARGET_NAME']='LeapAuto'
    targetname=key+'Target'
    add(targetname,{'isa':'PBXNativeTarget','buildConfigurationList':configs(key,settings),'buildPhases':[phase(key+'Sources','PBXSourcesBuildPhase',sources),phase(key+'Frameworks','PBXFrameworksBuildPhase',[core_test_build] if key=='test' else []),phase(key+'Resources','PBXResourcesBuildPhase',[])],'buildRules':[],'dependencies':[dep],'name':name,'packageProductDependencies':[core] if key=='test' else [],'productName':name,'productReference':product,'productType':ptype})
add('project',{'isa':'PBXProject','attributes':{'BuildIndependentTargetsInParallel':'YES','LastUpgradeCheck':'1600','TargetAttributes':{app_target:{'CreatedOnToolsVersion':'16.0','SystemCapabilities':{'com.apple.ApplicationGroups.iOS':{'enabled':'1'}}},widget_target:{'CreatedOnToolsVersion':'16.0','SystemCapabilities':{'com.apple.ApplicationGroups.iOS':{'enabled':'1'}}},test_target:{'CreatedOnToolsVersion':'16.0','TestTargetID':app_target},ui_target:{'CreatedOnToolsVersion':'16.0','TestTargetID':app_target}}},'buildConfigurationList':configs('project',{'ALWAYS_SEARCH_USER_PATHS':'NO','CLANG_ANALYZER_NONNULL':'YES','GCC_C_LANGUAGE_STANDARD':'gnu17','SWIFT_VERSION':'5.0'}),'compatibilityVersion':'Xcode 14.0','developmentRegion':'zh-Hans','hasScannedForEncodings':'0','knownRegions':['zh-Hans','en','Base'],'mainGroup':main,'packageReferences':[package],'productRefGroup':products,'projectDirPath':'','projectRoot':'','targets':[app_target,test_target,ui_target,widget_target]})
project_dir=ROOT/'LeapAuto.xcodeproj'
project_dir.mkdir(exist_ok=True)
payload={'archiveVersion':'1','classes':{},'objectVersion':'56','objects':OBJECTS,'rootObject':project}
(project_dir/'project.pbxproj').write_text('// !$*UTF8*$!\n'+serialize(payload)+'\n')
scheme_dir=project_dir/'xcshareddata/xcschemes'; scheme_dir.mkdir(parents=True,exist_ok=True)
def build_ref(target,name,product): return f'<BuildableReference BuildableIdentifier="primary" BlueprintIdentifier="{target}" BuildableName="{product}" BlueprintName="{name}" ReferencedContainer="container:LeapAuto.xcodeproj"/>'
app_ref=build_ref(app_target,'LeapAuto','LeapAuto.app')
scheme=f'''<?xml version="1.0" encoding="UTF-8"?>
<Scheme LastUpgradeVersion="1600" version="1.3">
<BuildAction parallelizeBuildables="YES" buildImplicitDependencies="YES"><BuildActionEntries><BuildActionEntry buildForTesting="YES" buildForRunning="YES" buildForProfiling="YES" buildForArchiving="YES" buildForAnalyzing="YES">{app_ref}</BuildActionEntry></BuildActionEntries></BuildAction>
<TestAction buildConfiguration="Debug" selectedDebuggerIdentifier="Xcode.DebuggerFoundation.Debugger.LLDB" selectedLauncherIdentifier="Xcode.IDEFoundation.Launcher.LLDB" shouldUseLaunchSchemeArgsEnv="NO"><Testables><TestableReference skipped="NO">{build_ref(test_target,'LeapAutoTests','LeapAutoTests.xctest')}</TestableReference><TestableReference skipped="NO">{build_ref(ui_target,'LeapAutoUITests','LeapAutoUITests.xctest')}</TestableReference></Testables><EnvironmentVariables><EnvironmentVariable key="LEAPAUTO_OFFLINE_TESTS" value="1" isEnabled="YES"/></EnvironmentVariables></TestAction>
<LaunchAction buildConfiguration="Debug" selectedDebuggerIdentifier="Xcode.DebuggerFoundation.Debugger.LLDB" selectedLauncherIdentifier="Xcode.IDEFoundation.Launcher.LLDB" launchStyle="0" useCustomWorkingDirectory="NO" ignoresPersistentStateOnLaunch="NO" debugDocumentVersioning="YES" debugServiceExtension="internal" allowLocationSimulation="NO"><BuildableProductRunnable runnableDebuggingMode="0">{app_ref}</BuildableProductRunnable></LaunchAction>
<ProfileAction buildConfiguration="Release" shouldUseLaunchSchemeArgsEnv="YES" savedToolIdentifier="" useCustomWorkingDirectory="NO" debugDocumentVersioning="YES"><BuildableProductRunnable runnableDebuggingMode="0">{app_ref}</BuildableProductRunnable></ProfileAction>
<AnalyzeAction buildConfiguration="Debug"/>
<ArchiveAction buildConfiguration="Release" revealArchiveInOrganizer="YES"/>
</Scheme>
'''
(scheme_dir/'LeapAuto.xcscheme').write_text(scheme)
workspace=project_dir/'project.xcworkspace'; workspace.mkdir(exist_ok=True)
(workspace/'contents.xcworkspacedata').write_text('<?xml version="1.0" encoding="UTF-8"?><Workspace version="1.0"><FileRef location="self:"/></Workspace>\n')
print(f'Generated {project_dir.name}: {len(app_sources)} app sources, {len(widget_sources)} widget sources, {len(test_sources)} unit test files, {len(ui_sources)} UI test files.')
