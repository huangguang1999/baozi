// swift-tools-version: 6.2
import PackageDescription

let package = Package(
    name: "Baozi",
    platforms: [
        .iOS(.v26)
    ],
    products: [
        .library(name: "Baozi", targets: ["Baozi"])
    ],
    targets: [
        .binaryTarget(
            name: "codex_bridge",
            path: "apps/ios/Frameworks/codex_bridge.xcframework"
        ),
        .target(
            name: "Baozi",
            dependencies: ["codex_bridge"],
            path: "apps/ios/Sources/Baozi",
            publicHeadersPath: "Bridge"
        )
    ]
)
