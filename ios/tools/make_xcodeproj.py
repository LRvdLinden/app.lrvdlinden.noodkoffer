#!/usr/bin/env python3
"""Generates Noodpakket.xcodeproj (app + widget extension) from the folders in ios/.

Run from anywhere: python3 ios/tools/make_xcodeproj.py
Re-run after adding or removing files. IDs are deterministic, so the project only changes when files change.
"""
import hashlib
import os
import plistlib

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
NAME = "Noodpakket"
BUNDLE_ID = "app.lrvdlinden.noodpakket"
TEAM = "EU3UY476T6"
VERSION, BUILD = "1.0.0", "5"


def oid(*parts):
    return hashlib.md5("|".join(parts).encode()).hexdigest()[:24].upper()


FILE_TYPES = {
    ".swift": "sourcecode.swift", ".xcassets": "folder.assetcatalog", ".xcstrings": "text.json.xcstrings",
    ".xcprivacy": "text.xml", ".plist": "text.plist.xml", ".entitlements": "text.plist.entitlements",
}

# ---------------------------------------------------------------- plists and entitlements

def write_plist(rel, data):
    with open(os.path.join(ROOT, rel), "wb") as f:
        plistlib.dump(data, f, sort_keys=True)

common_info = {
    "CFBundleDevelopmentRegion": "$(DEVELOPMENT_LANGUAGE)",
    "CFBundleDisplayName": NAME,
    "CFBundleExecutable": "$(EXECUTABLE_NAME)",
    "CFBundleIdentifier": "$(PRODUCT_BUNDLE_IDENTIFIER)",
    "CFBundleInfoDictionaryVersion": "6.0",
    "CFBundleName": "$(PRODUCT_NAME)",
    "CFBundlePackageType": "$(PRODUCT_BUNDLE_PACKAGE_TYPE)",
    "CFBundleShortVersionString": "$(MARKETING_VERSION)",
    "CFBundleVersion": "$(CURRENT_PROJECT_VERSION)",
    "ITSAppUsesNonExemptEncryption": False,
}
write_plist(f"{NAME}/Info.plist", {
    **common_info,
    "LSRequiresIPhoneOS": True,
    "LSApplicationCategoryType": "public.app-category.lifestyle",
    "UIApplicationSceneManifest": {"UIApplicationSupportsMultipleScenes": True},
    "UILaunchScreen": {"UIColorName": "LaunchBackground", "UIImageName": "LaunchLogo"},
    "UISupportedInterfaceOrientations": ["UIInterfaceOrientationPortrait"],
    "UISupportedInterfaceOrientations~ipad": ["UIInterfaceOrientationPortrait", "UIInterfaceOrientationPortraitUpsideDown",
                                              "UIInterfaceOrientationLandscapeLeft", "UIInterfaceOrientationLandscapeRight"],
    "UIBackgroundModes": ["remote-notification"],
    "CFBundleURLTypes": [{"CFBundleURLName": BUNDLE_ID, "CFBundleURLSchemes": ["noodpakket"]}],
    "NSCameraUsageDescription": "Noodpakket uses the camera to scan barcodes and expiry dates on packaging.",
    "NSFaceIDUsageDescription": "Noodpakket uses Face ID to unlock the app when you have turned on the app lock.",
})
write_plist(f"{NAME}Widget/Info.plist", {
    **common_info,
    "NSExtension": {"NSExtensionPointIdentifier": "com.apple.widgetkit-extension"},
})
write_plist(f"{NAME}/{NAME}.entitlements", {
    "aps-environment": "development",
    "com.apple.developer.icloud-container-identifiers": [f"iCloud.{BUNDLE_ID}"],
    "com.apple.developer.icloud-services": ["CloudKit"],
    "com.apple.security.application-groups": [f"group.{BUNDLE_ID}"],
})
write_plist(f"{NAME}Widget/{NAME}Widget.entitlements", {
    "com.apple.security.application-groups": [f"group.{BUNDLE_ID}"],
})

# ---------------------------------------------------------------- collect files

