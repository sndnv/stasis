package stasis.test.specs.unit.client.tracking.trackers

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorSystem, Behavior, SpawnProtocol}
import akka.stream.scaladsl.Sink
import org.scalatest.concurrent.Eventually
import org.scalatest.{Assertion, BeforeAndAfterAll}
import stasis.client.collection.rules.Rule
import stasis.client.tracking.TrackerView
import stasis.client.tracking.trackers.DefaultTracker
import stasis.core.persistence.backends.memory.EventLogMemoryBackend
import stasis.shared.model.datasets.DatasetEntry
import stasis.shared.ops.Operation
import stasis.test.specs.unit.AsyncUnitSpec
import stasis.test.specs.unit.core.telemetry.MockTelemetryContext

import java.nio.file.Paths
import scala.collection.immutable.Queue
import scala.concurrent.duration._

class DefaultTrackerSpec extends AsyncUnitSpec with Eventually with BeforeAndAfterAll {
  "A DefaultTracker" should "track backup events" in {
    val tracker = createTracker()

    implicit val operation: Operation.Id = Operation.generateId()
    val file = Paths.get("test").toAbsolutePath

    val initialState = tracker.state.await

    initialState should be(
      TrackerView.State.empty
    )

    val rule = Rule(
      operation = Rule.Operation.Include,
      directory = "/work",
      pattern = "?",
      comment = None,
      original = Rule.Original(line = "", lineNumber = 0)
    )

    tracker.backup.entityDiscovered(entity = file)
    tracker.backup.entityDiscovered(entity = file)
    tracker.backup.entityDiscovered(entity = file)
    tracker.backup.entityDiscovered(entity = file)
    tracker.backup.specificationProcessed(unmatched = Seq.empty)
    tracker.backup.specificationProcessed(unmatched = Seq(rule -> new RuntimeException("Test failure")))
    tracker.backup.entityExamined(entity = file, metadataChanged = true, contentChanged = false)
    tracker.backup.entityExamined(entity = file, metadataChanged = true, contentChanged = true)
    tracker.backup.entityCollected(entity = file)
    tracker.backup.entityProcessed(entity = file, contentChanged = true)
    tracker.backup.metadataCollected()
    tracker.backup.metadataPushed(entry = DatasetEntry.generateId())
    tracker.backup.failureEncountered(failure = new RuntimeException("test failure"))
    tracker.backup.completed()

    eventually[Assertion] {
      val completedState = tracker.state.await.operations(operation)

      completedState.completed should not be empty

      completedState.stages.contains("discovery") should be(true)
      completedState.stages("discovery").steps.size should be(4)

      completedState.stages.contains("specification") should be(true)
      completedState.stages("specification").steps.size should be(1)

      completedState.stages.contains("examination") should be(true)
      completedState.stages("examination").steps.size should be(2)

      completedState.stages.contains("collection") should be(true)
      completedState.stages("collection").steps.size should be(1)

      completedState.stages.contains("processing") should be(true)
      completedState.stages("processing").steps.size should be(1)

      completedState.stages.contains("metadata") should be(true)
      completedState.stages("metadata").steps.size should be(2)

      completedState.failures should be(
        Queue(
          "RuleMatchingFailure: Rule [+ /work ?] failed with [RuntimeException - Test failure]",
          "RuntimeException: test failure"
        )
      )
    }
  }

