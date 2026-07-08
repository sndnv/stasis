import Foundation

public protocol Secret: Sendable, CustomStringConvertible {}

public extension Secret {
    var description: String { "Secret(\(String(reflecting: type(of: self))))" }
}

public enum SecretError: Error, Equatable, LocalizedError {
    case passwordAlreadyExtracted

    public var errorDescription: String? {
        switch self {
        case .passwordAlreadyExtracted:
            "Password already extracted"
        }
    }
}

public struct EncryptionSecretConfig: Sendable, Equatable, Hashable {
    public static let minKeySize: Int = 16
    public static let minIvSize: Int = 12

    public let keySize: Int
    public let ivSize: Int

    public init(keySize: Int, ivSize: Int) throws {
        guard keySize >= Self.minKeySize else {
            throw InvalidArgumentError("key must not be smaller than 16 bytes")
        }
        guard ivSize >= Self.minIvSize else {
            throw InvalidArgumentError("iv must not be smaller than 12 bytes")
        }
        self.keySize = keySize
        self.ivSize = ivSize
    }
}

public struct EncryptionKeyDerivationConfig: Sendable, Equatable, Hashable {
    public static let minSecretSize: Int = 16
    public static let minIterations: Int = 100000

    public let secretSize: Int
    public let iterations: Int
    public let saltPrefix: String

    public init(secretSize: Int, iterations: Int, saltPrefix: String) throws {
        guard secretSize >= Self.minSecretSize else {
            throw InvalidArgumentError("secret must not be smaller than 16 bytes")
        }
        guard iterations >= Self.minIterations else {
            throw InvalidArgumentError("iterations must not be fewer than 100k")
        }
        self.secretSize = secretSize
        self.iterations = iterations
        self.saltPrefix = saltPrefix
    }
}

public struct AuthenticationKeyDerivationConfig: Sendable, Equatable, Hashable {
    public static let minSecretSize: Int = 16
    public static let minIterations: Int = 100000

    public let enabled: Bool
    public let secretSize: Int
    public let iterations: Int
    public let saltPrefix: String

    public init(enabled: Bool, secretSize: Int, iterations: Int, saltPrefix: String) throws {
        guard secretSize >= Self.minSecretSize else {
            throw InvalidArgumentError("secret must not be smaller than 16 bytes")
        }
        guard iterations >= Self.minIterations else {
            throw InvalidArgumentError("iterations must not be fewer than 100k")
        }
        self.enabled = enabled
        self.secretSize = secretSize
        self.iterations = iterations
        self.saltPrefix = saltPrefix
    }
}

public struct SecretConfig: Sendable, Equatable, Hashable {
    public let derivation: DerivationConfig
    public let encryption: EncryptionConfig

    public init(derivation: DerivationConfig, encryption: EncryptionConfig) {
        self.derivation = derivation
        self.encryption = encryption
    }

    public struct DerivationConfig: Sendable, Equatable, Hashable {
        public let encryption: EncryptionKeyDerivationConfig
        public let authentication: AuthenticationKeyDerivationConfig

        public init(
            encryption: EncryptionKeyDerivationConfig,
            authentication: AuthenticationKeyDerivationConfig
        ) {
            self.encryption = encryption
            self.authentication = authentication
        }
    }

    public struct EncryptionConfig: Sendable, Equatable, Hashable {
        public let file: EncryptionSecretConfig
        public let metadata: EncryptionSecretConfig
        public let deviceSecret: EncryptionSecretConfig

        public init(
            file: EncryptionSecretConfig,
            metadata: EncryptionSecretConfig,
            deviceSecret: EncryptionSecretConfig
        ) {
            self.file = file
            self.metadata = metadata
            self.deviceSecret = deviceSecret
        }
    }
}

extension UUID {
    var bytes: Data {
        withUnsafeBytes(of: uuid) { Data($0) }
    }
}
