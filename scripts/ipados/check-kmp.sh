#!/bin/sh
set -eu
cd "$(dirname "$0")/../.."
./gradlew -p probes/kmp --project-cache-dir ../../tmp/kmp-probe-cache \
  jvmTest macosArm64Test linkDebugFrameworkMacosArm64 linkDebugFrameworkIosSimulatorArm64 --no-daemon
framework_dir="$PWD/tmp/kmp-probe-build/bin/macosArm64/debugFramework"
xcrun swiftc -F "$framework_dir" -framework XNotesCoreProbe \
  -Xlinker -rpath -Xlinker "$framework_dir" -module-cache-path tmp/swift-ios-module-cache \
  probes/kmp/swift/GeometryInterop.swift probes/kmp/swift/InteropMain.swift -o tmp/kmp-swift-interop
tmp/kmp-swift-interop
simulator_sdk="$(xcrun --sdk iphonesimulator --show-sdk-path)"
xcrun swiftc -typecheck -target arm64-apple-ios15.6-simulator -sdk "$simulator_sdk" \
  -module-cache-path tmp/swift-ios-module-cache \
  -F tmp/kmp-probe-build/bin/iosSimulatorArm64/debugFramework \
  probes/kmp/swift/GeometryInterop.swift probes/kmp/swift/InteropMain.swift
