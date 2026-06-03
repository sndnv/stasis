import Foundation

public protocol OperationState: Sendable {
    var type: OperationType { get }
    var started: Date { get }
    var completed: Date? { get }
    func asProgress() -> OperationProgress
}
