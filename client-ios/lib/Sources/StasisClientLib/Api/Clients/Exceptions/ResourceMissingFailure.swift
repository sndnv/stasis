import Foundation

public struct ResourceMissingFailure: Error, Equatable, LocalizedError {
    public init() {}

    public var errorDescription: String? { "The requested resource was not found" }
}
