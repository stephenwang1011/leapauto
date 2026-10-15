// swift-tools-version: 5.9
import PackageDescription

let package = Package(
    name: "LeapCore",
    platforms: [.iOS(.v17), .macOS(.v13)],
    products: [.library(name: "LeapCore", targets: ["LeapCore"])],
    targets: [
        .target(name: "LeapCore"),
        .testTarget(name: "LeapCoreTests", dependencies: ["LeapCore"])
    ]
)
