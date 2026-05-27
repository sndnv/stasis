import Foundation

public struct DeviceBootstrapParameters: Sendable, Equatable, Hashable, Codable {
    public let authentication: Authentication
    public let serverApi: ServerApi
    public let serverCore: ServerCore
    public let secrets: SecretsConfig

    public init(
        authentication: Authentication,
        serverApi: ServerApi,
        serverCore: ServerCore,
        secrets: SecretsConfig
    ) {
        self.authentication = authentication
        self.serverApi = serverApi
        self.serverCore = serverCore
        self.secrets = secrets
    }

    public struct Authentication: Sendable, Equatable, Hashable, Codable {
        public let tokenEndpoint: String
        public let clientId: String
        public let clientSecret: String
        public let scopes: Scopes

        public init(tokenEndpoint: String, clientId: String, clientSecret: String, scopes: Scopes) {
            self.tokenEndpoint = tokenEndpoint
            self.clientId = clientId
            self.clientSecret = clientSecret
            self.scopes = scopes
        }
    }

    public struct ServerApi: Sendable, Equatable, Hashable, Codable {
        public let url: String
        public let user: String
        public let userSalt: String
        public let device: String

        public init(url: String, user: String, userSalt: String, device: String) {
            self.url = url
            self.user = user
            self.userSalt = userSalt
            self.device = device
        }
    }

    public struct ServerCore: Sendable, Equatable, Hashable, Codable {
        public let address: String
        public let nodeId: String

        public init(address: String, nodeId: String) {
            self.address = address
            self.nodeId = nodeId
        }
    }

    public struct Scopes: Sendable, Equatable, Hashable, Codable {
        public let api: String
        public let core: String

        public init(api: String, core: String) {
            self.api = api
            self.core = core
        }
    }

    public struct SecretsConfig: Sendable, Equatable, Hashable, Codable {
        public let derivation: Derivation
        public let encryption: Encryption

        public init(derivation: Derivation, encryption: Encryption) {
            self.derivation = derivation
            self.encryption = encryption
        }

        public struct Derivation: Sendable, Equatable, Hashable, Codable {
            public let encryption: Encryption
            public let authentication: Authentication

            public init(encryption: Encryption, authentication: Authentication) {
                self.encryption = encryption
                self.authentication = authentication
            }

            public struct Encryption: Sendable, Equatable, Hashable, Codable {
                public let secretSize: Int
                public let iterations: Int
                public let saltPrefix: String

                public init(secretSize: Int, iterations: Int, saltPrefix: String) {
                    self.secretSize = secretSize
                    self.iterations = iterations
                    self.saltPrefix = saltPrefix
                }
            }

            public struct Authentication: Sendable, Equatable, Hashable, Codable {
                public let enabled: Bool
                public let secretSize: Int
                public let iterations: Int
                public let saltPrefix: String

                public init(enabled: Bool, secretSize: Int, iterations: Int, saltPrefix: String) {
                    self.enabled = enabled
                    self.secretSize = secretSize
                    self.iterations = iterations
                    self.saltPrefix = saltPrefix
                }
            }
        }

        public struct Encryption: Sendable, Equatable, Hashable, Codable {
            public let file: File
            public let metadata: Metadata
            public let deviceSecret: DeviceSecret

            public init(file: File, metadata: Metadata, deviceSecret: DeviceSecret) {
                self.file = file
                self.metadata = metadata
                self.deviceSecret = deviceSecret
            }

            public struct File: Sendable, Equatable, Hashable, Codable {
                public let keySize: Int
                public let ivSize: Int

                public init(keySize: Int, ivSize: Int) {
                    self.keySize = keySize
                    self.ivSize = ivSize
                }
            }

            public struct Metadata: Sendable, Equatable, Hashable, Codable {
                public let keySize: Int
                public let ivSize: Int

                public init(keySize: Int, ivSize: Int) {
                    self.keySize = keySize
                    self.ivSize = ivSize
                }
            }

            public struct DeviceSecret: Sendable, Equatable, Hashable, Codable {
                public let keySize: Int
                public let ivSize: Int

                public init(keySize: Int, ivSize: Int) {
                    self.keySize = keySize
                    self.ivSize = ivSize
                }
            }
        }
    }
}
