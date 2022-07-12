package stasis.test.specs.unit.client.tracking.state

import stasis.client.model.SourceEntity
import stasis.client.tracking.state.BackupState
import stasis.client.tracking.state.BackupState.{PendingSourceEntity, ProcessedSourceEntity}
import stasis.shared.ops.Operation
import stasis.test.specs.unit.UnitSpec
import stasis.test.specs.unit.client.Fixtures

class BackupStateSpec extends UnitSpec {
  "A BackupState" should "provide its type and state" in {
    val backup = BackupState.start(operation = Operation.generateId())

    backup.`type` should be(Operation.Type.Backup)
    backup.isCompleted should be(false)

    backup.backupCompleted().isCompleted should be(true)
  }

  it should "support collecting operation progress information" in {
    val backup = BackupState.start(operation = Operation.generateId())

    backup.entities should be(BackupState.Entities.empty)
    backup.metadataCollected should be(empty)
    backup.metadataPushed should be(empty)
    backup.failures should be(empty)
    backup.completed should be(empty)

    val completed = backup
      .entityDiscovered(entity = entity1)
      .entityDiscovered(entity = entity2)
      .entityDiscovered(entity = entity3)
      .specificationProcessed(unmatched = Seq("a", "b", "c"))
      .entityExamined(entity = entity1)
      .entityExamined(entity = entity2)
      .entityExamined(entity = entity3)
      .entityCollected(entity = sourceEntity1)
      .failureEncountered(new RuntimeException("Test failure #1"))
      .entityCollected(entity = sourceEntity3)
      .entityProcessingStarted(entity = entity1, expectedParts = 3)
      .entityPartProcessed(entity = entity1)
      .entityPartProcessed(entity = entity1)
      .entityProcessingStarted(entity = entity2, expectedParts = 1)
      .entityPartProcessed(entity = entity1)
      .entityProcessed(entity = entity1, metadata = Left(Fixtures.Metadata.FileOneMetadata))
      .entityFailed(entity = entity2, new RuntimeException("Test failure #2"))
      .backupMetadataCollected()
      .backupMetadataPushed()
      .backupCompleted()

    completed.operation should be(backup.operation)

    completed.entities should be(
      BackupState.Entities(
        discovered = Set(entity1, entity2, entity3),
        unmatched = Seq("a", "b", "c"),
        examined = Set(entity1, entity2, entity3),
        collected = Map(
          entity1 -> sourceEntity1,
          entity3 -> sourceEntity3
        ),
        pending = Map(
          entity2 -> PendingSourceEntity(expectedParts = 1, processedParts = 0)
        ),
        processed = Map(
          entity1 -> ProcessedSourceEntity(
            expectedParts = 3,
            processedParts = 3,
            metadata = Left(Fixtures.Metadata.FileOneMetadata)
          )
        ),
        failed = Map(
          entity2 -> "RuntimeException - Test failure #2"
        )
      )
    )

    completed.metadataCollected should not be empty
    completed.metadataPushed should not be empty
    completed.failures should be(Seq("RuntimeException - Test failure #1"))
    completed.completed should not be empty
  }

  it should "support providing entities that have not been processed" in {
    val backup = BackupState
      .start(operation = Operation.generateId())
      .entityCollected(entity = sourceEntity1)
      .entityCollected(entity = sourceEntity3)
      .entityProcessed(entity = entity1, metadata = Left(Fixtures.Metadata.FileOneMetadata))

    backup.remainingEntities() should be(Seq(sourceEntity3))

    backup.backupCompleted().remainingEntities() should be(Seq.empty)
  }

  it should "support extracting a pending backup's progress" in {
    val backup = BackupState
      .start(operation = Operation.generateId())
      .entityDiscovered(entity = entity1)
      .entityExamined(entity = entity2)
      .entityCollected(entity = sourceEntity1)
      .entityCollected(entity = sourceEntity3)
      .entityProcessingStarted(entity = entity1, expectedParts = 1)
      .entityPartProcessed(entity = entity1)
      .entityProcessed(entity = entity3, metadata = Left(Fixtures.Metadata.FileOneMetadata))
      .entityFailed(entity = entity2, new RuntimeException("Test failure #1"))
      .failureEncountered(new RuntimeException("Test failure #2"))
      .backupMetadataCollected()
      .backupMetadataPushed()
      .backupCompleted()

    backup.asProgress should be(
      Operation.Progress(
        total = 1,
        processed = 1,
        failures = 2,
        completed = backup.completed
      )
    )
  }

  "A PendingSourceEntity" should "support incrementing its number of processed parts" in {
    val entity = PendingSourceEntity(expectedParts = 5, processedParts = 1)

    val expectedEntity = PendingSourceEntity(expectedParts = entity.expectedParts, processedParts = entity.processedParts + 2)

    val actualEntity = entity.inc().inc()

    actualEntity should be(expectedEntity)
  }

  it should "support converting to a processed entity" in {
    val pending = PendingSourceEntity(expectedParts = 5, processedParts = 1)

    pending.toProcessed(withMetadata = Left(Fixtures.Metadata.FileOneMetadata)) should be(
      ProcessedSourceEntity(
        expectedParts = pending.expectedParts,
        processedParts = pending.processedParts,
        metadata = Left(Fixtures.Metadata.FileOneMetadata)
      )
    )
  }

  private val entity1 = Fixtures.Metadata.FileOneMetadata.path
  private val entity2 = Fixtures.Metadata.FileTwoMetadata.path
  private val entity3 = Fixtures.Metadata.FileThreeMetadata.path

  private val sourceEntity1 = SourceEntity(
    path = entity1,
    existingMetadata = None,
    currentMetadata = Fixtures.Metadata.FileOneMetadata
  )

  private val sourceEntity3 = SourceEntity(
    path = entity3,
    existingMetadata = None,
    currentMetadata = Fixtures.Metadata.FileThreeMetadata
  )
}
