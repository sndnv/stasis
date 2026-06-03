import Foundation

public typealias OperationCallback = @Sendable (Error?) -> Void

public protocol OperationExecutor: Sendable {
    func active() async -> [OperationId: OperationType]
    func completed() async -> [OperationId: OperationType]
    func find(operation: OperationId) async -> OperationType?

    func startBackupWithRules(
        definition: DatasetDefinitionId,
        rules: [Rule],
        callback: @escaping OperationCallback
    ) async -> OperationId

    func startBackupWithEntities(
        definition: DatasetDefinitionId,
        entities: [URL],
        callback: @escaping OperationCallback
    ) async -> OperationId

    func resumeBackup(
        operation: OperationId,
        callback: @escaping OperationCallback
    ) async -> OperationId

    func startRecoveryWithDefinition(
        definition: DatasetDefinitionId,
        until: Date?,
        query: Recovery.PathQuery?,
        destination: Recovery.Destination?,
        callback: @escaping OperationCallback
    ) async -> OperationId

    func startRecoveryWithEntry(
        entry: DatasetEntryId,
        query: Recovery.PathQuery?,
        destination: Recovery.Destination?,
        callback: @escaping OperationCallback
    ) async -> OperationId

    func startExpiration(callback: @escaping OperationCallback) async throws -> OperationId
    func startValidation(callback: @escaping OperationCallback) async throws -> OperationId
    func startKeyRotation(callback: @escaping OperationCallback) async throws -> OperationId

    func stop(operation: OperationId) async throws
}

public enum OperationExecutorError: Error, Equatable {
    case notImplemented(String)
    case operationNotFound(OperationId)
    case operationAlreadyActive(type: OperationType, existing: OperationId)
    case cannotResumeCompleted(operation: OperationId)
    case cannotResumeMissing(operation: OperationId)
}