def collect(folder):
    """Returns (relative paths) of files/bundles to reference, depth-first; .xcassets counted as one item."""
    out = []
    base = os.path.join(ROOT, folder)
    for dirpath, dirnames, filenames in os.walk(base):
        dirnames.sort()
        keep = []
        for d in dirnames:
            if d.endswith(".xcassets"):
                out.append(os.path.relpath(os.path.join(dirpath, d), ROOT))
            else:
                keep.append(d)
        dirnames[:] = keep
        for f in sorted(filenames):
            if os.path.splitext(f)[1] in FILE_TYPES and not f.startswith("."):
                out.append(os.path.relpath(os.path.join(dirpath, f), ROOT))
    return sorted(out)

folders = [NAME, f"{NAME}Widget", "Shared"]
all_files = [p for folder in folders for p in collect(folder)]

objects = {}

def add(id_, obj):
    objects[id_] = obj
    return id_

# file references and groups (mirror the folder tree)
file_ref = {}
groups = {}

def group_for(rel_dir):
    if rel_dir in groups:
        return groups[rel_dir]
    gid = oid("group", rel_dir)
    groups[rel_dir] = gid
    add(gid, {"isa": "PBXGroup", "children": [], "path": os.path.basename(rel_dir), "sourceTree": "<group>"})
    parent = os.path.dirname(rel_dir)
    if parent:
        objects[group_for(parent)]["children"].append(gid)
    else:
        objects[MAIN_GROUP]["children"].append(gid)
    return gid

MAIN_GROUP = oid("main-group")
PRODUCTS_GROUP = oid("products-group")
add(MAIN_GROUP, {"isa": "PBXGroup", "children": [], "sourceTree": "<group>"})

for rel in all_files:
    ext = os.path.splitext(rel)[1]
    fid = oid("file", rel)
    file_ref[rel] = fid
    add(fid, {"isa": "PBXFileReference", "lastKnownFileType": FILE_TYPES[ext], "path": os.path.basename(rel), "sourceTree": "<group>"})
    objects[group_for(os.path.dirname(rel))]["children"].append(fid)

add(PRODUCTS_GROUP, {"isa": "PBXGroup", "children": [], "name": "Products", "sourceTree": "<group>"})
objects[MAIN_GROUP]["children"].append(PRODUCTS_GROUP)

APP_PRODUCT = add(oid("product-app"), {"isa": "PBXFileReference", "explicitFileType": "wrapper.application",
                                      "includeInIndex": 0, "path": f"{NAME}.app", "sourceTree": "BUILT_PRODUCTS_DIR"})
EXT_PRODUCT = add(oid("product-ext"), {"isa": "PBXFileReference", "explicitFileType": "wrapper.app-extension",
                                      "includeInIndex": 0, "path": f"{NAME}Widget.appex", "sourceTree": "BUILT_PRODUCTS_DIR"})
objects[PRODUCTS_GROUP]["children"] += [APP_PRODUCT, EXT_PRODUCT]

# ---------------------------------------------------------------- targets

def build_phase(kind, target, files):
    pid = oid("phase", kind, target)
    bfs = []
    for rel in files:
        bid = add(oid("buildfile", kind, target, rel), {"isa": "PBXBuildFile", "fileRef": file_ref[rel]})
        bfs.append(bid)
    add(pid, {"isa": kind, "buildActionMask": 2147483647, "files": bfs, "runOnlyForDeploymentPostprocessing": 0})
    return pid

def is_source(rel): return rel.endswith(".swift")
def is_resource(rel): return os.path.splitext(rel)[1] in (".xcassets", ".xcstrings", ".xcprivacy")

app_files = [p for p in all_files if p.startswith(NAME + "/") or p.startswith("Shared/")]
ext_files = [p for p in all_files if p.startswith(f"{NAME}Widget/") or p.startswith("Shared/")]

