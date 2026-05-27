import Foundation

public struct ResetUserPassword: Sendable, Equatable, Hashable, Codable {
    public let rawPassword: String

    public init(rawPassword: String) {
        self.rawPassword = rawPassword
    }
}
