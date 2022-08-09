package stasis.test.specs.unit.client.tracking.state

import stasis.client.model.{proto, SourceEntity}
import stasis.client.tracking.state.BackupState
import stasis.client.tracking.state.BackupState.{PendingSourceEntity, ProcessedSourceEntity}
import stasis.shared.model.datasets.DatasetDefinition
import stasis.shared.ops.Operation
import stasis.test.specs.unit.UnitSpec
import stasis.test.specs.unit.client.Fixtures

import scala.util.{Failure, Success}

class BackupStateSpec extends UnitSpec {
  "A BackupState" should "provide its type and state" in {
    val backup = BackupState.start(
      operation = Operation.generateId(),
      definition = DatasetDefinition.generateId()
    )

    backup.`type` should be(Operation.Type.Backup)
    backup.isCompleted should be(false)

    backup.backupCompleted().isCompleted should be(true)
  }

  it should "support collecting operation progress information" in {
    val backup = BackupState.start(
      operation = Operation.generateId(),
      definition = DatasetDefinition.generateId()
    )

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
      .start(operation = Operation.generateId(), definition = DatasetDefinition.generateId())
      .entityDiscovered(entity = sourceEntity1.path)
      .entityDiscovered(entity = sourceEntity3.path)
      .entityProcessed(entity = entity1, metadata = Left(Fixtures.Metadata.FileOneMetadata))

    backup.remainingEntities() should be(Seq(sourceEntity3.path))

    backup.backupCompleted().remainingEntities() should be(Seq.empty)
  }

  it should "support providing entities as metadata changes" in {
    val backup = BackupState
      .start(operation = Operation.generateId(), definition = DatasetDefinition.generateId())
      .entityProcessed(
        entity = Fixtures.Metadata.FileOneMetadata.path,
        metadata = Right(Fixtures.Metadata.FileOneMetadata) // metadata changed
      )
      .entityProcessed(
        entity = Fixtures.Metadata.FileTwoMetadata.path,
        metadata = Left(Fixtures.Metadata.FileTwoMetadata) // content changed
      )
      .entityProcessed(
        entity = Fixtures.Metadata.FileThreeMetadata.path,
        metadata = Right(Fixtures.Metadata.FileThreeMetadata) // metadata changed
      )

    val (contentChanged, metadataChanged) = backup.asMetadataChanges

    contentChanged should be(
      Map(
        Fixtures.Metadata.FileTwoMetadata.path -> Fixtures.Metadata.FileTwoMetadata
      )
    )

    metadataChanged should be(
      Map(
        Fixtures.Metadata.FileOneMetadata.path -> Fixtures.Metadata.FileOneMetadata,
        Fixtures.Metadata.FileThreeMetadata.path -> Fixtures.Metadata.FileThreeMetadata
      )
    )
  }

