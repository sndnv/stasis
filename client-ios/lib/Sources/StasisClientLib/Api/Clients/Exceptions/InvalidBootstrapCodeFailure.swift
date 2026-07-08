import Foundation

public struct InvalidBootstrapCodeFailure: Error, Equatable, LocalizedError {
    public init() {}

    public var errorDescription: String? { "The provided bootstrap code is invalid" }
}
