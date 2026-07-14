import Foundation
import StasisClientLib

public final class ConfigRepository: @unchecked Sendable {
    public static let suiteName: String = "group.stasis.client.ios"

    public enum Keys {
        public enum Authentication {
            public static let tokenEndpoint = "authentication_token_endpoint"
            public static let clientId = "authentication_client_id"
            public static let clientSecret = "authentication_client_secret"
            public static let scopeApi = "authentication_scope_api"
            public static let scopeCore = "authentication_scope_core"
            public static let all: [String] = [tokenEndpoint, clientId, clientSecret, scopeApi, scopeCore]
        }

        public enum ServerApi {
            public static let url = "server_api_url"
            public static let user = "server_api_user"
            public static let userSalt = "server_api_user_salt"
            public static let device = "server_api_device"
            public static let all: [String] = [url, user, userSalt, device]
        }

        public enum ServerCore {
            public static let address = "server_core_address"
            public static let nodeId = "server_core_node_id"
            public static let all: [String] = [address, nodeId]
        }

        public enum Secrets {
            public static let derivationEncryptionSecretSize = "secrets_derivation_encryption_secrets_size"
            public static let derivationEncryptionIterations = "secrets_derivation_encryption_iterations"
            public static let derivationEncryptionSaltPrefix = "secrets_derivation_encryption_salt_prefix"
            public static let derivationAuthenticationEnabled = "secrets_derivation_authentication_enabled"
            public static let derivationAuthenticationSecretSize = "secrets_derivation_authentication_secrets_size"
            public static let derivationAuthenticationIterations = "secrets_derivation_authentication_iterations"
            public static let derivationAuthenticationSaltPrefix = "secrets_derivation_authentication_salt_prefix"
            public static let encryptionFileKeySize = "secrets_encryption_file_key_size"
            public static let encryptionMetadataKeySize = "secrets_encryption_metadata_key_size"
            public static let encryptionDeviceSecretKeySize = "secrets_encryption_device_secret_key_size"
            public static let encryptedDeviceSecret = "secrets_encrypted_device_secret"
        }

        public enum General {
            public static let isFirstRun = "general_is_first_run"
            public static let savedUsername = "general_saved_username"
            public static let lastProcessedCommand = "last_processed_command"
        }

        public enum Analytics {
            public static let entryCache = "analytics_entry_cache"
            public static let pendingCache = "analytics_pending_cache"
        }
    }

    public enum Defaults {
        public enum Secrets {
            public enum Derivation {
                public enum Encryption {
                    public static let secretSize: Int = 32
                    public static let iterations: Int = 150_000
                    public static let saltPrefix: String = "changeme"
                }

                public enum Authentication {
                    public static let enabled: Bool = true
                    public static let secretSize: Int = 16
                    public static let iterations: Int = 150_000
                    public static let saltPrefix: String = "changeme"
                }
            }

            public enum Encryption {
                public enum File {
                    public static let keySize: Int = 16
                }

                public enum Metadata {
                    public static let keySize: Int = 16
                }

                public enum DeviceSecret {
                    public static let keySize: Int = 16
                }
            }
        }

        public enum General {
            public static let isFirstRun: Bool = true
        }
    }

    public enum RepositoryError: Error, Equatable, LocalizedError {
        case malformedConfig(String)
        case missingDeviceSecret

        public var errorDescription: String? {
            switch self {
            case .malformedConfig(let reason):
                "Malformed configuration: \(reason)"
            case .missingDeviceSecret:
                "No device secret is available"
            }
        }
    }

    private let preferences: UserDefaults

    public init(preferences: UserDefaults) {
        self.preferences = preferences
    }

    public convenience init?(suiteName: String = ConfigRepository.suiteName) {
        guard let defaults = UserDefaults(suiteName: suiteName) else { return nil }
        self.init(preferences: defaults)
    }

    public func available() throws -> Bool {
        let configs: [Any?] = try [
            preferences.authenticationConfig(),
            preferences.serverApiConfig(),
            preferences.serverCoreConfig()
        ]
        return configs.contains { $0 != nil }
    }

