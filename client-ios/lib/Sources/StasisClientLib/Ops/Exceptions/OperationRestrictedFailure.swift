import Foundation

public struct OperationRestrictedFailure: Error, Sendable, Equatable, Hashable, LocalizedError {
    public let restrictions: [OperationRestriction]

    public init(restrictions: [OperationRestriction]) {
        self.restrictions = restrictions
    }

    public var errorDescription: String? {
        "Operation restricted: \(restrictions.map(\.summary).joined(separator: ", "))"
    }
}
