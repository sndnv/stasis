import Foundation

public struct InvalidArgumentError: Error, Equatable, Hashable {
    public let message: String

    public init(_ message: String) {
        self.message = message
    }
}
