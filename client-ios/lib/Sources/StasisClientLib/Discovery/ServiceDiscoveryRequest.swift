import Foundation

public struct ServiceDiscoveryRequest: Sendable, Equatable, Hashable, Codable {
    public let isInitialRequest: Bool
    public let attributes: [String: String]

    public init(isInitialRequest: Bool, attributes: [String: String]) {
        self.isInitialRequest = isInitialRequest
        self.attributes = attributes
    }

    public var id: String {
        attributes
            .sorted { $0.key < $1.key }
            .map { "\($0.key)=\($0.value)" }
            .joined(separator: "::")
    }
}
