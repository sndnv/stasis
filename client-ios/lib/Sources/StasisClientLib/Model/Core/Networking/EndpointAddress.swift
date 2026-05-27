import Foundation

public enum EndpointAddress: Sendable, Equatable, Hashable {
    case http(uri: String)
    case grpc(host: String, port: Int, tlsEnabled: Bool)
}

extension EndpointAddress: Codable {
    private enum CodingKeys: String, CodingKey {
        case addressType
        case address
    }

    private enum AddressType: String, Codable {
        case http
        case grpc
    }

    private struct HttpAddress: Codable {
        let uri: String
    }

    private struct GrpcAddress: Codable {
        let host: String
        let port: Int
        let tlsEnabled: Bool
    }

    public init(from decoder: any Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        let addressType = try container.decode(AddressType.self, forKey: .addressType)
        switch addressType {
        case .http:
            let inner = try container.decode(HttpAddress.self, forKey: .address)
            self = .http(uri: inner.uri)
        case .grpc:
            let inner = try container.decode(GrpcAddress.self, forKey: .address)
            self = .grpc(host: inner.host, port: inner.port, tlsEnabled: inner.tlsEnabled)
        }
    }

    public func encode(to encoder: any Encoder) throws {
        var container = encoder.container(keyedBy: CodingKeys.self)
        switch self {
        case .http(let uri):
            try container.encode(AddressType.http, forKey: .addressType)
            try container.encode(HttpAddress(uri: uri), forKey: .address)
        case .grpc(let host, let port, let tlsEnabled):
            try container.encode(AddressType.grpc, forKey: .addressType)
            try container.encode(GrpcAddress(host: host, port: port, tlsEnabled: tlsEnabled), forKey: .address)
        }
    }
}
