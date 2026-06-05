// swift-tools-version: 6.2
import PackageDescription

let package = Package(
    name: "StasisClientLib",
    platforms: [
        .iOS(.v26),
        .macOS(.v15)
    ],
    products: [
        .library(
            name: "StasisClientLib",
            targets: ["StasisClientLib"]
        ),
        .library(
            name: "StasisClientLibTestSupport",
            targets: ["StasisClientLibTestSupport"]
        )
    ],
    dependencies: [
        .package(url: "https://github.com/apple/swift-protobuf.git", from: "1.38.0"),
        .package(url: "https://github.com/sndnv/fsi-swift.git", from: "1.0.1"),
        .package(url: "https://github.com/1024jp/GzipSwift.git", from: "6.0.1"),
        .package(url: "https://github.com/davbeck/swift-glob.git", from: "1.0.0"),
        .package(path: "../../proto")
    ],
    targets: [
        .target(
            name: "StasisClientLib",
            dependencies: [
                .product(name: "SwiftProtobuf", package: "swift-protobuf"),
                .product(name: "fsi", package: "fsi-swift"),
                .product(name: "Gzip", package: "GzipSwift"),
                .product(name: "Glob", package: "swift-glob"),
                .product(name: "StasisSharedProto", package: "proto")
            ],
            plugins: [
                .plugin(name: "SwiftProtobufPlugin", package: "swift-protobuf")
            ]
        ),
        .target(
            name: "StasisClientLibTestSupport",
            dependencies: [
                "StasisClientLib",
                .product(name: "fsi", package: "fsi-swift")
            ]
        ),
        .testTarget(
            name: "StasisClientLibTests",
            dependencies: ["StasisClientLib", "StasisClientLibTestSupport"],
            exclude: ["Resources"]
        )
    ]
)