base_target_settings = {
    "CODE_SIGN_STYLE": "Automatic",
    "DEVELOPMENT_TEAM": TEAM,
    "CURRENT_PROJECT_VERSION": BUILD,
    "MARKETING_VERSION": VERSION,
    "IPHONEOS_DEPLOYMENT_TARGET": "17.0",
    "TARGETED_DEVICE_FAMILY": "1,2",
    "SWIFT_VERSION": "5.0",
    "SWIFT_EMIT_LOC_STRINGS": "YES",
    "LOCALIZATION_PREFERS_STRING_CATALOGS": "YES",
    "GENERATE_INFOPLIST_FILE": "YES",
    "INFOPLIST_KEY_CFBundleDisplayName": NAME,
    "INFOPLIST_KEY_ITSAppUsesNonExemptEncryption": "NO",
    "PRODUCT_NAME": "$(TARGET_NAME)",
    "ENABLE_PREVIEWS": "YES",
}

def configs(owner, settings_by_name):
    ids = []
    for name in ("Debug", "Release"):
        cid = add(oid("config", owner, name), {"isa": "XCBuildConfiguration", "buildSettings": settings_by_name(name), "name": name})
        ids.append(cid)
    lid = add(oid("configlist", owner), {"isa": "XCConfigurationList", "buildConfigurations": ids,
                                         "defaultConfigurationIsVisible": 0, "defaultConfigurationName": "Release"})
    return lid

app_settings = lambda name: {
    **base_target_settings,
    "PRODUCT_BUNDLE_IDENTIFIER": BUNDLE_ID,
    "INFOPLIST_FILE": f"{NAME}/Info.plist",
    "CODE_SIGN_ENTITLEMENTS": f"{NAME}/{NAME}.entitlements",
    "ASSETCATALOG_COMPILER_APPICON_NAME": "AppIcon",
    "ASSETCATALOG_COMPILER_GLOBAL_ACCENT_COLOR_NAME": "AccentColor",
    "INFOPLIST_KEY_LSApplicationCategoryType": "public.app-category.lifestyle",
    "INFOPLIST_KEY_UIApplicationSceneManifest_Generation": "YES",
    "LD_RUNPATH_SEARCH_PATHS": ["$(inherited)", "@executable_path/Frameworks"],
}
ext_settings = lambda name: {
    **base_target_settings,
    "PRODUCT_BUNDLE_IDENTIFIER": f"{BUNDLE_ID}.widget",
    "INFOPLIST_FILE": f"{NAME}Widget/Info.plist",
    "CODE_SIGN_ENTITLEMENTS": f"{NAME}Widget/{NAME}Widget.entitlements",
    "SKIP_INSTALL": "YES",
    "LD_RUNPATH_SEARCH_PATHS": ["$(inherited)", "@executable_path/Frameworks", "@executable_path/../../Frameworks"],
}

EXT_TARGET = oid("target-ext")
APP_TARGET = oid("target-app")
PROJECT = oid("project")

ext_phases = [build_phase("PBXSourcesBuildPhase", "ext", [p for p in ext_files if is_source(p)]),
              build_phase("PBXResourcesBuildPhase", "ext", [p for p in ext_files if is_resource(p)]),
              build_phase("PBXFrameworksBuildPhase", "ext", [])]
add(EXT_TARGET, {"isa": "PBXNativeTarget", "buildConfigurationList": configs("ext", ext_settings), "buildPhases": ext_phases,
                 "buildRules": [], "dependencies": [], "name": f"{NAME}Widget", "productName": f"{NAME}Widget",
                 "productReference": EXT_PRODUCT, "productType": "com.apple.product-type.app-extension"})

proxy = add(oid("proxy-ext"), {"isa": "PBXContainerItemProxy", "containerPortal": PROJECT, "proxyType": 1,
                               "remoteGlobalIDString": EXT_TARGET, "remoteInfo": f"{NAME}Widget"})
dep = add(oid("dep-ext"), {"isa": "PBXTargetDependency", "target": EXT_TARGET, "targetProxy": proxy})
embed_file = add(oid("embed-ext"), {"isa": "PBXBuildFile", "fileRef": EXT_PRODUCT, "settings": {"ATTRIBUTES": ["RemoveHeadersOnCopy"]}})
embed_phase = add(oid("embed-phase"), {"isa": "PBXCopyFilesBuildPhase", "buildActionMask": 2147483647, "dstPath": "",
                                       "dstSubfolderSpec": 13, "files": [embed_file], "name": "Embed Foundation Extensions",
                                       "runOnlyForDeploymentPostprocessing": 0})
