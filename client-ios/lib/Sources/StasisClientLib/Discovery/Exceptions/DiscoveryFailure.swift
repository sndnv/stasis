import Foundation

public struct DiscoveryFailure: Error, Equatable, LocalizedError {
    public let message: String

    public init(message: String) {
        self.message = message
    }

    public var errorDescription: String? { message }
}
