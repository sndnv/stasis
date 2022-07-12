package stasis.client_android.lib.tracking.state

import stasis.client_android.lib.model.EntityMetadata
import stasis.client_android.lib.model.SourceEntity
import stasis.client_android.lib.ops.Operation
import stasis.client_android.lib.ops.OperationId
import stasis.client_android.lib.utils.Either
import java.nio.file.Path
import java.time.Instant

data class BackupState(
    val operation: OperationId,
    val entities: Entities,
    val metadataCollected: Instant?,
    val metadataPushed: Instant?,
    val failures: List<String>,
    override val completed: Instant?
) : OperationState {
    override val type: Operation.Type
        get() = Operation.Type.Backup

    fun entityDiscovered(entity: Path): BackupState =
        copy(entities = entities.copy(discovered = entities.discovered.plus(element = entity)))

    fun specificationProcessed(unmatched: List<String>): BackupState =
        copy(entities = entities.copy(unmatched = unmatched))

    fun entityExamined(entity: Path): BackupState =
        copy(entities = entities.copy(examined = entities.examined.plus(element = entity)))

    fun entityCollected(entity: SourceEntity): BackupState =
        copy(entities = entities.copy(collected = entities.collected + (entity.path to entity)))

    fun entityProcessingStarted(entity: Path, expectedParts: Int): BackupState =
        copy(
            entities = entities.copy(
                pending = entities.pending + (entity to PendingSourceEntity(
                    expectedParts = expectedParts,
                    processedParts = 0
                ))
            )
        )

    fun entityPartProcessed(entity: Path): BackupState =
        copy(
            entities = entities.copy(
                pending = entities.pending + (entity to entities.pending[entity]!!.inc())
            )
        )

    fun entityProcessed(entity: Path, metadata: Either<EntityMetadata, EntityMetadata>): BackupState {
        val processed = when (val pending = entities.pending[entity]) {
            null -> ProcessedSourceEntity(
                expectedParts = 0,
                processedParts = 0,
                metadata = metadata
            )

            else -> ProcessedSourceEntity(
                expectedParts = pending.expectedParts,
                processedParts = pending.processedParts,
                metadata = metadata
            )
        }

        return copy(
            entities = entities.copy(
                pending = entities.pending.minus(key = entity),
                processed = entities.processed + (entity to processed)
            )
        )

    }

    fun entityFailed(entity: Path, reason: Throwable): BackupState =
        copy(
            entities = entities.copy(
                failed = entities.failed + (entity to "${reason.javaClass.simpleName} - ${reason.message}")
            )
        )

    fun backupMetadataCollected(): BackupState =
        copy(metadataCollected = Instant.now())

    fun backupMetadataPushed(): BackupState =
        copy(metadataPushed = Instant.now())

    fun failureEncountered(failure: Throwable): BackupState =
        copy(failures = failures + "${failure.javaClass.simpleName} - ${failure.message}")

    fun backupCompleted(): BackupState =
        copy(completed = Instant.now())

    fun remainingEntities(): List<SourceEntity> =
        when (completed) {
            null -> entities.collected.filterKeys { entity -> !entities.processed.contains(entity) }.values.toList()
            else -> emptyList()
        }

    override fun asProgress(): Operation.Progress = Operation.Progress(
        total = entities.discovered.size,
        processed = entities.processed.size,
        failures = entities.failed.size + failures.size,
        completed = completed
    )

    companion object {
        fun start(operation: OperationId): BackupState = BackupState(
            operation = operation,
            entities = Entities.empty(),
            metadataCollected = null,
            metadataPushed = null,
            failures = emptyList(),
            completed = null
        )
    }

    data class Entities(
        val discovered: Set<Path>,
        val unmatched: List<String>,
        val examined: Set<Path>,
        val collected: Map<Path, SourceEntity>,
        val pending: Map<Path, PendingSourceEntity>,
        val processed: Map<Path, ProcessedSourceEntity>,
        val failed: Map<Path, String>
    ) {
        companion object {
            fun empty(): Entities = Entities(
                discovered = emptySet(),
                unmatched = emptyList(),
                examined = emptySet(),
                collected = emptyMap(),
                pending = emptyMap(),
                processed = emptyMap(),
                failed = emptyMap()
            )
        }
    }

    data class PendingSourceEntity(
        val expectedParts: Int,
        val processedParts: Int
    ) {
        fun inc(): PendingSourceEntity =
            copy(processedParts = processedParts + 1)

        fun toProcessed(withMetadata: Either<EntityMetadata, EntityMetadata>): ProcessedSourceEntity =
            ProcessedSourceEntity(
                expectedParts = expectedParts,
                processedParts = processedParts,
                metadata = withMetadata
            )
    }

    data class ProcessedSourceEntity(
        val expectedParts: Int,
        val processedParts: Int,
        val metadata: Either<EntityMetadata, EntityMetadata>
    )
}
