import Foundation

public struct TokenExpired: Error, Equatable, LocalizedError {
    public init() {}

    public var errorDescription: String? { "The authentication token has expired" }
}
