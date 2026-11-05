// swift-tools-version: 6.0
import PackageDescription

let package = Package(
    name: "XNotesProbes",
    products: [.library(name: "InputProbe", targets: ["InputProbe"])],
    targets: [.target(name: "InputProbe"), .testTarget(name: "InputProbeTests", dependencies: ["InputProbe"])])
