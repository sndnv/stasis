package stasis.test.client_android.lib.tracking.state

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import stasis.client_android.lib.model.SourceEntity
import stasis.client_android.lib.ops.Operation
import stasis.client_android.lib.tracking.state.BackupState
import stasis.client_android.lib.utils.Either
import stasis.test.client_android.lib.Fixtures

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

            val backup = BackupState.start(operation = Operation.generateId())

            backup.type shouldBe (Operation.Type.Backup)
            backup.completed shouldBe (null)

            backup.backupCompleted().completed shouldNotBe (null)
        }

        "support collecting operation progress information" {
            val backup = BackupState.start(operation = Operation.generateId())

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
                .start(operation = Operation.generateId())
                .entityCollected(entity = sourceEntity1)
                .entityCollected(entity = sourceEntity3)
                .entityProcessed(entity = entity1, metadata = Either.Left(Fixtures.Metadata.FileOneMetadata))

            backup.remainingEntities() shouldBe (listOf(sourceEntity3))

            backup.backupCompleted().remainingEntities() shouldBe (emptyList())
        }

        "support extracting a pending backup's progress" {
            val backup = BackupState
                .start(operation = Operation.generateId())
                .entityDiscovered(entity = entity1)
                .entityExamined(entity = entity2)
                .entityCollected(entity = sourceEntity1)
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
                        total = 1,
                        processed = 1,
                        failures = 2,
                        completed = backup.completed
                    )
                    )
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
