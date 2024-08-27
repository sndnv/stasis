package stasis.client_android.lib.tracking.state

import stasis.client_android.lib.model.EntityMetadata.Companion.toModel
import stasis.client_android.lib.model.EntityMetadata.Companion.toProto
import stasis.client_android.lib.model.TargetEntity
import stasis.client_android.lib.ops.Operation
import stasis.client_android.lib.ops.OperationId
import stasis.client_android.lib.utils.Try
import java.nio.file.Path
import java.nio.file.Paths
import java.time.Instant

data class RecoveryState(
    val operation: OperationId,
    override val started: Instant,
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
        started = started,
        total = entities.examined.size,
        processed = entities.processed.size,
        failures = entities.failed.size + failures.size,
        completed = completed
    )

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

    companion object {
        fun start(operation: OperationId): RecoveryState = RecoveryState(
            operation = operation,
            started = Instant.now(),
            entities = Entities.empty(),
            failures = emptyList(),
            completed = null
        )

        fun toProto(
            state: RecoveryState
        ): stasis.client_android.lib.model.proto.RecoveryState =
            stasis.client_android.lib.model.proto.RecoveryState(
                started = state.started.toEpochMilli(),
                entities =
                stasis.client_android.lib.model.proto.RecoveryEntities(
                    examined = state.entities.examined.map { it.toAbsolutePath().toString() }.toList(),
                    collected = state.entities.collected.map { (k, v) ->
                        k.toAbsolutePath().toString() to toProtoTargetEntity(v)
                    }.toMap(),
                    pending = state.entities.pending.map { (k, v) ->
                        k.toAbsolutePath().toString() to toProtoPendingTargetEntity(v)
                    }.toMap(),
                    processed = state.entities.processed.map { (k, v) ->
                        k.toAbsolutePath().toString() to toProtoProcessedTargetEntity(v)
                    }.toMap(),
                    metadataApplied = state.entities.metadataApplied.map { it.toAbsolutePath().toString() }.toList(),
                    failed = state.entities.failed.map { (k, v) -> k.toAbsolutePath().toString() to v }.toMap()
                ),
                failures = state.failures,
                completed = state.completed?.toEpochMilli()
            )

        fun fromProto(
            operation: OperationId, state: stasis.client_android.lib.model.proto.RecoveryState
        ): Try<RecoveryState> =
            when (val entities = state.entities) {
                null -> Try.Failure(IllegalArgumentException("Expected entities in recovery state but none were found"))
                else -> Try {
                    RecoveryState(
                        operation = operation,
                        started = Instant.ofEpochMilli(state.started),
                        entities = Entities(
                            examined = entities.examined.map { Paths.get(it) }.toSet(),
                            collected = entities.collected.map { (k, v) ->
                                Paths.get(k) to fromProtoTargetEntity(v)
                            }.toMap(),
                            pending = entities.pending.map { (k, v) ->
                                Paths.get(k) to fromProtoPendingTargetEntity(v)
                            }.toMap(),
                            processed = entities.processed.map { (k, v) ->
                                Paths.get(k) to fromProtoProcessedTargetEntity(v)
                            }.toMap(),
                            metadataApplied = entities.metadataApplied.map { Paths.get(it) }.toSet(),
                            failed = entities.failed.map { (k, v) -> Paths.get(k) to v }.toMap()
                        ),
                        failures = state.failures,
                        completed = state.completed?.let { Instant.ofEpochMilli(it) }
                    )
                }
            }

        private fun fromProtoTargetEntity(entity: stasis.client_android.lib.model.proto.TargetEntity): TargetEntity =
            TargetEntity(
                path = Paths.get(entity.path),
                destination = when (val directory = entity.destination?.directory) {
                    null -> TargetEntity.Destination.Default
                    else -> TargetEntity.Destination.Directory(
                        path = Paths.get(directory.path),
                        keepDefaultStructure = directory.keepDefaultStructure
                    )
                },
                existingMetadata = when (val metadata = entity.existingMetadata?.toModel()?.toOption()) {
                    null -> throw IllegalArgumentException("Expected existing metadata in recovery state but none was found")
                    else -> metadata
                },
                currentMetadata = entity.currentMetadata?.let { metadata ->
                    when (val result = metadata.toModel()) {
                        is Try.Success -> result.value
                        is Try.Failure -> throw result.exception
                    }
                }
            )

        private fun toProtoTargetEntity(entity: TargetEntity): stasis.client_android.lib.model.proto.TargetEntity =
            stasis.client_android.lib.model.proto.TargetEntity(
                path = entity.path.toAbsolutePath().toString(),
                destination = when (val destination = entity.destination) {
                    is TargetEntity.Destination.Directory ->
                        stasis.client_android.lib.model.proto.TargetEntityDestination(
                            directory = stasis.client_android.lib.model.proto.TargetEntityDestinationDirectory(
                                path = destination.path.toAbsolutePath().toString(),
                                keepDefaultStructure = destination.keepDefaultStructure
                            )
                        )

                    is TargetEntity.Destination.Default ->
                        stasis.client_android.lib.model.proto.TargetEntityDestination(
                            default = stasis.client_android.lib.model.proto.TargetEntityDestinationDefault()
                        )
                },
                existingMetadata = entity.existingMetadata.toProto(),
                currentMetadata = entity.currentMetadata?.toProto()
            )

        private fun fromProtoPendingTargetEntity(
            entity: stasis.client_android.lib.model.proto.PendingTargetEntity
        ): PendingTargetEntity =
            PendingTargetEntity(
                expectedParts = entity.expectedParts,
                processedParts = entity.processedParts
            )

        private fun toProtoPendingTargetEntity(
            entity: PendingTargetEntity
        ): stasis.client_android.lib.model.proto.PendingTargetEntity =
            stasis.client_android.lib.model.proto.PendingTargetEntity(
                expectedParts = entity.expectedParts,
                processedParts = entity.processedParts
            )

        private fun fromProtoProcessedTargetEntity(
            entity: stasis.client_android.lib.model.proto.ProcessedTargetEntity
        ): ProcessedTargetEntity =
            ProcessedTargetEntity(
                expectedParts = entity.expectedParts,
                processedParts = entity.processedParts
            )

        private fun toProtoProcessedTargetEntity(
            entity: ProcessedTargetEntity
        ): stasis.client_android.lib.model.proto.ProcessedTargetEntity =
            stasis.client_android.lib.model.proto.ProcessedTargetEntity(
                expectedParts = entity.expectedParts,
                processedParts = entity.processedParts
            )
    }
}
