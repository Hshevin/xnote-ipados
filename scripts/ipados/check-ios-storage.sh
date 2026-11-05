#!/bin/sh
set -eu
cd "$(dirname "$0")/../.."
: "${1:?Pass the UUID of a booted iPad simulator}"
simulator_sdk="$(xcrun --sdk iphonesimulator --show-sdk-path)"
xcrun --sdk iphonesimulator swiftc -swift-version 6 -target arm64-apple-ios15.6-simulator \
  -sdk "$simulator_sdk" -module-cache-path tmp/swift-ios-module-cache \
  ios/Sources/LocalStorage/PDFObjectStore.swift probes/ios/StorageMain.swift -o tmp/ios-storage-probe
xcrun simctl spawn "$1" "$PWD/tmp/ios-storage-probe"
