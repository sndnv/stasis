package stasis.test.specs.unit.client.tracking.trackers

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorSystem, Behavior, SpawnProtocol}
import akka.stream.scaladsl.Sink
import akka.util.Timeout
import org.scalatest.{Assertion, BeforeAndAfterAll}
import org.scalatest.concurrent.Eventually
import stasis.client.tracking.trackers.DefaultServerTracker
import stasis.core.persistence.backends.memory.EventLogMemoryBackend
import stasis.test.specs.unit.AsyncUnitSpec
import stasis.test.specs.unit.core.telemetry.MockTelemetryContext

import scala.concurrent.duration._

class DefaultServerTrackerSpec extends AsyncUnitSpec with Eventually with BeforeAndAfterAll {
  "A DefaultServerTracker" should "track server events" in withRetry {
    val tracker = createTracker()

    val server1 = "test-server-01"
    val server2 = "test-server-02"

    val initialState = tracker.state.await
    initialState should be(empty)

    tracker.reachable(server1)
    await(100.millis, withSystem = system)
    tracker.reachable(server2)
    await(100.millis, withSystem = system)
    tracker.unreachable(server1)
    await(100.millis, withSystem = system)

    eventually[Assertion] {
      val state = tracker.state.await

      state.get(server1).map(_.reachable) should be(Some(false))
      state.get(server2).map(_.reachable) should be(Some(true))
    }
  }

  it should "provide state updates" in withRetry {
    val tracker = createTracker()

    val server = "test-server-01"

    val initialState = tracker.state.await
    initialState should be(empty)

    val expectedUpdates = 3
    val updates = tracker.updates(server).take(expectedUpdates.toLong).runWith(Sink.seq)
    await(100.millis, withSystem = system)

    tracker.reachable(server)
    await(100.millis, withSystem = system)
    tracker.reachable(server)
    await(100.millis, withSystem = system)
    tracker.unreachable(server)

    updates.await.toList match {
      case first :: second :: third :: Nil =>
        first.reachable should be(true)
        second.reachable should be(true)
        third.reachable should be(false)

      case other =>
        fail(s"Unexpected result received: [$other]")
    }
  }

  it should "support providing at least one state update" in withRetry {
    val tracker = createTracker()

    val server = "test-server-01"

    val initialState = tracker.state.await
    initialState should be(empty)

    val expectedUpdates = 1

    tracker.reachable(server)
    tracker.reachable(server)
    tracker.unreachable(server)
    await(100.millis, withSystem = system)

    val updates = tracker.updates(server).take(expectedUpdates.toLong).runWith(Sink.seq)
    await(100.millis, withSystem = system)

    updates.await.toList match {
      case update :: Nil =>
        update.reachable should be(false)

      case other =>
        fail(s"Unexpected result received: [$other]")
    }
  }

  private def createTracker(): DefaultServerTracker =
    DefaultServerTracker(
      createBackend = state =>
        EventLogMemoryBackend(
          name = s"test-server-tracker-${java.util.UUID.randomUUID()}",
          initialState = state
        )
    )

  override implicit val patienceConfig: PatienceConfig = PatienceConfig(7.seconds, 300.milliseconds)

  override implicit val timeout: Timeout = 7.seconds

  private implicit val system: ActorSystem[SpawnProtocol.Command] = ActorSystem(
    Behaviors.setup(_ => SpawnProtocol()): Behavior[SpawnProtocol.Command],
    "DefaultServerTrackerSpec"
  )

  private implicit val telemetry: MockTelemetryContext = MockTelemetryContext()

  override protected def afterAll(): Unit =
    system.terminate()
}