    public func bootstrap(params: DeviceBootstrapParameters) {
        preferences.set(params.authentication.tokenEndpoint, forKey: Keys.Authentication.tokenEndpoint)
        preferences.set(params.authentication.clientId, forKey: Keys.Authentication.clientId)
        preferences.set(params.authentication.clientSecret, forKey: Keys.Authentication.clientSecret)
        preferences.set(params.authentication.scopes.api, forKey: Keys.Authentication.scopeApi)
        preferences.set(params.authentication.scopes.core, forKey: Keys.Authentication.scopeCore)
        preferences.set(params.serverApi.url, forKey: Keys.ServerApi.url)
        preferences.set(params.serverApi.user, forKey: Keys.ServerApi.user)
        preferences.set(params.serverApi.userSalt, forKey: Keys.ServerApi.userSalt)
        preferences.set(params.serverApi.device, forKey: Keys.ServerApi.device)
        preferences.set(params.serverCore.address, forKey: Keys.ServerCore.address)
        preferences.set(params.serverCore.nodeId, forKey: Keys.ServerCore.nodeId)
        preferences.set(
            params.secrets.derivation.encryption.secretSize,
            forKey: Keys.Secrets.derivationEncryptionSecretSize
        )
        preferences.set(
            params.secrets.derivation.encryption.iterations,
            forKey: Keys.Secrets.derivationEncryptionIterations
        )
        preferences.set(
            params.secrets.derivation.encryption.saltPrefix,
            forKey: Keys.Secrets.derivationEncryptionSaltPrefix
        )
        preferences.set(
            params.secrets.derivation.authentication.enabled,
            forKey: Keys.Secrets.derivationAuthenticationEnabled
        )
        preferences.set(
            params.secrets.derivation.authentication.secretSize,
            forKey: Keys.Secrets.derivationAuthenticationSecretSize
        )
        preferences.set(
            params.secrets.derivation.authentication.iterations,
            forKey: Keys.Secrets.derivationAuthenticationIterations
        )
        preferences.set(
            params.secrets.derivation.authentication.saltPrefix,
            forKey: Keys.Secrets.derivationAuthenticationSaltPrefix
        )
        preferences.set(params.secrets.encryption.file.keySize, forKey: Keys.Secrets.encryptionFileKeySize)
        preferences.set(params.secrets.encryption.metadata.keySize, forKey: Keys.Secrets.encryptionMetadataKeySize)
        preferences.set(
            params.secrets.encryption.deviceSecret.keySize,
            forKey: Keys.Secrets.encryptionDeviceSecretKeySize
        )
    }

    public func reset() {
        let keys: [String] = Keys.Authentication.all
            + Keys.ServerApi.all
            + Keys.ServerCore.all
            + [
                Keys.Secrets.derivationEncryptionSecretSize,
                Keys.Secrets.derivationEncryptionIterations,
                Keys.Secrets.derivationEncryptionSaltPrefix,
                Keys.Secrets.derivationAuthenticationEnabled,
                Keys.Secrets.derivationAuthenticationSecretSize,
                Keys.Secrets.derivationAuthenticationIterations,
                Keys.Secrets.derivationAuthenticationSaltPrefix,
                Keys.Secrets.encryptionFileKeySize,
                Keys.Secrets.encryptionMetadataKeySize,
                Keys.Secrets.encryptionDeviceSecretKeySize,
                Keys.Secrets.encryptedDeviceSecret,
                Keys.General.isFirstRun,
                Keys.General.savedUsername,
                Keys.General.lastProcessedCommand,
                Keys.Analytics.entryCache,
                Keys.Analytics.pendingCache
            ]
        for key in keys { preferences.removeObject(forKey: key) }
    }

    public var preferencesStore: UserDefaults { preferences }
}

public extension UserDefaults {
    func authenticationConfig() throws -> Config.Authentication? {
        let params = ConfigRepository.Keys.Authentication.all.compactMap { string(forKey: $0) }
        switch params.count {
        case 0:
            return nil
        case ConfigRepository.Keys.Authentication.all.count:
            return Config.Authentication(
                tokenEndpoint: params[0],
                clientId: params[1],
                clientSecret: params[2],
                scopeApi: params[3],
                scopeCore: params[4]
            )
        default:
            throw ConfigRepository.RepositoryError.malformedConfig(
                "Expected [\(ConfigRepository.Keys.Authentication.all.count)] authentication parameters" +
                    " but [\(params.count)] found"
            )
        }
    }

    func serverApiConfig() throws -> Config.ServerApi? {
        let params = ConfigRepository.Keys.ServerApi.all.compactMap { string(forKey: $0) }
        switch params.count {
        case 0:
            return nil
        case ConfigRepository.Keys.ServerApi.all.count:
            return Config.ServerApi(
                url: params[0],
                user: params[1],
                userSalt: params[2],
                device: params[3]
            )
        default:
            throw ConfigRepository.RepositoryError.malformedConfig(
                "Expected [\(ConfigRepository.Keys.ServerApi.all.count)] server API parameters" +
                    " but [\(params.count)] found"
            )
        }
    }

    func serverCoreConfig() throws -> Config.ServerCore? {
        let params = ConfigRepository.Keys.ServerCore.all.compactMap { string(forKey: $0) }
        switch params.count {
        case 0:
            return nil
        case ConfigRepository.Keys.ServerCore.all.count:
            return Config.ServerCore(address: params[0], nodeId: params[1])
        default:
            throw ConfigRepository.RepositoryError.malformedConfig(
                "Expected [\(ConfigRepository.Keys.ServerCore.all.count)] server core parameters" +
                    " but [\(params.count)] found"
            )
        }
    }

