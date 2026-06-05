import Foundation

public struct Config: Sendable, Equatable {
    public let authentication: Authentication
    public let api: ServerApi
    public let core: ServerCore

    public init(authentication: Authentication, api: ServerApi, core: ServerCore) {
        self.authentication = authentication
        self.api = api
        self.core = core
    }

    public struct Authentication: Sendable, Equatable {
        public let tokenEndpoint: String
        public let clientId: String
        public let clientSecret: String
        public let scopeApi: String
        public let scopeCore: String

        public init(
            tokenEndpoint: String,
            clientId: String,
            clientSecret: String,
            scopeApi: String,
            scopeCore: String
        ) {
            self.tokenEndpoint = tokenEndpoint
            self.clientId = clientId
            self.clientSecret = clientSecret
            self.scopeApi = scopeApi
            self.scopeCore = scopeCore
        }
    }

    public struct ServerApi: Sendable, Equatable {
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

    public struct ServerCore: Sendable, Equatable {
        public let address: String
        public let nodeId: String

        public init(address: String, nodeId: String) {
            self.address = address
            self.nodeId = nodeId
        }
    }
}
