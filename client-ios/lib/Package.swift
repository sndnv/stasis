// swift-tools-version: 6.2
import PackageDescription

let package = Package(
    name: "StasisClientLib",
    platforms: [
        .iOS(.v26)
    ],
    products: [
        .library(
            name: "StasisClientLib",
            targets: ["StasisClientLib"]
        )
    ],
    targets: [
        .target(
            name: "StasisClientLib"
        ),
        .testTarget(
            name: "StasisClientLibTests",
            dependencies: ["StasisClientLib"]
        )
    ]
)
