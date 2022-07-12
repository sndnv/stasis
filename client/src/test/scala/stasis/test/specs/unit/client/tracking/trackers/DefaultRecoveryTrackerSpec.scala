package stasis.test.specs.unit.client.tracking.trackers

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorSystem, Behavior, SpawnProtocol}
import akka.stream.scaladsl.Sink
import akka.util.Timeout
import org.scalatest.{Assertion, BeforeAndAfterAll}
import org.scalatest.concurrent.Eventually
import stasis.client.model.TargetEntity
import stasis.client.tracking.state.RecoveryState
import stasis.client.tracking.state.RecoveryState.ProcessedTargetEntity
import stasis.client.tracking.trackers.DefaultRecoveryTracker
import stasis.core.persistence.backends.memory.EventLogMemoryBackend
import stasis.shared.ops.Operation
import stasis.test.specs.unit.AsyncUnitSpec
import stasis.test.specs.unit.client.Fixtures
import stasis.test.specs.unit.core.telemetry.MockTelemetryContext

import scala.concurrent.duration._

class DefaultRecoveryTrackerSpec extends AsyncUnitSpec with Eventually with BeforeAndAfterAll {
  "A DefaultRecoveryTracker" should "track recovery events" in {
    val tracker = createTracker()

    implicit val operation: Operation.Id = Operation.generateId()

    val entity = Fixtures.Metadata.FileOneMetadata.path

    val targetEntity = TargetEntity(
      path = entity,
      existingMetadata = Fixtures.Metadata.FileOneMetadata,
      currentMetadata = None,
      destination = TargetEntity.Destination.Default
    )

    val initialState = tracker.state.await
    initialState should be(empty)

    tracker.entityExamined(entity = entity, metadataChanged = false, contentChanged = false)
    tracker.entityCollected(entity = targetEntity)
    tracker.entityProcessingStarted(entity = entity, expectedParts = 1)
    tracker.entityPartProcessed(entity = entity)
    tracker.entityProcessed(entity = entity)
    tracker.metadataApplied(entity = entity)
    tracker.failureEncountered(new RuntimeException("Test failure #1"))
    tracker.failureEncountered(entity = Fixtures.Metadata.FileTwoMetadata.path, new RuntimeException("Test failure #2"))
    tracker.completed()

    eventually[Assertion] {
      val state = tracker.state.await.get(operation) match {
        case Some(value) => value
        case None        => fail(s"Expected state for operation [$operation] but none was found")
      }

      state.operation should be(operation)

      state.entities should be(
        RecoveryState.Entities(
          examined = Set(entity),
          collected = Map(entity -> targetEntity),
          pending = Map.empty,
          processed = Map(
            entity -> ProcessedTargetEntity(expectedParts = 1, processedParts = 1)
          ),
          metadataApplied = Set(entity),
          failed = Map(
            Fixtures.Metadata.FileTwoMetadata.path -> "RuntimeException - Test failure #2"
          )
        )
      )

      state.failures should be(Seq("RuntimeException - Test failure #1"))
      state.completed should not be empty
    }
  }

  it should "provide state updates" in {
    val tracker = createTracker()

    implicit val operation: Operation.Id = Operation.generateId()

    val entity = Fixtures.Metadata.FileOneMetadata.path

    val initialState = tracker.state.await
    initialState should be(empty)

    val expectedUpdates = 3
    val updates = tracker.updates(operation).take(expectedUpdates.toLong).runWith(Sink.seq)
    await(50.millis, withSystem = system)

    tracker.entityExamined(entity, metadataChanged = false, contentChanged = false)
    await(50.millis, withSystem = system)
    tracker.entityProcessed(entity)
    await(50.millis, withSystem = system)
    tracker.completed()

    updates.await.toList match {
      case first :: second :: third :: Nil =>
        first.entities.examined should be(Set(entity))
        first.entities.processed.keys.toSeq should be(empty)
        first.completed should be(empty)

        second.entities.examined should be(Set(entity))
        second.entities.processed.keys.toSeq should be(Seq(entity))
        second.completed should be(empty)

        third.entities.examined should be(Set(entity))
        third.entities.processed.keys.toSeq should be(Seq(entity))
        third.completed should not be empty

      case other =>
        fail(s"Unexpected result received: [$other]")
    }
  }

  private def createTracker(): DefaultRecoveryTracker = DefaultRecoveryTracker(
    createBackend = state =>
      EventLogMemoryBackend(
        name = s"test-recovery-tracker-${java.util.UUID.randomUUID()}",
        initialState = state
      )
  )

  override implicit val patienceConfig: PatienceConfig = PatienceConfig(7.seconds, 300.milliseconds)

  override implicit val timeout: Timeout = 7.seconds

  private implicit val system: ActorSystem[SpawnProtocol.Command] = ActorSystem(
    Behaviors.setup(_ => SpawnProtocol()): Behavior[SpawnProtocol.Command],
    "DefaultRecoveryTrackerSpec"
  )

  private implicit val telemetry: MockTelemetryContext = MockTelemetryContext()

  override protected def afterAll(): Unit =
    system.terminate()
}