  it should "track recovery events" in {
    val tracker = createTracker()

    implicit val operation: Operation.Id = Operation.generateId()
    val file = Paths.get("test").toAbsolutePath

    val initialState = tracker.state.await

    initialState should be(
      TrackerView.State.empty
    )

    tracker.recovery.entityExamined(entity = file, metadataChanged = true, contentChanged = false)
    tracker.recovery.entityExamined(entity = file, metadataChanged = true, contentChanged = true)
    tracker.recovery.entityCollected(entity = file)
    tracker.recovery.entityProcessed(entity = file)
    tracker.recovery.metadataApplied(entity = file)
    tracker.recovery.failureEncountered(failure = new RuntimeException("test failure"))
    tracker.recovery.completed()

    eventually[Assertion] {
      val completedState = tracker.state.await.operations(operation)

      completedState.completed should not be empty

      completedState.stages.contains("examination") should be(true)
      completedState.stages("examination").steps.size should be(2)

      completedState.stages.contains("collection") should be(true)
      completedState.stages("collection").steps.size should be(1)

      completedState.stages.contains("processing") should be(true)
      completedState.stages("processing").steps.size should be(1)

      completedState.stages.contains("metadata-applied") should be(true)
      completedState.stages("metadata-applied").steps.size should be(1)

      completedState.failures should be(Queue("RuntimeException: test failure"))
    }
  }

  it should "track server events" in {
    val tracker = createTracker()

    val server1 = "test-server-01"
    val server2 = "test-server-02"

    val initialState = tracker.state.await

    initialState should be(
      TrackerView.State.empty
    )

    tracker.server.reachable(server1)
    tracker.server.reachable(server2)
    tracker.server.unreachable(server1)

    eventually[Assertion] {
      tracker.state.await.servers.view.mapValues(_.reachable).toMap should be(
        Map(
          server1 -> false,
          server2 -> true
        )
      )
    }
  }

  it should "provide state updates" in {
    val tracker = createTracker()

    implicit val operation: Operation.Id = Operation.generateId()
    val file = Paths.get("test").toAbsolutePath

    val initialState = tracker.state.await

    initialState should be(
      TrackerView.State.empty
    )

    val expectedUpdates = 3
    val updates = tracker.stateUpdates.take(expectedUpdates.toLong).runWith(Sink.seq)

    tracker.backup.entityExamined(entity = file, metadataChanged = true, contentChanged = false)
    await(50.millis, withSystem = system)
    tracker.backup.entityCollected(entity = file)
    await(50.millis, withSystem = system)
    tracker.recovery.completed()

    updates.await.flatMap(_.operations.get(operation)).toList match {
      case first :: second :: third :: Nil =>
        first.stages.size should be(1)
        first.completed should be(empty)

        second.stages.size should be(2)
        second.completed should be(empty)

        third.stages.size should be(2)
        third.completed should not be empty

      case other =>
        fail(s"Unexpected result received: [$other]")
    }
  }

  it should "provide operation updates" in {
    val tracker = createTracker()

    implicit val operation: Operation.Id = Operation.generateId()
    val file = Paths.get("test").toAbsolutePath

    val initialState = tracker.state.await

    initialState should be(
      TrackerView.State.empty
    )

    val expectedUpdates = 2
    val updates = tracker.operationUpdates(operation).take(expectedUpdates.toLong).runWith(Sink.seq)

    tracker.backup.entityExamined(entity = file, metadataChanged = true, contentChanged = false)
    await(50.millis, withSystem = system)
    tracker.backup.entityCollected(entity = file)(operation = Operation.generateId()) // other operation
    await(50.millis, withSystem = system)
    tracker.recovery.completed()

    updates.await.toList match {
      case first :: second :: Nil =>
        first.stages.size should be(1)
        first.completed should be(empty)

        second.stages.size should be(1)
        second.completed should not be empty

      case other =>
        fail(s"Unexpected result received: [$other]")
    }
  }

  private def createTracker(): DefaultTracker =
    DefaultTracker(
      createBackend = state =>
        EventLogMemoryBackend(
          name = s"test-tracker-${java.util.UUID.randomUUID()}",
          initialState = state
        )
    )

  override implicit val patienceConfig: PatienceConfig = PatienceConfig(5.seconds, 250.milliseconds)

  private implicit val system: ActorSystem[SpawnProtocol.Command] = ActorSystem(
    Behaviors.setup(_ => SpawnProtocol()): Behavior[SpawnProtocol.Command],
    "DefaultTrackerSpec"
  )

  private implicit val telemetry: MockTelemetryContext = MockTelemetryContext()

  override protected def afterAll(): Unit =
    system.terminate()
}