  it should "support extracting a pending backup's progress" in {
    val backup = BackupState
      .start(operation = Operation.generateId(), definition = DatasetDefinition.generateId())
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

  it should "be serializable to protobuf data" in {
    BackupState.toProto(Fixtures.State.BackupOneState) should be(Fixtures.Proto.State.BackupOneStateProto)
  }

  it should "be deserializable from valid protobuf data" in {
    BackupState.fromProto(
      operation = Fixtures.State.BackupOneState.operation,
      state = Fixtures.Proto.State.BackupOneStateProto
    ) should be(
      Success(Fixtures.State.BackupOneState)
    )
  }

  it should "fail to be deserialized when no entities are provided" in {
    BackupState.fromProto(
      operation = Fixtures.State.BackupOneState.operation,
      state = Fixtures.Proto.State.BackupOneStateProto.copy(entities = None)
    ) match {
      case Success(state) =>
        fail(s"Unexpected successful result received: [$state]")

      case Failure(e) =>
        e shouldBe an[IllegalArgumentException]
        e.getMessage should be("Expected entities in backup state but none were found")
    }
  }

  it should "fail to be deserialized when source entity metadata is not provided" in {
    BackupState.fromProto(
      operation = Fixtures.State.BackupOneState.operation,
      state = Fixtures.Proto.State.BackupOneStateProto.copy(
        entities = Fixtures.Proto.State.BackupOneStateProto.entities.map { entities =>
          entities.copy(
            collected = entities.collected.map { case (k, v) =>
              k -> v.copy(
                existingMetadata = v.existingMetadata.map(_.copy(entity = proto.metadata.EntityMetadata.Entity.Empty))
              )
            }
          )
        }
      )
    ) match {
      case Success(state) =>
        fail(s"Unexpected successful result received: [$state]")

      case Failure(e) =>
        e shouldBe an[IllegalArgumentException]
        e.getMessage should be("Expected entity in metadata but none was found")
    }

    BackupState.fromProto(
      operation = Fixtures.State.BackupOneState.operation,
      state = Fixtures.Proto.State.BackupOneStateProto.copy(
        entities = Fixtures.Proto.State.BackupOneStateProto.entities.map { entities =>
          entities.copy(
            collected = entities.collected.map { case (k, v) =>
              k -> v.copy(currentMetadata = None)
            }
          )
        }
      )
    ) match {
      case Success(state) =>
        fail(s"Unexpected successful result received: [$state]")

      case Failure(e) =>
        e shouldBe an[IllegalArgumentException]
        e.getMessage should be("Expected current metadata in backup state but none was found")
    }
  }

  it should "fail to be deserialized when processed source entity metadata is not provided" in {
    BackupState.fromProto(
      operation = Fixtures.State.BackupOneState.operation,
      state = Fixtures.Proto.State.BackupOneStateProto.copy(
        entities = Fixtures.Proto.State.BackupOneStateProto.entities.map { entities =>
          entities.copy(
            processed = entities.processed.map { case (k, v) =>
              k -> v.copy(metadata =
                proto.state.ProcessedSourceEntity.Metadata.Left(
                  Fixtures.Proto.Metadata.FileOneMetadataProto.copy(
                    entity = proto.metadata.EntityMetadata.Entity.Empty
                  )
                )
              )
            }
          )
        }
      )
    ) match {
      case Success(state) =>
        fail(s"Unexpected successful result received: [$state]")

      case Failure(e) =>
        e shouldBe an[IllegalArgumentException]
        e.getMessage should be("Expected entity in metadata but none was found")
    }

    BackupState.fromProto(
      operation = Fixtures.State.BackupOneState.operation,
      state = Fixtures.Proto.State.BackupOneStateProto.copy(
        entities = Fixtures.Proto.State.BackupOneStateProto.entities.map { entities =>
          entities.copy(
            processed = entities.processed.map { case (k, v) =>
              k -> v.copy(metadata =
                proto.state.ProcessedSourceEntity.Metadata.Right(
                  Fixtures.Proto.Metadata.FileOneMetadataProto.copy(
                    entity = proto.metadata.EntityMetadata.Entity.Empty
                  )
                )
              )
            }
          )
        }
      )
    ) match {
      case Success(state) =>
        fail(s"Unexpected successful result received: [$state]")

      case Failure(e) =>
        e shouldBe an[IllegalArgumentException]
        e.getMessage should be("Expected entity in metadata but none was found")
    }

    BackupState.fromProto(
      operation = Fixtures.State.BackupOneState.operation,
      state = Fixtures.Proto.State.BackupOneStateProto.copy(
        entities = Fixtures.Proto.State.BackupOneStateProto.entities.map { entities =>
          entities.copy(
            processed = entities.processed.map { case (k, v) =>
              k -> v.copy(metadata = proto.state.ProcessedSourceEntity.Metadata.Empty)
            }
          )
        }
      )
    ) match {
      case Success(state) =>
        fail(s"Unexpected successful result received: [$state]")

      case Failure(e) =>
        e shouldBe an[IllegalArgumentException]
        e.getMessage should be("Expected entity metadata in backup state but none was found")
    }
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
