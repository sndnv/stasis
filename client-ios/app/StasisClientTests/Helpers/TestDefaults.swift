import Foundation
@testable import StasisClient
import StasisClientLib

enum TestDefaults {
    static func isolatedDefaults() -> UserDefaults {
        let suite = UUID().uuidString
        let defaults = UserDefaults(suiteName: suite)!
        defaults.removePersistentDomain(forName: suite)
        return defaults
    }

    static func bootstrapParams() -> DeviceBootstrapParameters {
        bootstrapParamsWithApi()
    }

    static func bootstrapParamsWithApi(
        user: String = UUID().uuidString,
        device: String = UUID().uuidString
    ) -> DeviceBootstrapParameters {
        DeviceBootstrapParameters(
            authentication: .init(
                tokenEndpoint: "http://localhost/token",
                clientId: "client-id",
                clientSecret: "client-secret",
                scopes: .init(api: "scope-api", core: "scope-core")
            ),
            serverApi: .init(
                url: "http://localhost/api",
                user: user,
                userSalt: "salt-value",
                device: device
            ),
            serverCore: .init(address: "http://localhost/core", nodeId: UUID().uuidString),
            secrets: .init(
                derivation: .init(
                    encryption: .init(secretSize: 32, iterations: 150_000, saltPrefix: "enc"),
                    authentication: .init(enabled: true, secretSize: 16, iterations: 150_000, saltPrefix: "auth")
                ),
                encryption: .init(
                    file: .init(keySize: 16, ivSize: 12),
                    metadata: .init(keySize: 16, ivSize: 12),
                    deviceSecret: .init(keySize: 16, ivSize: 12)
                )
            )
        )
    }

    static func trackers() throws -> DefaultTrackers {
        DefaultTrackers(
            backup: DefaultBackupTracker(store: try TestStateStore.backups()),
            recovery: DefaultRecoveryTracker(store: try TestStateStore.recoveries()),
            server: DefaultServerTracker()
        )
    }

    static func apiConfig(
        user: UUID = UUID(),
        device: UUID = UUID(),
        salt: String = "salt-value"
    ) -> Config.ServerApi {
        Config.ServerApi(
            url: "http://localhost/api",
            user: user.uuidString,
            userSalt: salt,
            device: device.uuidString
        )
    }
}
