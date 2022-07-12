package stasis.test.specs.unit.client.tracking.state

import stasis.client.model.TargetEntity
import stasis.client.tracking.state.RecoveryState
import stasis.client.tracking.state.RecoveryState.{PendingTargetEntity, ProcessedTargetEntity}
import stasis.shared.ops.Operation
import stasis.test.specs.unit.UnitSpec
import stasis.test.specs.unit.client.Fixtures

class RecoveryStateSpec extends UnitSpec {
  "A RecoveryState" should "provide its type and state" in {
    val backup = RecoveryState.start(operation = Operation.generateId())

    backup.`type` should be(Operation.Type.Recovery)
    backup.isCompleted should be(false)

    backup.recoveryCompleted().isCompleted should be(true)
  }

  it should "support collecting operation progress information" in {
    val recovery = RecoveryState.start(
      operation = Operation.generateId()
    )

    recovery.entities should be(RecoveryState.Entities.empty)
    recovery.failures should be(empty)
    recovery.completed should be(empty)

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
      .entityFailed(entity = entity3, new RuntimeException("Test failure #1"))
      .failureEncountered(new RuntimeException("Test failure #2"))
      .recoveryCompleted()

    completed.entities should be(
      RecoveryState.Entities(
        examined = Set(entity1, entity2, entity3),
        collected = Map(
          entity1 -> targetEntity1,
          entity3 -> targetEntity3
        ),
        pending = Map(
          entity3 -> PendingTargetEntity(expectedParts = 3, processedParts = 1)
        ),
        processed = Map(entity1 -> ProcessedTargetEntity(expectedParts = 1, processedParts = 1)),
        metadataApplied = Set(entity1),
        failed = Map(
          entity3 -> "RuntimeException - Test failure #1"
        )
      )
    )

    completed.failures should be(Seq("RuntimeException - Test failure #2"))
    completed.completed should not be empty
  }

  it should "support extracting a pending recovery's progress" in {
    val recovery = RecoveryState
      .start(operation = Operation.generateId())
      .entityExamined(entity = entity1)
      .entityCollected(targetEntity1)
      .entityCollected(targetEntity3)
      .entityProcessingStarted(entity = entity1, expectedParts = 1)
      .entityPartProcessed(entity = entity1)
      .entityProcessed(entity = entity2)
      .entityMetadataApplied(entity = entity3)
      .entityFailed(entity = entity3, new RuntimeException("Test failure #1"))
      .failureEncountered(new RuntimeException("Test failure #2"))
      .recoveryCompleted()

    recovery.asProgress should be(
      Operation.Progress(
        total = 1,
        processed = 1,
        failures = 2,
        completed = recovery.completed
      )
    )
  }

  "A PendingTargetEntity" should "support incrementing its number of processed parts" in {
    val entity = PendingTargetEntity(expectedParts = 5, processedParts = 1)

    val expectedEntity = PendingTargetEntity(expectedParts = entity.expectedParts, processedParts = entity.processedParts + 2)

    val actualEntity = entity.inc().inc()

    actualEntity should be(expectedEntity)
  }

  private val entity1 = Fixtures.Metadata.FileOneMetadata.path
  private val entity2 = Fixtures.Metadata.FileTwoMetadata.path
  private val entity3 = Fixtures.Metadata.FileThreeMetadata.path

  private val targetEntity1 = TargetEntity(
    path = entity1,
    existingMetadata = Fixtures.Metadata.FileOneMetadata,
    currentMetadata = None,
    destination = TargetEntity.Destination.Default
  )

  private val targetEntity3 = TargetEntity(
    path = entity3,
    existingMetadata = Fixtures.Metadata.FileThreeMetadata,
    currentMetadata = None,
    destination = TargetEntity.Destination.Default
  )
}
