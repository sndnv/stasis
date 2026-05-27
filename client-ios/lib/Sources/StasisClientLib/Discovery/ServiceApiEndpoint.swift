import Foundation

public enum ServiceApiEndpoint: Sendable, Equatable, Hashable {
    case api(Api)
    case core(Core)
    case discovery(Discovery)

    public var id: String {
        switch self {
        case .api(let inner): inner.id
        case .core(let inner): inner.id
        case .discovery(let inner): inner.id
        }
    }

    public struct Api: Sendable, Equatable, Hashable, Codable {
        public let uri: String

        public init(uri: String) {
            self.uri = uri
        }

        public var id: String { "api__\(uri)" }
    }

    public struct Core: Sendable, Equatable, Hashable, Codable {
        public let address: EndpointAddress

        public init(address: EndpointAddress) {
            self.address = address
        }

        public var id: String {
            switch address {
            case .http(let uri): "core_http__\(uri)"
            case .grpc(let host, let port, _): "core_grpc__\(host):\(port)"
            }
        }
    }

    public struct Discovery: Sendable, Equatable, Hashable, Codable {
        public let uri: String

        public init(uri: String) {
            self.uri = uri
        }

        public var id: String { "discovery__\(uri)" }
    }
}
