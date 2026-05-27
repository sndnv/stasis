import Foundation

public enum ServiceDiscoveryResult: Sendable, Equatable, Hashable {
    case keepExisting
    case switchTo(endpoints: Endpoints, recreateExisting: Bool)

    public var asString: String {
        switch self {
        case .keepExisting:
            "result=keep-existing"
        case .switchTo(let endpoints, let recreateExisting):
            "result=switch-to,endpoints=\(endpoints.asString),recreate-existing=\(recreateExisting)"
        }
    }

    public struct Endpoints: Sendable, Equatable, Hashable, Codable {
        public let api: ServiceApiEndpoint.Api
        public let core: ServiceApiEndpoint.Core
        public let discovery: ServiceApiEndpoint.Discovery

        public init(
            api: ServiceApiEndpoint.Api,
            core: ServiceApiEndpoint.Core,
            discovery: ServiceApiEndpoint.Discovery
        ) {
            self.api = api
            self.core = core
            self.discovery = discovery
        }

        public var asString: String {
            "\(api.id);\(core.id);\(discovery.id)"
        }
    }
}

extension ServiceDiscoveryResult: Codable {
    private enum CodingKeys: String, CodingKey {
        case result
        case endpoints
        case recreateExisting
    }

    private enum ResultType: String, Codable {
        case keepExisting = "keep-existing"
        case switchTo = "switch-to"
    }

    public init(from decoder: any Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        let result = try container.decode(ResultType.self, forKey: .result)
        switch result {
        case .keepExisting:
            self = .keepExisting
        case .switchTo:
            let endpoints = try container.decode(Endpoints.self, forKey: .endpoints)
            let recreate = try container.decode(Bool.self, forKey: .recreateExisting)
            self = .switchTo(endpoints: endpoints, recreateExisting: recreate)
        }
    }

    public func encode(to encoder: any Encoder) throws {
        var container = encoder.container(keyedBy: CodingKeys.self)
        switch self {
        case .keepExisting:
            try container.encode(ResultType.keepExisting, forKey: .result)
        case .switchTo(let endpoints, let recreateExisting):
            try container.encode(ResultType.switchTo, forKey: .result)
            try container.encode(endpoints, forKey: .endpoints)
            try container.encode(recreateExisting, forKey: .recreateExisting)
        }
    }
}
