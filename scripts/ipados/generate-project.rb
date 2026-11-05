#!/usr/bin/env ruby
# Deterministic Xcode project generation, without third-party dependencies.
require 'fileutils'
root = File.expand_path('../../ios', __dir__)
objects = {}
counter = 0
add = lambda do |body|
  counter += 1
  id = '%024X' % counter
  objects[id] = body
  id
end
sources = Dir.glob(File.join(root, '{App,Canvas}/**/*.swift')).sort.map do |file|
  ref = add.call("isa = PBXFileReference; lastKnownFileType = sourcecode.swift; path = \"#{file.delete_prefix(root + '/')}\"; sourceTree = SOURCE_ROOT;")
  [ref, add.call("isa = PBXBuildFile; fileRef = #{ref};")]
end
locales = %w[en zh-Hans].map do |locale|
  add.call("isa = PBXFileReference; lastKnownFileType = text.plist.strings; name = \"#{locale}\"; path = \"Resources/#{locale}.lproj/Localizable.strings\"; sourceTree = SOURCE_ROOT;")
end
strings = add.call("isa = PBXVariantGroup; children = (#{locales.join(',')}); name = Localizable.strings; sourceTree = SOURCE_ROOT;")
resource = add.call("isa = PBXBuildFile; fileRef = #{strings};")
product = add.call('isa = PBXFileReference; explicitFileType = wrapper.application; path = XNotes.app; sourceTree = BUILT_PRODUCTS_DIR;')
group = add.call("isa = PBXGroup; children = (#{(sources.map(&:first) + [strings, product]).join(',')}); sourceTree = \"<group>\";")
source_phase = add.call("isa = PBXSourcesBuildPhase; buildActionMask = 2147483647; files = (#{sources.map(&:last).join(',')}); runOnlyForDeploymentPostprocessing = 0;")
resource_phase = add.call("isa = PBXResourcesBuildPhase; buildActionMask = 2147483647; files = (#{resource}); runOnlyForDeploymentPostprocessing = 0;")
settings = 'ALWAYS_SEARCH_USER_PATHS = NO; SDKROOT = iphoneos; IPHONEOS_DEPLOYMENT_TARGET = 15.6; TARGETED_DEVICE_FAMILY = 2; SWIFT_VERSION = 5.0; GENERATE_INFOPLIST_FILE = YES; INFOPLIST_KEY_UIApplicationSceneManifest_Generation = YES; INFOPLIST_KEY_UILaunchScreen_Generation = YES; INFOPLIST_KEY_UISupportedInterfaceOrientations_iPad = "UIInterfaceOrientationPortrait UIInterfaceOrientationPortraitUpsideDown UIInterfaceOrientationLandscapeLeft UIInterfaceOrientationLandscapeRight"; PRODUCT_BUNDLE_IDENTIFIER = com.xnotes.ipados.probe; PRODUCT_NAME = XNotes; CURRENT_PROJECT_VERSION = 1; MARKETING_VERSION = 0.1.0;'
configs = %w[Debug Release].map do |name|
  add.call("isa = XCBuildConfiguration; name = #{name}; buildSettings = { #{settings} SWIFT_OPTIMIZATION_LEVEL = \"#{name == 'Debug' ? '-Onone' : '-O'}\"; };")
end
config_list = add.call("isa = XCConfigurationList; buildConfigurations = (#{configs.join(',')}); defaultConfigurationIsVisible = 0; defaultConfigurationName = Debug;")
target = add.call("isa = PBXNativeTarget; name = XNotes; productName = XNotes; productType = \"com.apple.product-type.application\"; productReference = #{product}; buildConfigurationList = #{config_list}; buildPhases = (#{source_phase},#{resource_phase}); buildRules = (); dependencies = ();")
project = add.call("isa = PBXProject; attributes = { LastUpgradeCheck = 2720; }; buildConfigurationList = #{config_list}; compatibilityVersion = \"Xcode 14.0\"; developmentRegion = en; knownRegions = (en, \"zh-Hans\"); mainGroup = #{group}; projectDirPath = \"\"; projectRoot = \"\"; targets = (#{target});")
File.write(File.join(root, 'XNotes.xcodeproj/project.pbxproj'), "// !$*UTF8*$!\n{ archiveVersion = 1; classes = {}; objectVersion = 56; objects = {\n" + objects.map { |id, body| "#{id} = { #{body} };" }.join("\n") + "\n}; rootObject = #{project}; }\n")
File.write(File.join(root, 'XNotes.xcodeproj/xcshareddata/xcschemes/XNotes.xcscheme'), <<~XML)
<?xml version="1.0" encoding="UTF-8"?>
<Scheme LastUpgradeVersion="2720" version="1.3">
<BuildAction parallelizeBuildables="YES" buildImplicitDependencies="YES"><BuildActionEntries><BuildActionEntry buildForTesting="YES" buildForRunning="YES" buildForProfiling="YES" buildForArchiving="YES" buildForAnalyzing="YES"><BuildableReference BuildableIdentifier="primary" BlueprintIdentifier="#{target}" BuildableName="XNotes.app" BlueprintName="XNotes" ReferencedContainer="container:XNotes.xcodeproj"/></BuildActionEntry></BuildActionEntries></BuildAction>
<LaunchAction buildConfiguration="Debug" selectedDebuggerIdentifier="Xcode.DebuggerFoundation.Debugger.LLDB" selectedLauncherIdentifier="Xcode.IDEFoundation.Launcher.LLDB" launchStyle="0" useCustomWorkingDirectory="NO" ignoresPersistentStateOnLaunch="NO" debugDocumentVersioning="YES" allowLocationSimulation="YES"><BuildableProductRunnable runnableDebuggingMode="0"><BuildableReference BuildableIdentifier="primary" BlueprintIdentifier="#{target}" BuildableName="XNotes.app" BlueprintName="XNotes" ReferencedContainer="container:XNotes.xcodeproj"/></BuildableProductRunnable></LaunchAction>
</Scheme>
XML
