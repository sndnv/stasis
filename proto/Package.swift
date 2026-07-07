// swift-tools-version: 6.2
import PackageDescription

let package = Package(
    name: "StasisSharedProto",
    platforms: [
        .iOS(.v26)
    ],
    products: [
        .library(
            name: "StasisSharedProto",
            targets: ["StasisSharedProto"]
        )
    ],
    dependencies: [
        .package(url: "https://github.com/apple/swift-protobuf.git", from: "1.38.0")
    ],
    targets: [
        .target(
            name: "StasisSharedProto",
            dependencies: [
                .product(name: "SwiftProtobuf", package: "swift-protobuf")
            ],
            path: "src/main/protobuf",
            exclude: [
                "common_aux_options.proto",
                "commands_aux_options.proto",
                "stasis.proto"
            ],
            resources: [
                .copy("swift-protobuf-config.json")
            ],
            plugins: [
                .plugin(name: "SwiftProtobufPlugin", package: "swift-protobuf")
            ]
        )
    ]
)
