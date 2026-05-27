import Foundation

public struct UpdatedUserSalt: Sendable, Equatable, Hashable, Codable {
    public let salt: String

    public init(salt: String) {
        self.salt = salt
    }
}