app_phases = [build_phase("PBXSourcesBuildPhase", "app", [p for p in app_files if is_source(p)]),
              build_phase("PBXResourcesBuildPhase", "app", [p for p in app_files if is_resource(p)]),
              build_phase("PBXFrameworksBuildPhase", "app", []), embed_phase]
add(APP_TARGET, {"isa": "PBXNativeTarget", "buildConfigurationList": configs("app", app_settings), "buildPhases": app_phases,
                 "buildRules": [], "dependencies": [dep], "name": NAME, "productName": NAME,
                 "productReference": APP_PRODUCT, "productType": "com.apple.product-type.application"})

project_settings = lambda name: {
    "ALWAYS_SEARCH_USER_PATHS": "NO",
    "ASSETCATALOG_COMPILER_GENERATE_SWIFT_ASSET_SYMBOL_EXTENSIONS": "YES",
    "CLANG_ENABLE_MODULES": "YES",
    "CLANG_ENABLE_OBJC_ARC": "YES",
    "COPY_PHASE_STRIP": "NO",
    "DEBUG_INFORMATION_FORMAT": "dwarf" if name == "Debug" else "dwarf-with-dsym",
    "ENABLE_STRICT_OBJC_MSGSEND": "YES",
    "ENABLE_TESTABILITY": "YES" if name == "Debug" else "NO",
    "ENABLE_USER_SCRIPT_SANDBOXING": "YES",
    "GCC_C_LANGUAGE_STANDARD": "gnu17",
    "GCC_NO_COMMON_BLOCKS": "YES",
    "GCC_OPTIMIZATION_LEVEL": "0" if name == "Debug" else "s",
    "IPHONEOS_DEPLOYMENT_TARGET": "17.0",
    "LOCALIZATION_PREFERS_STRING_CATALOGS": "YES",
    "MTL_FAST_MATH": "YES",
    "ONLY_ACTIVE_ARCH": "YES" if name == "Debug" else "NO",
    "SDKROOT": "iphoneos",
    "SWIFT_COMPILATION_MODE": "singlefile" if name == "Debug" else "wholemodule",
    "SWIFT_OPTIMIZATION_LEVEL": "-Onone" if name == "Debug" else "-O",
    **({"SWIFT_ACTIVE_COMPILATION_CONDITIONS": "DEBUG $(inherited)", "GCC_PREPROCESSOR_DEFINITIONS": ["DEBUG=1", "$(inherited)"]}
       if name == "Debug" else {"VALIDATE_PRODUCT": "YES"}),
}
add(PROJECT, {"isa": "PBXProject",
              "attributes": {"BuildIndependentTargetsInParallel": 1, "LastSwiftUpdateCheck": 1600, "LastUpgradeCheck": 1600,
                             "TargetAttributes": {APP_TARGET: {"CreatedOnToolsVersion": "16.0"}, EXT_TARGET: {"CreatedOnToolsVersion": "16.0"}}},
              "buildConfigurationList": configs("project", project_settings),
              "compatibilityVersion": "Xcode 14.0", "developmentRegion": "en", "hasScannedForEncodings": 0,
              "knownRegions": ["en", "nl", "Base"], "mainGroup": MAIN_GROUP, "productRefGroup": PRODUCTS_GROUP,
              "projectDirPath": "", "projectRoot": "", "targets": [APP_TARGET, EXT_TARGET]})

# ---------------------------------------------------------------- serialise (OpenStep plist)

import re as _re

def q(v):
    s = str(v)
    if _re.fullmatch(r"[A-Za-z0-9_./]+", s):
        return s
    return '"' + s.replace("\\", "\\\\").replace('"', '\\"').replace("\n", "\\n") + '"'

