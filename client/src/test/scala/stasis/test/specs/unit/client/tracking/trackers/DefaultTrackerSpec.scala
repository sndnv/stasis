package stasis.test.specs.unit.client.tracking.trackers

import java.nio.file.Paths

import akka.actor.typed.{ActorSystem, Behavior, SpawnProtocol}
import akka.actor.typed.scaladsl.Behaviors
import org.scalatest.concurrent.Eventually
import org.scalatest.BeforeAndAfterAll
import stasis.client.tracking.trackers.DefaultTracker
import stasis.client.tracking.TrackerView
import stasis.core.persistence.backends.memory.EventLogMemoryBackend
import stasis.shared.model.datasets.DatasetEntry
import stasis.shared.ops.Operation
import stasis.test.specs.unit.AsyncUnitSpec

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

    tracker.backup.fileExamined(file, metadataChanged = true, contentChanged = false)
    tracker.backup.fileExamined(file, metadataChanged = true, contentChanged = true)
    tracker.backup.fileCollected(file)
    tracker.backup.fileProcessed(file, contentChanged = true)
    tracker.backup.metadataCollected()
    tracker.backup.metadataPushed(entry = DatasetEntry.generateId())
    tracker.backup.failureEncountered(failure = new RuntimeException("test failure"))
    tracker.backup.completed()

    eventually {
      val completedState = tracker.state.await.operations(operation)

      completedState.completed should not be empty

      completedState.stages.contains("examination") should be(true)
      completedState.stages("examination").steps.size should be(2)

      completedState.stages.contains("collection") should be(true)
      completedState.stages("collection").steps.size should be(1)

      completedState.stages.contains("processing") should be(true)
      completedState.stages("processing").steps.size should be(1)

      completedState.stages.contains("metadata") should be(true)
      completedState.stages("metadata").steps.size should be(2)

      completedState.failures should be(Queue("test failure"))
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

    tracker.recovery.fileExamined(file, metadataChanged = true, contentChanged = false)
    tracker.recovery.fileExamined(file, metadataChanged = true, contentChanged = true)
    tracker.recovery.fileCollected(file)
    tracker.recovery.fileProcessed(file)
    tracker.recovery.metadataApplied(file)
    tracker.recovery.failureEncountered(failure = new RuntimeException("test failure"))
    tracker.recovery.completed()

    eventually {
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

      completedState.failures should be(Queue("test failure"))
    }
  }

  it should "track server events" in {
    val tracker = createTracker()

    implicit val operation: Operation.Id = Operation.generateId()
    val server1 = "test-server-01"
    val server2 = "test-server-02"

    val initialState = tracker.state.await

    initialState should be(
      TrackerView.State.empty
    )

    tracker.server.reachable(server1)
    tracker.server.reachable(server2)
    tracker.server.unreachable(server1)

    eventually {
      tracker.state.await.servers.mapValues(_.reachable) should be(
        Map(
          server1 -> false,
          server2 -> true
        )
      )
    }
  }

  private def createTracker(): DefaultTracker = DefaultTracker(
    createBackend = state =>
      EventLogMemoryBackend(
        name = s"test-tracker-${java.util.UUID.randomUUID()}",
        initialState = state
    )
  )

  override implicit val patienceConfig: PatienceConfig = PatienceConfig(5.seconds, 250.milliseconds)

  private implicit val typedSystem: ActorSystem[SpawnProtocol] = ActorSystem(
    Behaviors.setup(_ => SpawnProtocol.behavior): Behavior[SpawnProtocol],
    "DefaultTrackerSpec"
  )

  override protected def afterAll(): Unit =
    typedSystem.terminate()
}
