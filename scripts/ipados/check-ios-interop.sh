#!/bin/sh
set -eu
cd "$(dirname "$0")/../.."
: "${1:?Pass the UUID of a booted iPad simulator}"
framework_dir="$PWD/tmp/kmp-probe-build/bin/iosSimulatorArm64/debugFramework"
simulator_sdk="$(xcrun --sdk iphonesimulator --show-sdk-path)"
xcrun --sdk iphonesimulator swiftc -target arm64-apple-ios15.6-simulator -sdk "$simulator_sdk" \
  -F "$framework_dir" -framework XNotesCoreProbe -Xlinker -rpath -Xlinker "$framework_dir" \
  -module-cache-path tmp/swift-ios-module-cache \
  probes/kmp/swift/GeometryInterop.swift probes/kmp/swift/InteropMain.swift -o tmp/kmp-ios-interop
xcrun simctl spawn "$1" "$PWD/tmp/kmp-ios-interop"
