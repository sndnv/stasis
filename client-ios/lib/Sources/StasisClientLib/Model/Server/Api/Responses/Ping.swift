import Foundation

public struct Ping: Sendable, Equatable, Hashable, Codable {
    public let id: UUID

    public init(id: UUID) {
        self.id = id
    }
}
