package stasis.test.client_android.lib.tracking.state

import io.kotest.assertions.fail
import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.maps.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import stasis.client_android.lib.model.SourceEntity
import stasis.client_android.lib.ops.Operation
import stasis.client_android.lib.tracking.state.BackupState
import stasis.client_android.lib.utils.Either
import stasis.client_android.lib.utils.Try
import stasis.test.client_android.lib.Fixtures
import java.util.UUID

class BackupStateSpec : WordSpec({
    val entity1 = Fixtures.Metadata.FileOneMetadata.path
    val entity2 = Fixtures.Metadata.FileTwoMetadata.path
    val entity3 = Fixtures.Metadata.FileThreeMetadata.path

    val sourceEntity1 = SourceEntity(
        path = entity1,
        existingMetadata = null,
        currentMetadata = Fixtures.Metadata.FileOneMetadata
    )

    val sourceEntity3 = SourceEntity(
        path = entity3,
        existingMetadata = null,
        currentMetadata = Fixtures.Metadata.FileThreeMetadata
    )

    "A BackupState" should {
        "provide its type and state" {

            val backup = BackupState.start(operation = Operation.generateId(), definition = UUID.randomUUID())

            backup.type shouldBe (Operation.Type.Backup)
            backup.completed shouldBe (null)

            backup.backupCompleted().completed shouldNotBe (null)
        }

        "support collecting operation progress information" {
            val backup = BackupState.start(operation = Operation.generateId(), definition = UUID.randomUUID())

            backup.entities shouldBe (BackupState.Entities.empty())
            backup.metadataCollected shouldBe (null)
            backup.metadataPushed shouldBe (null)
            backup.failures.size shouldBe (0)
            backup.completed shouldBe (null)

            val completed = backup
                .entityDiscovered(entity = entity1)
                .entityDiscovered(entity = entity2)
                .entityDiscovered(entity = entity3)
                .specificationProcessed(unmatched = listOf("a", "b", "c"))
                .entityExamined(entity = entity1)
                .entityExamined(entity = entity2)
                .entityExamined(entity = entity3)
                .entityCollected(entity = sourceEntity1)
                .entitySkipped(entity = entity2)
                .failureEncountered(RuntimeException("Test failure #1"))
                .entityCollected(entity = sourceEntity3)
                .entityProcessingStarted(entity = entity1, expectedParts = 3)
                .entityPartProcessed(entity = entity1)
                .entityPartProcessed(entity = entity1)
                .entityProcessingStarted(entity = entity2, expectedParts = 1)
                .entityPartProcessed(entity = entity1)
                .entityProcessed(entity = entity1, metadata = Either.Left(Fixtures.Metadata.FileOneMetadata))
                .entityFailed(entity = entity2, RuntimeException("Test failure #2"))
                .backupMetadataCollected()
                .backupMetadataPushed()
                .backupCompleted()

            completed.operation shouldBe (backup.operation)

            completed.entities shouldBe (
                    BackupState.Entities(
                        discovered = setOf(entity1, entity2, entity3),
                        unmatched = listOf("a", "b", "c"),
                        examined = setOf(entity1, entity2, entity3),
                        skipped = setOf(entity2),
                        collected = mapOf(
                            entity1 to sourceEntity1,
                            entity3 to sourceEntity3
                        ),
                        pending = mapOf(
                            entity2 to BackupState.PendingSourceEntity(expectedParts = 1, processedParts = 0)
                        ),
                        processed = mapOf(
                            entity1 to BackupState.ProcessedSourceEntity(
                                expectedParts = 3,
                                processedParts = 3,
                                metadata = Either.Left(Fixtures.Metadata.FileOneMetadata)
                            )
                        ),
                        failed = mapOf(
                            entity2 to "RuntimeException - Test failure #2"
                        )
                    )
                    )

            completed.metadataCollected shouldNotBe (null)
            completed.metadataPushed shouldNotBe (null)
            completed.failures shouldBe (listOf("RuntimeException - Test failure #1"))
            completed.completed shouldNotBe (null)
        }

        "support providing entities that have not been processed" {
            val backup = BackupState
                .start(operation = Operation.generateId(), definition = UUID.randomUUID())
                .entityDiscovered(entity = sourceEntity1.path)
                .entityDiscovered(entity = sourceEntity3.path)
                .entityProcessed(entity = entity1, metadata = Either.Left(Fixtures.Metadata.FileOneMetadata))

            backup.remainingEntities() shouldBe (listOf(sourceEntity3.path))

            backup.backupCompleted().remainingEntities() shouldBe (emptyList())
        }

        "support providing entities as metadata changes" {
            val backup = BackupState
                .start(operation = Operation.generateId(), definition = UUID.randomUUID())
                .entityProcessed(
                    entity = Fixtures.Metadata.FileOneMetadata.path,
                    metadata = Either.Right(Fixtures.Metadata.FileOneMetadata) // metadata changed
                )
                .entityProcessed(
                    entity = Fixtures.Metadata.FileTwoMetadata.path,
                    metadata = Either.Left(Fixtures.Metadata.FileTwoMetadata) // content changed
                )
                .entityProcessed(
                    entity = Fixtures.Metadata.FileThreeMetadata.path,
                    metadata = Either.Right(Fixtures.Metadata.FileThreeMetadata) // metadata changed
                )

            val (contentChanged, metadataChanged) = backup.asMetadataChanges()

            contentChanged shouldContainExactly (mapOf(
                Fixtures.Metadata.FileTwoMetadata.path to Fixtures.Metadata.FileTwoMetadata
            ))

            metadataChanged shouldContainExactly (mapOf(
                Fixtures.Metadata.FileOneMetadata.path to Fixtures.Metadata.FileOneMetadata,
                Fixtures.Metadata.FileThreeMetadata.path to Fixtures.Metadata.FileThreeMetadata
            ))
        }

        "support extracting a pending backup's progress" {
            val backup = BackupState
                .start(operation = Operation.generateId(), definition = UUID.randomUUID())
                .entityDiscovered(entity = entity1)
                .entityDiscovered(entity = entity2)
                .entityDiscovered(entity = entity3)
                .entityExamined(entity = entity1)
                .entityExamined(entity = entity2)
                .entityExamined(entity = entity3)
                .entityCollected(entity = sourceEntity1)
                .entitySkipped(entity = entity2)
                .entityCollected(entity = sourceEntity3)
                .entityProcessingStarted(entity = entity1, expectedParts = 1)
                .entityPartProcessed(entity = entity1)
                .entityProcessed(entity = entity3, metadata = Either.Left(Fixtures.Metadata.FileOneMetadata))
                .entityFailed(entity = entity2, RuntimeException("Test failure #1"))
                .failureEncountered(RuntimeException("Test failure #2"))
                .backupMetadataCollected()
                .backupMetadataPushed()
                .backupCompleted()

            backup.asProgress() shouldBe (
                    Operation.Progress(
                        total = 3,
                        processed = 2,
                        failures = 2,
                        completed = backup.completed
                    )
                    )
        }

        "be serializable to protobuf data" {
            BackupState.fromProto(
                operation = Fixtures.State.BackupOneState.operation,
                state = Fixtures.Proto.State.BackupOneStateProto
            ) shouldBe (Try.Success(Fixtures.State.BackupOneState))
        }

        "fail to be deserialized when no entities are provided" {
            val result = BackupState.fromProto(
                operation = Fixtures.State.BackupOneState.operation,
                state = Fixtures.Proto.State.BackupOneStateProto.copy(entities = null)
            )

            when (result) {
                is Try.Success -> fail("Unexpected successful result received: [${result.value}]")
                is Try.Failure -> result.exception.message shouldBe (
                        "Expected entities in backup state but none were found"
                        )
            }
        }

        "fail to be deserialized when source entity metadata is not provided" {
            val result1 = BackupState.fromProto(
                operation = Fixtures.State.BackupOneState.operation,
                state = Fixtures.Proto.State.BackupOneStateProto.copy(
                    entities = Fixtures.Proto.State.BackupOneStateProto.entities?.let { entities ->
                        entities.copy(
                            collected = entities.collected.map { (k, v) ->
                                k to v.copy(
                                    existingMetadata = v.existingMetadata?.copy(file_ = null, directory = null)
                                )
                            }.toMap()
                        )
                    }
                )
            )

            when (result1) {
                is Try.Success -> fail("Unexpected successful result received: [${result1.value}]")
                is Try.Failure -> result1.exception.message shouldBe ("Expected entity in metadata but none was found")
            }

            val result2 = BackupState.fromProto(
                operation = Fixtures.State.BackupOneState.operation,
                state = Fixtures.Proto.State.BackupOneStateProto.copy(
                    entities = Fixtures.Proto.State.BackupOneStateProto.entities?.let { entities ->
                        entities.copy(
                            collected = entities.collected.map { (k, v) ->
                                k to v.copy(currentMetadata = null)
                            }.toMap()
                        )
                    }
                )
            )

            when (result2) {
                is Try.Success -> fail("Unexpected successful result received: [${result2.value}]")
                is Try.Failure -> result2.exception.message shouldBe (
                        "Expected current metadata in backup state but none was found"
                        )
            }
        }

        "fail to be deserialized when processed source entity metadata is not provided" {
            val result1 = BackupState.fromProto(
                operation = Fixtures.State.BackupOneState.operation,
                state = Fixtures.Proto.State.BackupOneStateProto.copy(
                    entities = Fixtures.Proto.State.BackupOneStateProto.entities?.let { entities ->
                        entities.copy(
                            processed = entities.processed.map { (k, v) ->
                                k to v.copy(
                                    left = Fixtures.Proto.Metadata.FileOneMetadataProto.copy(
                                        file_ = null,
                                        directory = null
                                    ),
                                    right = null
                                )
                            }.toMap()
                        )
                    }
                )
            )

            when (result1) {
                is Try.Success -> fail("Unexpected successful result received: [${result1.value}]")
                is Try.Failure -> result1.exception.message shouldBe ("Expected entity in metadata but none was found")
            }

            val result2 = BackupState.fromProto(
                operation = Fixtures.State.BackupOneState.operation,
                state = Fixtures.Proto.State.BackupOneStateProto.copy(
                    entities = Fixtures.Proto.State.BackupOneStateProto.entities?.let { entities ->
                        entities.copy(
                            processed = entities.processed.map { (k, v) ->
                                k to v.copy(
                                    right = Fixtures.Proto.Metadata.FileOneMetadataProto.copy(
                                        file_ = null, directory = null
                                    ),
                                    left = null
                                )
                            }.toMap()
                        )
                    }
                )
            )

            when (result2) {
                is Try.Success -> fail("Unexpected successful result received: [${result2.value}]")
                is Try.Failure -> result2.exception.message shouldBe ("Expected entity in metadata but none was found")
            }

            val result3 = BackupState.fromProto(
                operation = Fixtures.State.BackupOneState.operation,
                state = Fixtures.Proto.State.BackupOneStateProto.copy(
                    entities = Fixtures.Proto.State.BackupOneStateProto.entities?.let { entities ->
                        entities.copy(
                            processed = entities.processed.map { (k, v) ->
                                k to v.copy(left = null, right = null)
                            }.toMap()
                        )
                    }
                )
            )

            when (result3) {
                is Try.Success -> fail("Unexpected successful result received: [${result3.value}]")
                is Try.Failure -> result3.exception.message shouldBe (
                        "Expected entity metadata in backup state but none was found"
                        )
            }
        }
    }

    "A PendingSourceEntity" should {
        "support incrementing its number of processed parts" {
            val entity = BackupState.PendingSourceEntity(expectedParts = 5, processedParts = 1)

            val expectedEntity = BackupState.PendingSourceEntity(
                expectedParts = entity.expectedParts,
                processedParts = entity.processedParts + 2
            )

            val actualEntity = entity.inc().inc()

            actualEntity shouldBe (expectedEntity)
        }

        "support converting to a processed entity" {
            val pending = BackupState.PendingSourceEntity(expectedParts = 5, processedParts = 1)

            pending.toProcessed(withMetadata = Either.Left(Fixtures.Metadata.FileOneMetadata)) shouldBe (
                    BackupState.ProcessedSourceEntity(
                        expectedParts = pending.expectedParts,
                        processedParts = pending.processedParts,
                        metadata = Either.Left(Fixtures.Metadata.FileOneMetadata)
                    )
                    )
        }
    }
})
