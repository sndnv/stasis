import Foundation

public actor DefaultOperationExecutor: OperationExecutor {
    public struct Config: Sendable, Equatable, Hashable {
        public let backup: BackupConfig

        public init(backup: BackupConfig) {
            self.backup = backup
        }

        public struct BackupConfig: Sendable, Equatable, Hashable {
            public let limits: Backup.Descriptor.Limits

            public init(limits: Backup.Descriptor.Limits) {
                self.limits = limits
            }
        }
    }

    private let config: Config
    private let deviceSecret: @Sendable () -> DeviceSecret
    private let backupProviders: BackupProviders
    private let recoveryProviders: RecoveryProviders
    private let restrictionsFor: @Sendable (OperationType) -> [OperationRestriction]

    private var activeOperations: [OperationId: any Operation] = [:]
    private var completeOperations: [OperationId: OperationType] = [:]

    public init(
        config: Config,
        deviceSecret: @escaping @Sendable () -> DeviceSecret,
        backupProviders: BackupProviders,
        recoveryProviders: RecoveryProviders,
        restrictions: @escaping @Sendable (OperationType) -> [OperationRestriction]
    ) {
        self.config = config
        self.deviceSecret = deviceSecret
        self.backupProviders = backupProviders
        self.recoveryProviders = recoveryProviders
        self.restrictionsFor = restrictions
    }

    public func active() async -> [OperationId: OperationType] {
        activeOperations.mapValues { $0.type }
    }

    public func completed() async -> [OperationId: OperationType] {
        completeOperations
    }

    public func find(operation: OperationId) async -> OperationType? {
        activeOperations[operation]?.type ?? completeOperations[operation]
    }

    public func startBackupWithRules(
        definition: DatasetDefinitionId,
        rules: [Rule],
        callback: @escaping OperationCallback
    ) async -> OperationId {
        await startBackup(
            ofType: .backup,
            callback: callback,
            collector: .withRules(rules),
            definition: definition
        )
    }

    public func startBackupWithEntities(
        definition: DatasetDefinitionId,
        entities: [URL],
        callback: @escaping OperationCallback
    ) async -> OperationId {
        await startBackup(
            ofType: .backup,
            callback: callback,
            collector: .withEntities(entities),
            definition: definition
        )
    }

    public func resumeBackup(
        operation: OperationId,
        callback: @escaping OperationCallback
    ) async -> OperationId {
        if let existing = preflight(ofType: .backup, callback: callback) {
            return existing
        }

        let state = await backupProviders.track.stateOf(operation: operation)
        guard let state else {
            callback(OperationExecutorError.cannotResumeMissing(operation: operation))
            return operation
        }
        guard state.completed == nil else {
            callback(OperationExecutorError.cannotResumeCompleted(operation: operation))
            return operation
        }

        do {
            let descriptor = try await Backup.Descriptor.build(
                definition: state.definition,
                collector: .withState(state),
                deviceSecret: deviceSecret(),
                limits: config.backup.limits,
                providers: backupProviders
            )
            let op = Backup(descriptor: descriptor, providers: backupProviders)
            register(operation: op, callback: callback)
            return op.id
        } catch {
            callback(error)
            return operation
        }
    }

    public func startRecoveryWithDefinition(
        definition: DatasetDefinitionId,
        until: Date?,
        query: Recovery.PathQuery?,
        destination: Recovery.Destination?,
        callback: @escaping OperationCallback
    ) async -> OperationId {
        await startRecovery(
            callback: callback,
            collector: .withDefinition(definition: definition, until: until),
            query: query,
            destination: destination
        )
    }

    public func startRecoveryWithEntry(
        entry: DatasetEntryId,
        query: Recovery.PathQuery?,
        destination: Recovery.Destination?,
        callback: @escaping OperationCallback
    ) async -> OperationId {
        await startRecovery(
            callback: callback,
            collector: .withEntry(entry: entry),
            query: query,
            destination: destination
        )
    }

    public func startExpiration(callback: @escaping OperationCallback) async throws -> OperationId {
        throw OperationExecutorError.notImplemented("Expiration is not supported")
    }

    public func startValidation(callback: @escaping OperationCallback) async throws -> OperationId {
        throw OperationExecutorError.notImplemented("Validation is not supported")
    }

    public func startKeyRotation(callback: @escaping OperationCallback) async throws -> OperationId {
        throw OperationExecutorError.notImplemented("Key rotation is not supported")
    }

    public func stop(operation: OperationId) async throws {
        guard let active = activeOperations[operation] else {
            throw OperationExecutorError.operationNotFound(operation)
        }
        active.stop()
    }

    private func startBackup(
        ofType: OperationType,
        callback: @escaping OperationCallback,
        collector: Backup.Descriptor.Collector,
        definition: DatasetDefinitionId
    ) async -> OperationId {
        if let existing = preflight(ofType: ofType, callback: callback) {
            return existing
        }

        do {
            let descriptor = try await Backup.Descriptor.build(
                definition: definition,
                collector: collector,
                deviceSecret: deviceSecret(),
                limits: config.backup.limits,
                providers: backupProviders
            )
            let op = Backup(descriptor: descriptor, providers: backupProviders)
            register(operation: op, callback: callback)
            return op.id
        } catch {
            callback(error)
            return Operations.generateId()
        }
    }

    private func startRecovery(
        callback: @escaping OperationCallback,
        collector: Recovery.Descriptor.Collector,
        query: Recovery.PathQuery?,
        destination: Recovery.Destination?
    ) async -> OperationId {
        if let existing = preflight(ofType: .recovery, callback: callback) {
            return existing
        }

        do {
            let descriptor = try await Recovery.Descriptor.build(
                query: query,
                destination: destination,
                collector: collector,
                deviceSecret: deviceSecret(),
                providers: recoveryProviders
            )
            let op = Recovery(descriptor: descriptor, providers: recoveryProviders)
            register(operation: op, callback: callback)
            return op.id
        } catch {
            callback(error)
            return Operations.generateId()
        }
    }

    private func preflight(
        ofType: OperationType,
        callback: @escaping OperationCallback
    ) -> OperationId? {
        let restrictions = restrictionsFor(ofType)
        guard restrictions.isEmpty else {
            callback(OperationRestrictedFailure(restrictions: restrictions))
            return Operations.generateId()
        }
        if let existing = activeOperations.first(where: { $0.value.type == ofType })?.key {
            callback(OperationExecutorError.operationAlreadyActive(type: ofType, existing: existing))
            return existing
        }
        return nil
    }

    private func register(operation: any Operation, callback: @escaping OperationCallback) {
        activeOperations[operation.id] = operation
        let id = operation.id
        let type = operation.type
        Task { [weak self] in
            var resultError: Error?
            do {
                try await operation.start()
            } catch {
                resultError = error
            }
            await self?.markCompleted(id: id, type: type)
            callback(resultError)
        }
    }

    private func markCompleted(id: OperationId, type: OperationType) {
        activeOperations.removeValue(forKey: id)
        completeOperations[id] = type
    }
}
