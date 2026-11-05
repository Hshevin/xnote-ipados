#!/bin/sh
set -eu
cd "$(dirname "$0")/../.."
: "${1:?Pass the UUID of a booted iPad simulator}"
simulator_sdk="$(xcrun --sdk iphonesimulator --show-sdk-path)"
module_dir="$PWD/tmp/ios-pdf-modules"
mkdir -p "$module_dir"
compile_module() {
  module_name="$1"
  source_file="$2"
  shift 2
  xcrun --sdk iphonesimulator swiftc -swift-version 6 -target arm64-apple-ios15.6-simulator \
    -sdk "$simulator_sdk" -module-cache-path tmp/swift-ios-module-cache \
    -emit-module -emit-library -module-name "$module_name" -I "$module_dir" -L "$module_dir" \
    -Xlinker -rpath -Xlinker "$module_dir" "$source_file" "$@" \
    -emit-module-path "$module_dir/$module_name.swiftmodule" -o "$module_dir/lib$module_name.dylib"
}
compile_module LocalStorage ios/Sources/LocalStorage/PDFObjectStore.swift
compile_module PDFPlatform ios/Sources/PDFPlatform/PDFReadSession.swift -lLocalStorage
xcrun --sdk iphonesimulator swiftc -parse-as-library -swift-version 6 -target arm64-apple-ios15.6-simulator \
  -sdk "$simulator_sdk" -module-cache-path tmp/swift-ios-module-cache \
  -I "$module_dir" -L "$module_dir" -lLocalStorage -lPDFPlatform \
  -Xlinker -rpath -Xlinker "$module_dir" probes/ios/PDFMain.swift -o tmp/ios-pdf-probe
xcrun simctl spawn "$1" "$PWD/tmp/ios-pdf-probe"
