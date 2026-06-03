import Foundation

public struct OperationRestrictedFailure: Error, Sendable, Equatable, Hashable {
    public let restrictions: [OperationRestriction]

    public init(restrictions: [OperationRestriction]) {
        self.restrictions = restrictions
    }
}
