import Foundation

public struct InvalidArgumentError: Error, Equatable, Hashable, LocalizedError {
    public let message: String

    public init(_ message: String) {
        self.message = message
    }

    public var errorDescription: String? { message }
}