def val(v, indent):
    pad = "\t" * indent
    if isinstance(v, dict):
        inner = "".join(f"{pad}\t{q(k)} = {val(v[k], indent + 1)};\n" for k in sorted(v, key=lambda k: (k != "isa", k)))
        return "{\n" + inner + pad + "}"
    if isinstance(v, list):
        return "(\n" + "".join(f"{pad}\t{val(x, indent + 1)},\n" for x in v) + pad + ")"
    return q(v)

lines = ["// !$*UTF8*$!", "{", "\tarchiveVersion = 1;", "\tclasses = {\n\t};", "\tobjectVersion = 56;", "\tobjects = {"]
by_isa = {}
for k, o in objects.items():
    by_isa.setdefault(o["isa"], []).append(k)
for isa in sorted(by_isa):
    lines.append(f"\n/* Begin {isa} section */")
    for k in sorted(by_isa[isa]):
        lines.append(f"\t\t{k} = {val(objects[k], 2)};")
    lines.append(f"/* End {isa} section */")
lines += ["\t};", f"\trootObject = {PROJECT};", "}", ""]

proj_dir = os.path.join(ROOT, f"{NAME}.xcodeproj")
os.makedirs(proj_dir, exist_ok=True)
with open(os.path.join(proj_dir, "project.pbxproj"), "w") as f:
    f.write("\n".join(lines))

# shared scheme, so "Noodpakket" is ready to run straight away
scheme_dir = os.path.join(proj_dir, "xcshareddata", "xcschemes")
os.makedirs(scheme_dir, exist_ok=True)
ref = lambda t, n, p: (f'<BuildableReference BuildableIdentifier = "primary" BlueprintIdentifier = "{t}" '
                       f'BuildableName = "{p}" BlueprintName = "{n}" ReferencedContainer = "container:{NAME}.xcodeproj"></BuildableReference>')
with open(os.path.join(scheme_dir, f"{NAME}.xcscheme"), "w") as f:
    f.write(f'''<?xml version="1.0" encoding="UTF-8"?>
<Scheme LastUpgradeVersion = "1600" version = "1.7">
   <BuildAction parallelizeBuildables = "YES" buildImplicitDependencies = "YES">
      <BuildActionEntries>
         <BuildActionEntry buildForTesting = "YES" buildForRunning = "YES" buildForProfiling = "YES" buildForArchiving = "YES" buildForAnalyzing = "YES">
            {ref(APP_TARGET, NAME, NAME + ".app")}
         </BuildActionEntry>
      </BuildActionEntries>
   </BuildAction>
   <TestAction buildConfiguration = "Debug" selectedDebuggerIdentifier = "Xcode.DebuggerFoundation.Debugger.LLDB" selectedLauncherIdentifier = "Xcode.DebuggerFoundation.Launcher.LLDB" shouldUseLaunchSchemeArgsEnv = "YES"></TestAction>
   <LaunchAction buildConfiguration = "Debug" selectedDebuggerIdentifier = "Xcode.DebuggerFoundation.Debugger.LLDB" selectedLauncherIdentifier = "Xcode.DebuggerFoundation.Launcher.LLDB" launchStyle = "0" useCustomWorkingDirectory = "NO" ignoresPersistentStateOnLaunch = "NO" debugDocumentVersioning = "YES" debugServiceExtension = "internal" allowLocationSimulation = "YES">
      <BuildableProductRunnable runnableDebuggingMode = "0">
         {ref(APP_TARGET, NAME, NAME + ".app")}
      </BuildableProductRunnable>
   </LaunchAction>
   <ProfileAction buildConfiguration = "Release" shouldUseLaunchSchemeArgsEnv = "YES" savedToolIdentifier = "" useCustomWorkingDirectory = "NO" debugDocumentVersioning = "YES">
      <BuildableProductRunnable runnableDebuggingMode = "0">
         {ref(APP_TARGET, NAME, NAME + ".app")}
      </BuildableProductRunnable>
   </ProfileAction>
   <AnalyzeAction buildConfiguration = "Debug"></AnalyzeAction>
   <ArchiveAction buildConfiguration = "Release" revealArchiveInOrganizer = "YES"></ArchiveAction>
</Scheme>
''')
print(f"{NAME}.xcodeproj: {len(all_files)} files, {len(objects)} objects")
