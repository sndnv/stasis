package stasis.client_android.lib.tracking.state

import stasis.client_android.lib.model.EntityMetadata
import stasis.client_android.lib.model.EntityMetadata.Companion.toModel
import stasis.client_android.lib.model.EntityMetadata.Companion.toProto
import stasis.client_android.lib.model.SourceEntity
import stasis.client_android.lib.model.server.datasets.DatasetDefinitionId
import stasis.client_android.lib.ops.Operation
import stasis.client_android.lib.ops.OperationId
import stasis.client_android.lib.utils.Either
import stasis.client_android.lib.utils.Try
import java.nio.file.Path
import java.nio.file.Paths
import java.time.Instant
import java.util.UUID

@Suppress("TooManyFunctions")
data class BackupState(
    val operation: OperationId,
    val definition: DatasetDefinitionId,
    val started: Instant,
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

    fun entitySkipped(entity: Path): BackupState =
        copy(entities = entities.copy(skipped = entities.skipped.plus(element = entity)))

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

    fun remainingEntities(): List<Path> =
        when (completed) {
            null -> entities.discovered.filterNot { entity -> entities.processed.contains(entity) }.toList()
            else -> emptyList()
        }

    fun asMetadataChanges(): Pair<Map<Path, EntityMetadata>, Map<Path, EntityMetadata>> {
        val contentChanged = mutableMapOf<Path, EntityMetadata>()
        val metadataChanged = mutableMapOf<Path, EntityMetadata>()

        entities.processed.forEach { (_, processed) ->
            when (processed.metadata) {
                is Either.Left -> contentChanged += (processed.metadata.value.path to processed.metadata.value)
                is Either.Right -> metadataChanged += (processed.metadata.value.path to processed.metadata.value)
            }
        }

        return contentChanged to metadataChanged
    }

    override fun asProgress(): Operation.Progress = Operation.Progress(
        total = entities.discovered.size,
        processed = entities.skipped.size + entities.processed.size,
        failures = entities.failed.size + failures.size,
        completed = completed
    )

    data class Entities(
        val discovered: Set<Path>,
        val unmatched: List<String>,
        val examined: Set<Path>,
        val skipped: Set<Path>,
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
                skipped = emptySet(),
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

    companion object {
        fun start(operation: OperationId, definition: DatasetDefinitionId): BackupState = BackupState(
            operation = operation,
            definition = definition,
            started = Instant.now(),
            entities = Entities.empty(),
            metadataCollected = null,
            metadataPushed = null,
            failures = emptyList(),
            completed = null
        )

        fun toProto(
            state: BackupState
        ): stasis.client_android.lib.model.proto.BackupState =
            stasis.client_android.lib.model.proto.BackupState(
                started = state.started.toEpochMilli(),
                definition = state.definition.toString(),
                entities =
                stasis.client_android.lib.model.proto.BackupEntities(
                    discovered = state.entities.discovered.map { it.toAbsolutePath().toString() }.toList(),
                    unmatched = state.entities.unmatched,
                    examined = state.entities.examined.map { it.toAbsolutePath().toString() }.toList(),
                    skipped = state.entities.skipped.map { it.toAbsolutePath().toString() }.toList(),
                    collected = state.entities.collected.map { (k, v) ->
                        k.toAbsolutePath().toString() to toProtoSourceEntity(v)
                    }.toMap(),
                    pending = state.entities.pending.map { (k, v) ->
                        k.toAbsolutePath().toString() to toProtoPendingSourceEntity(v)
                    }.toMap(),
                    processed = state.entities.processed.map { (k, v) ->
                        k.toAbsolutePath().toString() to toProtoProcessedSourceEntity(v)
                    }.toMap(),
                    failed = state.entities.failed.map { (k, v) -> k.toAbsolutePath().toString() to v }.toMap()
                ),
                metadataCollected = state.metadataCollected?.toEpochMilli(),
                metadataPushed = state.metadataPushed?.toEpochMilli(),
                failures = state.failures,
                completed = state.completed?.toEpochMilli()
            )

        fun fromProto(
            operation: OperationId,
            state: stasis.client_android.lib.model.proto.BackupState
        ): Try<BackupState> =
            when (val entities = state.entities) {
                null -> Try.Failure(IllegalArgumentException("Expected entities in backup state but none were found"))
                else -> Try {
                    BackupState(
                        operation = operation,
                        definition = UUID.fromString(state.definition),
                        started = Instant.ofEpochMilli(state.started),
                        entities = Entities(
                            discovered = entities.discovered.map { Paths.get(it) }.toSet(),
                            unmatched = entities.unmatched,
                            examined = entities.examined.map { Paths.get(it) }.toSet(),
                            skipped = entities.skipped.map { Paths.get(it) }.toSet(),
                            collected = entities.collected.map { (k, v) ->
                                Paths.get(k) to fromProtoSourceEntity(v)
                            }.toMap(),
                            pending = entities.pending.map { (k, v) ->
                                Paths.get(k) to fromProtoPendingSourceEntity(v)
                            }.toMap(),
                            processed = entities.processed.map { (k, v) ->
                                Paths.get(k) to fromProtoProcessedSourceEntity(v)
                            }.toMap(),
                            failed = entities.failed.map { (k, v) -> Paths.get(k) to v }.toMap()
                        ),
                        metadataCollected = state.metadataCollected?.let { Instant.ofEpochMilli(it) },
                        metadataPushed = state.metadataPushed?.let { Instant.ofEpochMilli(it) },
                        failures = state.failures,
                        completed = state.completed?.let { Instant.ofEpochMilli(it) }
                    )
                }
            }

        private fun fromProtoSourceEntity(
            entity: stasis.client_android.lib.model.proto.SourceEntity
        ): SourceEntity =
            SourceEntity(
                path = Paths.get(entity.path),
                existingMetadata = entity.existingMetadata?.let { metadata ->
                    when (val result = metadata.toModel()) {
                        is Try.Success -> result.value
                        is Try.Failure -> throw result.exception
                    }
                },
                currentMetadata = when (val metadata = entity.currentMetadata?.toModel()?.toOption()) {
                    null -> throw IllegalArgumentException("Expected current metadata in backup state but none was found")
                    else -> metadata
                }
            )

        private fun toProtoSourceEntity(
            entity: SourceEntity
        ): stasis.client_android.lib.model.proto.SourceEntity =
            stasis.client_android.lib.model.proto.SourceEntity(
                path = entity.path.toAbsolutePath().toString(),
                existingMetadata = entity.existingMetadata?.toProto(),
                currentMetadata = entity.currentMetadata.toProto()
            )

        private fun fromProtoPendingSourceEntity(
            entity: stasis.client_android.lib.model.proto.PendingSourceEntity
        ): PendingSourceEntity =
            PendingSourceEntity(
                expectedParts = entity.expectedParts,
                processedParts = entity.processedParts
            )

        private fun toProtoPendingSourceEntity(
            entity: PendingSourceEntity
        ): stasis.client_android.lib.model.proto.PendingSourceEntity =
            stasis.client_android.lib.model.proto.PendingSourceEntity(
                expectedParts = entity.expectedParts,
                processedParts = entity.processedParts
            )

        private fun fromProtoProcessedSourceEntity(
            entity: stasis.client_android.lib.model.proto.ProcessedSourceEntity
        ): ProcessedSourceEntity =
            ProcessedSourceEntity(
                expectedParts = entity.expectedParts,
                processedParts = entity.processedParts,
                metadata = entity.left?.let {
                    Either.Left(it.toModel().get())
                } ?: entity.right?.let {
                    Either.Right(it.toModel().get())
                } ?: throw IllegalArgumentException("Expected entity metadata in backup state but none was found")
            )

        private fun toProtoProcessedSourceEntity(
            entity: ProcessedSourceEntity
        ): stasis.client_android.lib.model.proto.ProcessedSourceEntity =
            stasis.client_android.lib.model.proto.ProcessedSourceEntity(
                expectedParts = entity.expectedParts,
                processedParts = entity.processedParts,
                left = entity.metadata.leftOpt?.toProto(),
                right = entity.metadata.rightOpt?.toProto()
            )
    }
}
