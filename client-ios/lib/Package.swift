// swift-tools-version: 6.2
import PackageDescription

let package = Package(
    name: "StasisClientLib",
    platforms: [
        .iOS(.v26),
        .macOS(.v13)
    ],
    products: [
        .library(
            name: "StasisClientLib",
            targets: ["StasisClientLib"]
        )
    ],
    dependencies: [
        .package(url: "https://github.com/apple/swift-protobuf.git", from: "1.38.0"),
        .package(url: "https://github.com/sndnv/fsi-swift.git", from: "1.0.1"),
        .package(path: "../../proto")
    ],
    targets: [
        .target(
            name: "StasisClientLib",
            dependencies: [
                .product(name: "SwiftProtobuf", package: "swift-protobuf"),
                .product(name: "fsi", package: "fsi-swift"),
                .product(name: "StasisSharedProto", package: "proto")
            ],
            plugins: [
                .plugin(name: "SwiftProtobufPlugin", package: "swift-protobuf")
            ]
        ),
        .testTarget(
            name: "StasisClientLibTests",
            dependencies: ["StasisClientLib"],
            exclude: ["Resources"]
        )
    ]
)
