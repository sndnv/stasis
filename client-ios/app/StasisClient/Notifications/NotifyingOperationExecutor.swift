import Foundation
import StasisClientLib

struct NotifyingOperationExecutor: OperationExecutor {
    let underlying: any OperationExecutor
    let notifications: any SchedulingNotifications

    func active() async -> [OperationId: OperationType] {
        await underlying.active()
    }

    func completed() async -> [OperationId: OperationType] {
        await underlying.completed()
    }

    func find(operation: OperationId) async -> OperationType? {
        await underlying.find(operation: operation)
    }

    func startBackupWithRules(
        definition: DatasetDefinitionId,
        rules: [Rule],
        callback: @escaping OperationCallback
    ) async -> OperationId {
        let notifying = await notifying(operation: .backup, callback: callback)
        return await underlying.startBackupWithRules(definition: definition, rules: rules, callback: notifying)
    }

    func startBackupWithEntities(
        definition: DatasetDefinitionId,
        entities: [URL],
        callback: @escaping OperationCallback
    ) async -> OperationId {
        let notifying = await notifying(operation: .backup, callback: callback)
        return await underlying.startBackupWithEntities(definition: definition, entities: entities, callback: notifying)
    }

    func resumeBackup(
        operation: OperationId,
        callback: @escaping OperationCallback
    ) async -> OperationId {
        let notifying = await notifying(operation: .backup, callback: callback)
        return await underlying.resumeBackup(operation: operation, callback: notifying)
    }

    func startRecoveryWithDefinition(
        definition: DatasetDefinitionId,
        until: Date?,
        entities: Set<String>?,
        sources: Set<RecoverySourceKind>,
        destination: Recovery.Destination?,
        callback: @escaping OperationCallback
    ) async -> OperationId {
        let notifying = await notifying(operation: .recovery, callback: callback)
        return await underlying.startRecoveryWithDefinition(
            definition: definition, until: until,
            entities: entities, sources: sources,
            destination: destination,
            callback: notifying
        )
    }

    func startRecoveryWithEntry(
        entry: DatasetEntryId,
        entities: Set<String>?,
        sources: Set<RecoverySourceKind>,
        destination: Recovery.Destination?,
        callback: @escaping OperationCallback
    ) async -> OperationId {
        let notifying = await notifying(operation: .recovery, callback: callback)
        return await underlying.startRecoveryWithEntry(
            entry: entry,
            entities: entities, sources: sources,
            destination: destination,
            callback: notifying
        )
    }

    func startExpiration(callback: @escaping OperationCallback) async throws -> OperationId {
        try await underlying.startExpiration(callback: callback)
    }

    func startValidation(callback: @escaping OperationCallback) async throws -> OperationId {
        try await underlying.startValidation(callback: callback)
    }

    func startKeyRotation(callback: @escaping OperationCallback) async throws -> OperationId {
        try await underlying.startKeyRotation(callback: callback)
    }

    func stop(operation: OperationId) async throws {
        try await underlying.stop(operation: operation)
    }

    private func notifying(
        operation: OperationType,
        callback: @escaping OperationCallback
    ) async -> OperationCallback {
        let id = UUID().uuidString
        let notifications = notifications
        await notifications.notifyOperationStarted(id: id, operation: operation)
        return { failure in
            callback(failure)
            Task { await notifications.notifyOperationCompleted(id: id, operation: operation, failure: failure) }
        }
    }
}
