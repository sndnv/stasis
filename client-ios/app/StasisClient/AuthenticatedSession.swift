import Foundation
import StasisClientLib
import Synchronization

struct AuthenticatedSession: Sendable {
    let credentialsProvider: CredentialsProvider
    let serverApiClient: any ServerApiEndpointClient
    let serverCoreClient: any ServerCoreEndpointClient
    let operationExecutor: any OperationExecutor
    let secretRef: SecretRef

    func refreshDeviceSecret() async throws {
        let updated = try await credentialsProvider.currentDeviceSecret().get()
        secretRef.set(updated)
    }

    static func make(
        credentialsProvider: CredentialsProvider,
        configRepository: ConfigRepository,
        trackers: DefaultTrackers,
        analytics: any AnalyticsCollector
    ) async throws -> AuthenticatedSession {
        let identities = try Self.resolveIdentities(configRepository: configRepository)
        let initialSecret = try await credentialsProvider.currentDeviceSecret().get()
        let ref = SecretRef(initial: initialSecret)
        let resolveSecret: @Sendable () -> DeviceSecret = { ref.get() }
        let serverCore: any ServerCoreEndpointClient
        let serverApi: any ServerApiEndpointClient
        #if DEBUG
        if MockConfig.isMockServer(identities.apiUrl) {
            serverCore = MockServerCoreEndpointClient()
            serverApi = MockServerApiEndpointClient()
        } else {
            serverCore = DefaultServerCoreEndpointClient(
                serverCoreUrl: identities.coreAddress,
                credentialsProvider: credentialsProvider.coreCredentialsProvider(),
                selfNode: identities.selfNode
            )
            serverApi = DefaultServerApiEndpointClient(
                serverApiUrl: identities.apiUrl,
                credentialsProvider: credentialsProvider.apiCredentialsProvider(),
                decryption: .enabled(core: serverCore, deviceSecret: resolveSecret),
                selfDevice: identities.selfDevice
            )
        }
        #else
        serverCore = DefaultServerCoreEndpointClient(
            serverCoreUrl: identities.coreAddress,
            credentialsProvider: credentialsProvider.coreCredentialsProvider(),
            selfNode: identities.selfNode
        )
        serverApi = DefaultServerApiEndpointClient(
            serverApiUrl: identities.apiUrl,
            credentialsProvider: credentialsProvider.apiCredentialsProvider(),
            decryption: .enabled(core: serverCore, deviceSecret: resolveSecret),
            selfDevice: identities.selfDevice
        )
        #endif
        let executor = Self.makeExecutor(
            resolveSecret: resolveSecret,
            serverApi: serverApi,
            serverCore: serverCore,
            trackers: trackers,
            analytics: analytics
        )
        return AuthenticatedSession(
            credentialsProvider: credentialsProvider,
            serverApiClient: serverApi,
            serverCoreClient: serverCore,
            operationExecutor: executor,
            secretRef: ref
        )
    }

    final class SecretRef: Sendable {
        private let state: Mutex<DeviceSecret?>

        init(initial: DeviceSecret? = nil) {
            self.state = Mutex(initial)
        }

        func get() -> DeviceSecret {
            guard let secret = state.withLock({ $0 }) else {
                preconditionFailure("AuthenticatedSession.SecretRef accessed before initialization")
            }
            return secret
        }

        func set(_ value: DeviceSecret) { state.withLock { $0 = value } }
    }

    private struct Identities {
        let apiUrl: String
        let coreAddress: String
        let selfDevice: DeviceId
        let selfNode: NodeId
    }

    private static func resolveIdentities(configRepository: ConfigRepository) throws -> Identities {
        let preferences = configRepository.preferencesStore
        guard let apiConfig = try preferences.serverApiConfig() else {
            throw SessionError.missingServerApiConfig
        }
        guard let coreConfig = try preferences.serverCoreConfig() else {
            throw SessionError.missingServerCoreConfig
        }
        guard let selfDevice = UUID(uuidString: apiConfig.device) else {
            throw SessionError.invalidServerApiDeviceId(apiConfig.device)
        }
        guard let selfNode = UUID(uuidString: coreConfig.nodeId) else {
            throw SessionError.invalidServerCoreNodeId(coreConfig.nodeId)
        }
        return Identities(
            apiUrl: apiConfig.url,
            coreAddress: coreConfig.address,
            selfDevice: selfDevice,
            selfNode: selfNode
        )
    }

    private static func makeExecutor(
        resolveSecret: @escaping @Sendable () -> DeviceSecret,
        serverApi: any ServerApiEndpointClient,
        serverCore: any ServerCoreEndpointClient,
        trackers: DefaultTrackers,
        analytics: any AnalyticsCollector
    ) -> any OperationExecutor {
        let clients: any Clients = StaticClients(api: serverApi, core: serverCore)
        let staging: any FileStaging = DefaultFileStaging(
            storeDirectory: nil, prefix: "", suffix: ""
        )
        let compression: any Compression = Compressions.create(
            defaultCompression: Gzip.shared,
            disabledExtensions: Self.compressionDisabledExtensions
        )
        return DefaultOperationExecutor(
            config: .init(backup: .init(limits: .init(maxPartSize: Self.maxBackupPartSize))),
            deviceSecret: resolveSecret,
            backupProviders: BackupProviders(
                checksum: Checksums.sha256,
                staging: staging,
                compression: compression,
                encryptor: Aes.shared,
                decryptor: Aes.shared,
                clients: clients,
                track: trackers.backup,
                analytics: analytics
            ),
            recoveryProviders: RecoveryProviders(
                checksum: Checksums.sha256,
                staging: staging,
                compression: compression,
                decryptor: Aes.shared,
                clients: clients,
                track: trackers.recovery,
                analytics: analytics
            ),
            restrictions: { _ in [] }
        )
    }

    private static let maxBackupPartSize: Int64 = 32 * 1024 * 1024

    private static let compressionDisabledExtensions: Set<String> = [
        "mp3", "mp4", "wav", "ogg", "flac", "webm",
        "jpg", "png", "gif", "webp", "heic",
        "aac", "amr"
    ]
}

enum SessionError: Error, Equatable {
    case missingServerApiConfig
    case missingServerCoreConfig
    case invalidServerApiDeviceId(String)
    case invalidServerCoreNodeId(String)
}
