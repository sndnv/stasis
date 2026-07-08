import Foundation

public struct AccessDeniedFailure: Error, Equatable, LocalizedError {
    public init() {}

    public var errorDescription: String? { "Access denied" }
}
