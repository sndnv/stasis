package stasis.client_android.lib.tracking.state

import stasis.client_android.lib.model.TargetEntity
import stasis.client_android.lib.ops.Operation
import stasis.client_android.lib.ops.OperationId
import java.nio.file.Path
import java.time.Instant

data class RecoveryState(
    val operation: OperationId,
    val entities: Entities,
    val failures: List<String>,
    override val completed: Instant?
) : OperationState {
    override val type: Operation.Type
        get() = Operation.Type.Recovery

    fun entityExamined(entity: Path): RecoveryState =
        copy(entities = entities.copy(examined = entities.examined.plus(element = entity)))

    fun entityCollected(entity: TargetEntity): RecoveryState =
        copy(entities = entities.copy(collected = entities.collected + (entity.path to entity)))

    fun entityProcessingStarted(entity: Path, expectedParts: Int): RecoveryState =
        copy(
            entities = entities.copy(
                pending =
                entities.pending + (entity to PendingTargetEntity(
                    expectedParts = expectedParts,
                    processedParts = 0
                ))
            )
        )

    fun entityPartProcessed(entity: Path): RecoveryState =
        copy(entities = entities.copy(pending = entities.pending + (entity to entities.pending[entity]!!.inc())))

    fun entityProcessed(entity: Path): RecoveryState {
        val processed = when (val pending = entities.pending[entity]) {
            null -> ProcessedTargetEntity(
                expectedParts = 0,
                processedParts = 0
            )

            else -> ProcessedTargetEntity(
                expectedParts = pending.expectedParts,
                processedParts = pending.processedParts
            )
        }

        return copy(
            entities = entities.copy(
                pending = entities.pending.minus(key = entity),
                processed = entities.processed + (entity to processed)
            )
        )
    }

    fun entityMetadataApplied(entity: Path): RecoveryState =
        copy(entities = entities.copy(metadataApplied = entities.metadataApplied.plus(element = entity)))

    fun entityFailed(entity: Path, reason: Throwable): RecoveryState =
        copy(
            entities = entities.copy(failed = entities.failed + (entity to "${reason.javaClass.simpleName} - ${reason.message}"))
        )

    fun failureEncountered(failure: Throwable): RecoveryState =
        copy(failures = failures + "${failure.javaClass.simpleName} - ${failure.message}")

    fun recoveryCompleted(): RecoveryState =
        copy(completed = Instant.now())

    override fun asProgress(): Operation.Progress = Operation.Progress(
        total = entities.examined.size,
        processed = entities.processed.size,
        failures = entities.failed.size + failures.size,
        completed = completed
    )

    companion object {
        fun start(operation: OperationId): RecoveryState = RecoveryState(
            operation = operation,
            entities = Entities.empty(),
            failures = emptyList(),
            completed = null
        )
    }

    data class Entities(
        val examined: Set<Path>,
        val collected: Map<Path, TargetEntity>,
        val pending: Map<Path, PendingTargetEntity>,
        val processed: Map<Path, ProcessedTargetEntity>,
        val metadataApplied: Set<Path>,
        val failed: Map<Path, String>
    ) {
        companion object {
            fun empty(): Entities = Entities(
                examined = emptySet(),
                collected = emptyMap(),
                pending = emptyMap(),
                processed = emptyMap(),
                metadataApplied = emptySet(),
                failed = emptyMap()
            )
        }
    }

    data class PendingTargetEntity(
        val expectedParts: Int,
        val processedParts: Int
    ) {
        fun inc(): PendingTargetEntity =
            copy(processedParts = processedParts + 1)
    }

    data class ProcessedTargetEntity(
        val expectedParts: Int,
        val processedParts: Int
    )
}
