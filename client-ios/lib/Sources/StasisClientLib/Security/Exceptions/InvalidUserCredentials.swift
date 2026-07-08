import Foundation

public struct InvalidUserCredentials: Error, Equatable, LocalizedError {
    public init() {}

    public var errorDescription: String? { "Invalid credentials provided" }
}
