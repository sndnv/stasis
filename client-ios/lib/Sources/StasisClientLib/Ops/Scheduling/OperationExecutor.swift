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
        entities: Set<String>?,
        sources: Set<RecoverySourceKind>,
        destination: Recovery.Destination?,
        callback: @escaping OperationCallback
    ) async -> OperationId

    func startRecoveryWithEntry(
        entry: DatasetEntryId,
        entities: Set<String>?,
        sources: Set<RecoverySourceKind>,
        destination: Recovery.Destination?,
        callback: @escaping OperationCallback
    ) async -> OperationId

    func startExpiration(callback: @escaping OperationCallback) async throws -> OperationId
    func startValidation(callback: @escaping OperationCallback) async throws -> OperationId
    func startKeyRotation(callback: @escaping OperationCallback) async throws -> OperationId

    func stop(operation: OperationId) async throws
}

public enum OperationExecutorError: Error, Equatable, LocalizedError {
    case notImplemented(String)
    case operationNotFound(OperationId)
    case operationAlreadyActive(type: OperationType, existing: OperationId)
    case cannotResumeCompleted(operation: OperationId)
    case cannotResumeMissing(operation: OperationId)

    public var errorDescription: String? {
        switch self {
        case .notImplemented(let what):
            "[\(what)] is not implemented"
        case .operationNotFound(let operation):
            "Operation [\(operation.uuidString)] not found"
        case .operationAlreadyActive(let type, let existing):
            "Cannot start [\(type.rawValue)] operation; [\(type.rawValue)] with ID [\(existing.uuidString)] is already active"
        case .cannotResumeCompleted(let operation):
            "Cannot resume operation with ID [\(operation.uuidString)]; operation already completed"
        case .cannotResumeMissing(let operation):
            "Cannot resume operation with ID [\(operation.uuidString)]; no existing state was found"
        }
    }
}

public struct NoOpOperationExecutor: OperationExecutor {
    public init() {}

    public func active() async -> [OperationId: OperationType] { [:] }
    public func completed() async -> [OperationId: OperationType] { [:] }
    public func find(operation: OperationId) async -> OperationType? { nil }

    public func startBackupWithRules(
        definition: DatasetDefinitionId,
        rules: [Rule],
        callback: @escaping OperationCallback
    ) async -> OperationId {
        let id = UUID()
        callback(OperationExecutorError.notImplemented("no executor available"))
        return id
    }

    public func startBackupWithEntities(
        definition: DatasetDefinitionId,
        entities: [URL],
        callback: @escaping OperationCallback
    ) async -> OperationId {
        let id = UUID()
        callback(OperationExecutorError.notImplemented("no executor available"))
        return id
    }

    public func resumeBackup(
        operation: OperationId,
        callback: @escaping OperationCallback
    ) async -> OperationId {
        callback(OperationExecutorError.notImplemented("no executor available"))
        return operation
    }

    public func startRecoveryWithDefinition(
        definition: DatasetDefinitionId,
        until: Date?,
        entities: Set<String>?,
        sources: Set<RecoverySourceKind>,
        destination: Recovery.Destination?,
        callback: @escaping OperationCallback
    ) async -> OperationId {
        let id = UUID()
        callback(OperationExecutorError.notImplemented("no executor available"))
        return id
    }

    public func startRecoveryWithEntry(
        entry: DatasetEntryId,
        entities: Set<String>?,
        sources: Set<RecoverySourceKind>,
        destination: Recovery.Destination?,
        callback: @escaping OperationCallback
    ) async -> OperationId {
        let id = UUID()
        callback(OperationExecutorError.notImplemented("no executor available"))
        return id
    }

    public func startExpiration(callback: @escaping OperationCallback) async throws -> OperationId {
        throw OperationExecutorError.notImplemented("no executor available")
    }

    public func startValidation(callback: @escaping OperationCallback) async throws -> OperationId {
        throw OperationExecutorError.notImplemented("no executor available")
    }

    public func startKeyRotation(callback: @escaping OperationCallback) async throws -> OperationId {
        throw OperationExecutorError.notImplemented("no executor available")
    }

    public func stop(operation: OperationId) async throws {
        throw OperationExecutorError.operationNotFound(operation)
    }
}
