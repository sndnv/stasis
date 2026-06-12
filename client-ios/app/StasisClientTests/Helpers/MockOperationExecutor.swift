import Foundation
import StasisClientLib

final actor MockOperationExecutor: OperationExecutor {
    enum StartCall: Sendable, Equatable {
        case backupRules(definition: DatasetDefinitionId, rules: Int)
        case backupEntities(definition: DatasetDefinitionId, entities: [URL])
        case recoveryDefinition(definition: DatasetDefinitionId, until: Date?, hasQuery: Bool, hasDestination: Bool)
        case recoveryEntry(entry: DatasetEntryId, hasQuery: Bool, hasDestination: Bool)
        case expiration
        case validation
        case keyRotation
    }

    private(set) var calls: [StartCall] = []
    private(set) var captured: [OperationCallback] = []
    private(set) var stopCalls: [OperationId] = []
    private(set) var resumeCalls: [OperationId] = []
    var fireCallbacksImmediately: Bool = true
    var callbackError: (any Error)?
    var startExpirationError: (any Error)?
    var startValidationError: (any Error)?
    var startKeyRotationError: (any Error)?
    var stopError: (any Error)?
    var activeOverride: [OperationId: OperationType] = [:]
    var completedOverride: [OperationId: OperationType] = [:]

    func active() async -> [OperationId: OperationType] { activeOverride }
    func completed() async -> [OperationId: OperationType] { completedOverride }
    func find(operation: OperationId) async -> OperationType? { nil }

    func startBackupWithRules(
        definition: DatasetDefinitionId,
        rules: [Rule],
        callback: @escaping OperationCallback
    ) async -> OperationId {
        calls.append(.backupRules(definition: definition, rules: rules.count))
        deliver(callback: callback)
        return UUID()
    }

    func startBackupWithEntities(
        definition: DatasetDefinitionId,
        entities: [URL],
        callback: @escaping OperationCallback
    ) async -> OperationId {
        calls.append(.backupEntities(definition: definition, entities: entities))
        deliver(callback: callback)
        return UUID()
    }

    func resumeBackup(operation: OperationId, callback: @escaping OperationCallback) async -> OperationId {
        resumeCalls.append(operation)
        deliver(callback: callback)
        return operation
    }

    func startRecoveryWithDefinition(
        definition: DatasetDefinitionId,
        until: Date?,
        query: Recovery.PathQuery?,
        destination: Recovery.Destination?,
        callback: @escaping OperationCallback
    ) async -> OperationId {
        calls.append(.recoveryDefinition(
            definition: definition, until: until,
            hasQuery: query != nil, hasDestination: destination != nil
        ))
        deliver(callback: callback)
        return UUID()
    }

    func startRecoveryWithEntry(
        entry: DatasetEntryId,
        query: Recovery.PathQuery?,
        destination: Recovery.Destination?,
        callback: @escaping OperationCallback
    ) async -> OperationId {
        calls.append(.recoveryEntry(
            entry: entry, hasQuery: query != nil, hasDestination: destination != nil
        ))
        deliver(callback: callback)
        return UUID()
    }

    func startExpiration(callback: @escaping OperationCallback) async throws -> OperationId {
        if let error = startExpirationError { throw error }
        calls.append(.expiration)
        deliver(callback: callback)
        return UUID()
    }

    func startValidation(callback: @escaping OperationCallback) async throws -> OperationId {
        if let error = startValidationError { throw error }
        calls.append(.validation)
        deliver(callback: callback)
        return UUID()
    }

    func startKeyRotation(callback: @escaping OperationCallback) async throws -> OperationId {
        if let error = startKeyRotationError { throw error }
        calls.append(.keyRotation)
        deliver(callback: callback)
        return UUID()
    }

    func stop(operation: OperationId) async throws {
        if let error = stopError { throw error }
        stopCalls.append(operation)
    }

    func setCallbackError(_ error: (any Error)?) { callbackError = error }
    func setStartExpirationError(_ error: (any Error)?) { startExpirationError = error }
    func setStartValidationError(_ error: (any Error)?) { startValidationError = error }
    func setStartKeyRotationError(_ error: (any Error)?) { startKeyRotationError = error }
    func setStopError(_ error: (any Error)?) { stopError = error }
    func setActiveOverride(_ value: [OperationId: OperationType]) { activeOverride = value }
    func setCompletedOverride(_ value: [OperationId: OperationType]) { completedOverride = value }

    private func deliver(callback: @escaping OperationCallback) {
        if fireCallbacksImmediately {
            callback(callbackError)
        } else {
            captured.append(callback)
        }
    }
}
