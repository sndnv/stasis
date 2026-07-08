#if DEBUG
import Foundation
import StasisClientLib

enum MockConfig {
    static let tokenApi: String = "https://mock:4241"
    static let serverApi: String = "https://mock:4242"
    static let serverCore: String = "https://mock:4243"
    static let serverNode: NodeId = UUID()

    static let user: UserId = UUID()

    static let device: DeviceId = UUID()
    static let deviceNode: NodeId = UUID()

    static let bootstrapParameters: DeviceBootstrapParameters = DeviceBootstrapParameters(
        authentication: .init(
            tokenEndpoint: "\(tokenApi)/oauth/token",
            clientId: "test-client-id",
            clientSecret: "test-client-secret",
            scopes: .init(api: "test-api", core: "test-core")
        ),
        serverApi: .init(
            url: serverApi,
            user: user.uuidString,
            userSalt: "test-user-salt",
            device: device.uuidString
        ),
        serverCore: .init(address: serverCore, nodeId: serverNode.uuidString),
        secrets: .init(
            derivation: .init(
                encryption: .init(secretSize: 16, iterations: 100_000, saltPrefix: "test-encryption-prefix"),
                authentication: .init(enabled: true, secretSize: 16, iterations: 100_000, saltPrefix: "test-authentication-prefix")
            ),
            encryption: .init(
                file: .init(keySize: 16, ivSize: 12),
                metadata: .init(keySize: 16, ivSize: 12),
                deviceSecret: .init(keySize: 16, ivSize: 12)
            )
        )
    )

    static func isMockServer(_ server: String) -> Bool {
        server == serverApi
    }

    static func isMockTokenEndpoint(_ endpoint: String) -> Bool {
        endpoint.hasPrefix(tokenApi)
    }
}
#endif
