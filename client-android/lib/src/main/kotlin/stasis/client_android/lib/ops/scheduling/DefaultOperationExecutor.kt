package stasis.client_android.lib.ops.scheduling

import stasis.client_android.lib.ops.backup.Backup as BackupOp
import stasis.client_android.lib.ops.backup.Providers as BackupProviders
import stasis.client_android.lib.ops.recovery.Providers as RecoveryProviders
import stasis.client_android.lib.ops.recovery.Recovery as RecoveryOp
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.withContext
import stasis.client_android.lib.collection.rules.Rule
import stasis.client_android.lib.encryption.secrets.DeviceSecret
import stasis.client_android.lib.model.server.datasets.DatasetDefinitionId
import stasis.client_android.lib.model.server.datasets.DatasetEntryId
import stasis.client_android.lib.ops.Operation
import stasis.client_android.lib.ops.OperationId
import stasis.client_android.lib.tracking.state.OperationState
import stasis.client_android.lib.utils.Try.Failure
import stasis.client_android.lib.utils.Try.Success
import java.nio.file.Path
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

class DefaultOperationExecutor(
    private val config: Config,
    private val deviceSecret: () -> DeviceSecret,
    private val backupProviders: BackupProviders,
    private val recoveryProviders: RecoveryProviders,
    operationDispatcher: CoroutineDispatcher
) : OperationExecutor {
    private val operationScope = CoroutineScope(operationDispatcher)

    private val activeOperations: ConcurrentHashMap<OperationId, Operation> =
        ConcurrentHashMap()

    private val completeOperations: ConcurrentHashMap<OperationId, Operation.Type> =
        ConcurrentHashMap()

    override suspend fun active(): Map<OperationId, Operation.Type> =
        activeOperations.mapValues { it.value.type() }

    override suspend fun completed(): Map<OperationId, Operation.Type> =
        completeOperations

    override suspend fun find(operation: OperationId): Operation.Type? =
        when (val active = activeOperations[operation]) {
            null -> completeOperations[operation]
            else -> active.type()
        }

    override suspend fun startBackupWithRules(
        definition: DatasetDefinitionId,
        rules: List<Rule>,
        f: (Throwable?) -> Unit
    ): OperationId = asUniqueOperation(ofType = Operation.Type.Backup, callback = f) {
        val descriptor = BackupOp.Descriptor(
            definition = definition,
            collector = BackupOp.Descriptor.Collector.WithRules(rules),
            deviceSecret = deviceSecret(),
            limits = config.backup.limits,
            providers = backupProviders
        )

        val operationId = when (descriptor) {
            is Success -> {
                val operation = BackupOp(descriptor.value, backupProviders)

                activeOperations[operation.id] = operation

                operation.start(withScope = operationScope) {
                    activeOperations.remove(operation.id)
                    completeOperations[operation.id] = operation.type()
                    f(it)
                }

                operation.id
            }

            is Failure -> {
                f(descriptor.exception)
                Operation.generateId()
            }
        }

        operationId
    }

    override suspend fun startBackupWithEntities(
        definition: DatasetDefinitionId,
        entities: List<Path>,
        f: (Throwable?) -> Unit
    ): OperationId = asUniqueOperation(ofType = Operation.Type.Backup, callback = f) {
        val descriptor = BackupOp.Descriptor(
            definition = definition,
            collector = BackupOp.Descriptor.Collector.WithEntities(entities = entities),
            deviceSecret = deviceSecret(),
            limits = config.backup.limits,
            providers = backupProviders
        )

        val operationId = when (descriptor) {
            is Success -> {
                val operation = BackupOp(descriptor.value, backupProviders)

                activeOperations[operation.id] = operation

                operation.start(withScope = operationScope) {
                    activeOperations.remove(operation.id)
                    completeOperations[operation.id] = operation.type()
                    f(it)
                }

                operation.id
            }

            is Failure -> {
                f(descriptor.exception)
                Operation.generateId()
            }
        }

        operationId
    }

    override suspend fun resumeBackup(
        operation: OperationId,
        f: (Throwable?) -> Unit
    ): OperationId = asUniqueOperation(ofType = Operation.Type.Backup, callback = f) {
        asValidOperationState(
            operation = operation,
            state = backupProviders.track.stateOf(operation),
            callback = f
        ) { state ->
            val descriptor = BackupOp.Descriptor(
                definition = state.definition,
                collector = BackupOp.Descriptor.Collector.WithState(state = state),
                deviceSecret = deviceSecret(),
                limits = config.backup.limits,
                providers = backupProviders
            )

            when (descriptor) {
                is Success -> {
                    val actualOperation = BackupOp(descriptor.value, backupProviders)

                    activeOperations[actualOperation.id] = actualOperation

                    actualOperation.start(withScope = operationScope) {
                        activeOperations.remove(actualOperation.id)
                        completeOperations[actualOperation.id] = actualOperation.type()
                        f(it)
                    }

                }

                is Failure -> {
                    f(descriptor.exception)
                }
            }

            operation
        }
    }

    override suspend fun startRecoveryWithDefinition(
        definition: DatasetDefinitionId,
        until: Instant?,
        query: RecoveryOp.PathQuery?,
        destination: RecoveryOp.Destination?,
        f: (Throwable?) -> Unit
    ): OperationId = asUniqueOperation(ofType = Operation.Type.Recovery, callback = f) {
        val descriptor = RecoveryOp.Descriptor(
            collector = RecoveryOp.Descriptor.Collector.WithDefinition(definition, until),
            query = query,
            destination = destination,
            deviceSecret = deviceSecret(),
            providers = recoveryProviders
        )

        val operationId = when (descriptor) {
            is Success -> {
                val operation = RecoveryOp(descriptor.value, recoveryProviders)

                activeOperations[operation.id] = operation

                operation.start(withScope = operationScope) {
                    activeOperations.remove(operation.id)
                    completeOperations[operation.id] = operation.type()
                    f(it)
                }

                operation.id
            }

            is Failure -> {
                f(descriptor.exception)
                Operation.generateId()
            }
        }

        operationId
    }

    override suspend fun startRecoveryWithEntry(
        entry: DatasetEntryId,
        query: RecoveryOp.PathQuery?,
        destination: RecoveryOp.Destination?,
        f: (Throwable?) -> Unit
    ): OperationId = asUniqueOperation(ofType = Operation.Type.Recovery, callback = f) {
        val descriptor = RecoveryOp.Descriptor(
            collector = RecoveryOp.Descriptor.Collector.WithEntry(entry),
            query = query,
            destination = destination,
            deviceSecret = deviceSecret(),
            providers = recoveryProviders
        )

        val operationId = when (descriptor) {
            is Success -> {
                val operation = RecoveryOp(descriptor.value, recoveryProviders)

                activeOperations[operation.id] = operation

                operation.start(withScope = operationScope) {
                    activeOperations.remove(operation.id)
                    completeOperations[operation.id] = operation.type()
                    f(it)
                }

                operation.id
            }

            is Failure -> {
                f(descriptor.exception)
                Operation.generateId()
            }
        }

        operationId
    }

    override suspend fun startExpiration(f: (Throwable?) -> Unit): OperationId {
        throw NotImplementedError("Expiration is not supported") // TODO - implement
    }

    override suspend fun startValidation(f: (Throwable?) -> Unit): OperationId {
        throw NotImplementedError("Validation is not supported") // TODO - implement
    }

    override suspend fun startKeyRotation(f: (Throwable?) -> Unit): OperationId {
        throw NotImplementedError("Key rotation is not supported") // TODO - implement
    }

    override suspend fun stop(operation: OperationId) {
        when (val activeOperation = activeOperations[operation]) {
            null -> throw IllegalArgumentException("Failed to stop [$operation]; operation not found")
            else -> activeOperation.stop()
        }
    }

    private suspend fun asUniqueOperation(
        ofType: Operation.Type,
        callback: (Throwable?) -> Unit,
        f: suspend () -> OperationId
    ): OperationId = withContext(operationScope.coroutineContext) {
        when (val operation = active().filterValues { it == ofType }.toList().firstOrNull()?.first) {
            null -> f() // operation doesn't exist; can proceed
            else -> {
                callback(
                    IllegalArgumentException(
                        "Cannot start [$ofType] operation; [$ofType] with ID [$operation] is already active"
                    )
                )

                operation
            }
        }
    }

    private suspend fun <T : OperationState> asValidOperationState(
        operation: OperationId,
        state: T?,
        callback: (Throwable?) -> Unit,
        f: suspend (T) -> OperationId
    ): OperationId = withContext(operationScope.coroutineContext) {
        when {
            state != null && state.completed == null -> f(state)
            state != null -> callback(
                IllegalArgumentException("Cannot resume operation with ID [$operation]; operation already completed")
            )
            else -> callback(
                IllegalArgumentException("Cannot resume operation with ID [$operation]; no existing state was found")
            )
        }

        operation
    }

    data class Config(
        val backup: Backup
    ) {
        data class Backup(
            val limits: BackupOp.Descriptor.Limits
        )
    }
}
