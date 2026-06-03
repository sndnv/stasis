import Foundation

public struct RuleMatchingFailure: Error, Equatable {
    public let message: String

    public init(_ message: String) {
        self.message = message
    }
}
