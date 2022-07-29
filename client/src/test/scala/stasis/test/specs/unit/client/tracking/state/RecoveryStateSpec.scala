package stasis.test.specs.unit.client.tracking.state

import stasis.client.model.{proto, TargetEntity}
import stasis.client.tracking.state.RecoveryState
import stasis.client.tracking.state.RecoveryState.{PendingTargetEntity, ProcessedTargetEntity}
import stasis.shared.ops.Operation
import stasis.test.specs.unit.UnitSpec
import stasis.test.specs.unit.client.Fixtures

import scala.util.{Failure, Success}

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

  it should "be serializable to protobuf data" in {
    RecoveryState.toProto(Fixtures.State.RecoveryOneState) should be(Fixtures.Proto.State.RecoveryOneStateProto)
    RecoveryState.toProto(Fixtures.State.RecoveryTwoState) should be(Fixtures.Proto.State.RecoveryTwoStateProto)
  }

  it should "be deserializable from valid protobuf data" in {
    RecoveryState.fromProto(
      operation = Fixtures.State.RecoveryOneState.operation,
      state = Fixtures.Proto.State.RecoveryOneStateProto
    ) should be(
      Success(Fixtures.State.RecoveryOneState)
    )

    RecoveryState.fromProto(
      operation = Fixtures.State.RecoveryTwoState.operation,
      state = Fixtures.Proto.State.RecoveryTwoStateProto
    ) should be(
      Success(Fixtures.State.RecoveryTwoState)
    )
  }

  it should "fail to be deserialized when no entities are provided" in {
    RecoveryState.fromProto(
      operation = Fixtures.State.RecoveryOneState.operation,
      state = Fixtures.Proto.State.RecoveryOneStateProto.copy(entities = None)
    ) match {
      case Success(state) =>
        fail(s"Unexpected successful result received: [$state]")

      case Failure(e) =>
        e shouldBe an[IllegalArgumentException]
        e.getMessage should be("Expected entities in recovery state but none were found")
    }
  }

  it should "fail to be deserialized when target entity metadata is not provided" in {
    RecoveryState.fromProto(
      operation = Fixtures.State.RecoveryOneState.operation,
      state = Fixtures.Proto.State.RecoveryOneStateProto.copy(
        entities = Fixtures.Proto.State.RecoveryOneStateProto.entities.map { entities =>
          entities.copy(
            collected = entities.collected.map { case (k, v) =>
              k -> v.copy(existingMetadata = None)
            }
          )
        }
      )
    ) match {
      case Success(state) =>
        fail(s"Unexpected successful result received: [$state]")

      case Failure(e) =>
        e shouldBe an[IllegalArgumentException]
        e.getMessage should be("Expected existing metadata in recovery state but none was found")
    }

    RecoveryState.fromProto(
      operation = Fixtures.State.RecoveryOneState.operation,
      state = Fixtures.Proto.State.RecoveryOneStateProto.copy(
        entities = Fixtures.Proto.State.RecoveryOneStateProto.entities.map { entities =>
          entities.copy(
            collected = entities.collected.map { case (k, v) =>
              k -> v.copy(
                currentMetadata = v.currentMetadata.map(_.copy(entity = proto.metadata.EntityMetadata.Entity.Empty))
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