    func secretsConfig() throws -> SecretConfig {
        try SecretConfig(
            derivation: .init(
                encryption: derivationEncryptionConfig(),
                authentication: derivationAuthenticationConfig()
            ),
            encryption: .init(
                file: encryptionConfig(
                    keySizeKey: ConfigRepository.Keys.Secrets.encryptionFileKeySize,
                    defaultKeySize: ConfigRepository.Defaults.Secrets.Encryption.File.keySize
                ),
                metadata: encryptionConfig(
                    keySizeKey: ConfigRepository.Keys.Secrets.encryptionMetadataKeySize,
                    defaultKeySize: ConfigRepository.Defaults.Secrets.Encryption.Metadata.keySize
                ),
                deviceSecret: encryptionConfig(
                    keySizeKey: ConfigRepository.Keys.Secrets.encryptionDeviceSecretKeySize,
                    defaultKeySize: ConfigRepository.Defaults.Secrets.Encryption.DeviceSecret.keySize
                )
            )
        )
    }

    private func derivationEncryptionConfig() throws -> EncryptionKeyDerivationConfig {
        try EncryptionKeyDerivationConfig(
            secretSize: integerOrDefault(
                forKey: ConfigRepository.Keys.Secrets.derivationEncryptionSecretSize,
                default: ConfigRepository.Defaults.Secrets.Derivation.Encryption.secretSize
            ),
            iterations: integerOrDefault(
                forKey: ConfigRepository.Keys.Secrets.derivationEncryptionIterations,
                default: ConfigRepository.Defaults.Secrets.Derivation.Encryption.iterations
            ),
            saltPrefix: string(forKey: ConfigRepository.Keys.Secrets.derivationEncryptionSaltPrefix)
                ?? ConfigRepository.Defaults.Secrets.Derivation.Encryption.saltPrefix
        )
    }

    private func derivationAuthenticationConfig() throws -> AuthenticationKeyDerivationConfig {
        try AuthenticationKeyDerivationConfig(
            enabled: value(forKey: ConfigRepository.Keys.Secrets.derivationAuthenticationEnabled) as? Bool
                ?? ConfigRepository.Defaults.Secrets.Derivation.Authentication.enabled,
            secretSize: integerOrDefault(
                forKey: ConfigRepository.Keys.Secrets.derivationAuthenticationSecretSize,
                default: ConfigRepository.Defaults.Secrets.Derivation.Authentication.secretSize
            ),
            iterations: integerOrDefault(
                forKey: ConfigRepository.Keys.Secrets.derivationAuthenticationIterations,
                default: ConfigRepository.Defaults.Secrets.Derivation.Authentication.iterations
            ),
            saltPrefix: string(forKey: ConfigRepository.Keys.Secrets.derivationAuthenticationSaltPrefix)
                ?? ConfigRepository.Defaults.Secrets.Derivation.Authentication.saltPrefix
        )
    }

    private func encryptionConfig(keySizeKey: String, defaultKeySize: Int) throws -> EncryptionSecretConfig {
        try EncryptionSecretConfig(
            keySize: integerOrDefault(forKey: keySizeKey, default: defaultKeySize),
            ivSize: Aes.ivSize
        )
    }

    func putEncryptedDeviceSecret(_ secret: Data) {
        set(secret.base64EncodedString(), forKey: ConfigRepository.Keys.Secrets.encryptedDeviceSecret)
    }

    func encryptedDeviceSecret() throws -> Data {
        guard let raw = string(forKey: ConfigRepository.Keys.Secrets.encryptedDeviceSecret),
              let decoded = Data(base64Encoded: raw) else {
            throw ConfigRepository.RepositoryError.missingDeviceSecret
        }
        return decoded
    }

    func isFirstRun() -> Bool {
        value(forKey: ConfigRepository.Keys.General.isFirstRun) as? Bool
            ?? ConfigRepository.Defaults.General.isFirstRun
    }

    func firstRunComplete() {
        set(false, forKey: ConfigRepository.Keys.General.isFirstRun)
    }

    func savedUsername() -> String? {
        string(forKey: ConfigRepository.Keys.General.savedUsername)
    }

    func saveUsername(_ username: String?) {
        if let username {
            set(username, forKey: ConfigRepository.Keys.General.savedUsername)
        } else {
            removeObject(forKey: ConfigRepository.Keys.General.savedUsername)
        }
    }

    func savedLastProcessedCommand() -> Int64 {
        (value(forKey: ConfigRepository.Keys.General.lastProcessedCommand) as? NSNumber)?.int64Value ?? 0
    }

    func saveLastProcessedCommand(_ sequenceId: Int64) {
        set(NSNumber(value: sequenceId), forKey: ConfigRepository.Keys.General.lastProcessedCommand)
    }

    func analyticsCachedEntry() -> String? {
        string(forKey: ConfigRepository.Keys.Analytics.entryCache)
    }

    func putAnalyticsCachedEntry(_ entry: String) {
        set(entry, forKey: ConfigRepository.Keys.Analytics.entryCache)
    }

    func analyticsPendingEntries() -> String? {
        string(forKey: ConfigRepository.Keys.Analytics.pendingCache)
    }

    func putAnalyticsPendingEntries(_ entries: String) {
        set(entries, forKey: ConfigRepository.Keys.Analytics.pendingCache)
    }

    private func integerOrDefault(forKey key: String, default fallback: Int) -> Int {
        guard let value = value(forKey: key) as? NSNumber else { return fallback }
        return value.intValue
    }
}
