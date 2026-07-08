import Foundation

public struct ExplicitLogout: Error, Equatable, LocalizedError {
    public init() {}

    public var errorDescription: String? { "The session was logged out" }
}
