// swift-tools-version: 6.0
import PackageDescription

let package = Package(
    name: "XNotesProbes",
    platforms: [.macOS(.v12), .iOS("15.6")],
    products: [
        .library(name: "InputProbe", targets: ["InputProbe"]),
        .library(name: "LocalStorage", targets: ["LocalStorage"]),
        .library(name: "PDFPlatform", targets: ["PDFPlatform"])
    ],
    targets: [
        .target(name: "PDFPlatform", dependencies: ["LocalStorage"]),
        .testTarget(name: "PDFPlatformTests", dependencies: ["PDFPlatform", "LocalStorage"]),
        .target(name: "LocalStorage"),
        .testTarget(name: "LocalStorageTests", dependencies: ["LocalStorage"]),
        .target(name: "InputProbe"),
        .testTarget(name: "InputProbeTests", dependencies: ["InputProbe"])
    ])
