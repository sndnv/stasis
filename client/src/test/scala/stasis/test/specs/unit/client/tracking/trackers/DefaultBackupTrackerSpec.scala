package stasis.test.specs.unit.client.tracking.trackers

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorSystem, Behavior, SpawnProtocol}
import akka.stream.scaladsl.Sink
import akka.util.Timeout
import org.scalatest.concurrent.Eventually
import org.scalatest.{Assertion, BeforeAndAfterAll}
import stasis.client.collection.rules.Rule
import stasis.client.model.SourceEntity
import stasis.client.tracking.state.BackupState
import stasis.client.tracking.state.BackupState.ProcessedSourceEntity
import stasis.client.tracking.trackers.DefaultBackupTracker
import stasis.core.persistence.backends.memory.EventLogMemoryBackend
import stasis.shared.ops.Operation
import stasis.test.specs.unit.AsyncUnitSpec
import stasis.test.specs.unit.client.Fixtures
import stasis.test.specs.unit.core.telemetry.MockTelemetryContext

import scala.concurrent.duration._

class DefaultBackupTrackerSpec extends AsyncUnitSpec with Eventually with BeforeAndAfterAll {
  "A DefaultBackupTracker" should "track backup events" in {
    val tracker = createTracker()

    implicit val operation: Operation.Id = Operation.generateId()

    val entity = Fixtures.Metadata.FileOneMetadata.path
    val sourceEntity = SourceEntity(path = entity, existingMetadata = None, currentMetadata = Fixtures.Metadata.FileOneMetadata)

    val initialState = tracker.state.await
    initialState should be(empty)

    val rule = Rule(
      operation = Rule.Operation.Include,
      directory = "/work",
      pattern = "?",
      comment = None,
      original = Rule.Original(line = "", lineNumber = 0)
    )

    tracker.entityDiscovered(entity = entity)
    tracker.specificationProcessed(unmatched = Seq.empty)
    tracker.specificationProcessed(unmatched = Seq(rule -> new RuntimeException("Test failure #1")))
    tracker.entityExamined(entity = entity, metadataChanged = false, contentChanged = false)
    tracker.entityCollected(entity = sourceEntity)
    tracker.entityProcessingStarted(entity = entity, expectedParts = 1)
    tracker.entityPartProcessed(entity = entity)
    tracker.entityProcessed(entity = entity, metadata = Left(Fixtures.Metadata.FileOneMetadata))
    tracker.metadataCollected()
    tracker.metadataPushed(entry = Fixtures.Entries.Default.id)
    tracker.failureEncountered(new RuntimeException("Test failure #2"))
    tracker.failureEncountered(entity = Fixtures.Metadata.FileTwoMetadata.path, new RuntimeException("Test failure #3"))
    tracker.completed()

    eventually[Assertion] {
      val state = tracker.state.await.get(operation) match {
        case Some(value) => value
        case None        => fail(s"Expected state for operation [$operation] but none was found")
      }

      state.operation should be(operation)

      state.entities should be(
        BackupState.Entities(
          discovered = Set(entity),
          unmatched = Seq("Rule [+ /work ?] failed with [RuntimeException - Test failure #1]"),
          examined = Set(entity),
          collected = Map(entity -> sourceEntity),
          pending = Map.empty,
          processed = Map(
            entity -> ProcessedSourceEntity(expectedParts = 1, processedParts = 1, metadata = Left(sourceEntity.currentMetadata))
          ),
          failed = Map(
            Fixtures.Metadata.FileTwoMetadata.path -> "RuntimeException - Test failure #3"
          )
        )
      )

      state.metadataCollected should not be empty
      state.metadataPushed should not be empty
      state.failures should be(Seq("RuntimeException - Test failure #2"))
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

    tracker.entityDiscovered(entity)
    await(50.millis, withSystem = system)
    tracker.entityExamined(entity, metadataChanged = false, contentChanged = false)
    await(50.millis, withSystem = system)
    tracker.completed()

    updates.await.toList match {
      case first :: second :: third :: Nil =>
        first.entities.discovered should be(Set(entity))
        first.entities.examined should be(empty)
        first.completed should be(empty)

        second.entities.discovered should be(Set(entity))
        second.entities.examined should be(Set(entity))
        second.completed should be(empty)

        third.entities.discovered should be(Set(entity))
        third.entities.examined should be(Set(entity))
        third.completed should not be empty

      case other =>
        fail(s"Unexpected result received: [$other]")
    }
  }

  private def createTracker(): DefaultBackupTracker = DefaultBackupTracker(
    createBackend = state =>
      EventLogMemoryBackend(
        name = s"test-backup-tracker-${java.util.UUID.randomUUID()}",
        initialState = state
      )
  )

  override implicit val patienceConfig: PatienceConfig = PatienceConfig(7.seconds, 300.milliseconds)

  override implicit val timeout: Timeout = 7.seconds

  private implicit val system: ActorSystem[SpawnProtocol.Command] = ActorSystem(
    Behaviors.setup(_ => SpawnProtocol()): Behavior[SpawnProtocol.Command],
    "DefaultBackupTrackerSpec"
  )

  private implicit val telemetry: MockTelemetryContext = MockTelemetryContext()

  override protected def afterAll(): Unit =
    system.terminate()
}
