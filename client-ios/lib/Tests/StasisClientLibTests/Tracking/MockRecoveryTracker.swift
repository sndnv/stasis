import Foundation
@testable import StasisClientLib

final class MockRecoveryTracker: RecoveryTracker {
    enum Statistic: String, CaseIterable, Sendable {
        case started = "Started"
        case entityExamined = "EntityExamined"
        case entityCollected = "EntityCollected"
        case entityProcessingStarted = "EntityProcessingStarted"
        case entityPartProcessed = "EntityPartProcessed"
        case entityProcessed = "EntityProcessed"
        case metadataApplied = "MetadataApplied"
        case failureEncountered = "FailureEncountered"
        case completed = "Completed"
    }

    private let counter = StatsCounter<Statistic>()

    var statistics: [Statistic: Int] { counter.snapshot }

    func started(operation: OperationId) { counter.increment(.started) }
    func entityExamined(operation: OperationId, entity: URL, metadataChanged: Bool, contentChanged: Bool) {
        counter.increment(.entityExamined)
    }
    func entityCollected(operation: OperationId, entity: TargetEntity) { counter.increment(.entityCollected) }
    func entityProcessingStarted(operation: OperationId, entity: URL, expectedParts: Int) {
        counter.increment(.entityProcessingStarted)
    }
    func entityPartProcessed(operation: OperationId, entity: URL) { counter.increment(.entityPartProcessed) }
    func entityProcessed(operation: OperationId, entity: URL) { counter.increment(.entityProcessed) }
    func metadataApplied(operation: OperationId, entity: URL) { counter.increment(.metadataApplied) }
    func failureEncountered(operation: OperationId, failure: any Error) { counter.increment(.failureEncountered) }
    func failureEncountered(operation: OperationId, entity: URL, failure: any Error) {
        counter.increment(.failureEncountered)
    }
    func completed(operation: OperationId) { counter.increment(.completed) }
    func stateOf(operation: OperationId) async -> RecoveryState? { nil }
}
