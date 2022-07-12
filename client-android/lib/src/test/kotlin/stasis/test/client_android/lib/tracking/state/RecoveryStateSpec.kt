package stasis.test.client_android.lib.tracking.state

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import stasis.client_android.lib.model.TargetEntity
import stasis.client_android.lib.ops.Operation
import stasis.client_android.lib.tracking.state.RecoveryState
import stasis.client_android.lib.tracking.state.RecoveryState.PendingTargetEntity
import stasis.test.client_android.lib.Fixtures

class RecoveryStateSpec : WordSpec({
    val entity1 = Fixtures.Metadata.FileOneMetadata.path
    val entity2 = Fixtures.Metadata.FileTwoMetadata.path
    val entity3 = Fixtures.Metadata.FileThreeMetadata.path

    val targetEntity1 = TargetEntity(
        path = entity1,
        existingMetadata = Fixtures.Metadata.FileOneMetadata,
        currentMetadata = null,
        destination = TargetEntity.Destination.Default
    )

    val targetEntity3 = TargetEntity(
        path = entity3,
        existingMetadata = Fixtures.Metadata.FileThreeMetadata,
        currentMetadata = null,
        destination = TargetEntity.Destination.Default
    )

    "A RecoveryState" should {
        "provide its type and state" {
            val backup = RecoveryState.start(operation = Operation.generateId())

            backup.type shouldBe (Operation.Type.Recovery)
            backup.completed shouldBe (null)

            backup.recoveryCompleted().completed shouldNotBe (null)
        }

        "support collecting operation progress information" {
            val recovery = RecoveryState.start(
                operation = Operation.generateId()
            )

            recovery.entities shouldBe (RecoveryState.Entities.empty())
            recovery.failures.size shouldBe (0)
            recovery.completed shouldBe (null)

            val completed = recovery
                .entityExamined(entity = entity1)
                .entityExamined(entity = entity2)
                .entityExamined(entity = entity3)
                .entityCollected(targetEntity1)
                .entityCollected(targetEntity3)
                .entityProcessingStarted(entity = entity1, expectedParts = 1)
                .entityPartProcessed(entity = entity1)
                .entityProcessingStarted(entity = entity3, expectedParts = 3)
                .entityPartProcessed(entity = entity3)
                .entityProcessed(entity = entity1)
                .entityMetadataApplied(entity = entity1)
                .entityFailed(entity = entity3, RuntimeException("Test failure #1"))
                .failureEncountered(RuntimeException("Test failure #2"))
                .recoveryCompleted()

            completed.entities shouldBe (
                    RecoveryState.Entities(
                        examined = setOf(entity1, entity2, entity3),
                        collected = mapOf(
                            entity1 to targetEntity1,
                            entity3 to targetEntity3
                        ),
                        pending = mapOf(
                            entity3 to PendingTargetEntity(expectedParts = 3, processedParts = 1)
                        ),
                        processed = mapOf(
                            entity1 to RecoveryState.ProcessedTargetEntity(
                                expectedParts = 1,
                                processedParts = 1
                            )
                        ),
                        metadataApplied = setOf(entity1),
                        failed = mapOf(
                            entity3 to "RuntimeException - Test failure #1"
                        )
                    )
                    )

            completed.failures shouldBe (listOf("RuntimeException - Test failure #2"))
            completed.completed shouldNotBe (null)
        }

        "support extracting a pending recovery's progress" {
            val recovery = RecoveryState
                .start(operation = Operation.generateId())
                .entityExamined(entity = entity1)
                .entityCollected(targetEntity1)
                .entityCollected(targetEntity3)
                .entityProcessingStarted(entity = entity1, expectedParts = 1)
                .entityPartProcessed(entity = entity1)
                .entityProcessed(entity = entity2)
                .entityMetadataApplied(entity = entity3)
                .entityFailed(entity = entity3, RuntimeException("Test failure #1"))
                .failureEncountered(RuntimeException("Test failure #2"))
                .recoveryCompleted()

            recovery.asProgress() shouldBe (
                    Operation.Progress(
                        total = 1,
                        processed = 1,
                        failures = 2,
                        completed = recovery.completed
                    )
                    )
        }
    }

    "A PendingTargetEntity" should {
        "support incrementing its number of processed parts" {
            val entity = PendingTargetEntity(expectedParts = 5, processedParts = 1)

            val expectedEntity = PendingTargetEntity(
                expectedParts = entity.expectedParts,
                processedParts = entity.processedParts + 2
            )

            val actualEntity = entity.inc().inc()

            actualEntity shouldBe (expectedEntity)
        }
    }
})
