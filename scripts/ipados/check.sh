#!/bin/sh
set -eu
cd "$(dirname "$0")/../.."
mkdir -p tmp
ruby scripts/ipados/audit-portability.rb > tmp/ipados-portability.json
swift test --package-path ios --scratch-path tmp/ipados-swift-build --disable-sandbox
xcodebuild -project ios/XNotes.xcodeproj -scheme XNotes -sdk iphonesimulator \
  -configuration Debug -derivedDataPath tmp/ipados-derived CODE_SIGNING_ALLOWED=NO build
