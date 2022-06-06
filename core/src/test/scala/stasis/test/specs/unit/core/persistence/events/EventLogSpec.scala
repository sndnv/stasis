package stasis.test.specs.unit.core.persistence.events

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorSystem, Behavior, SpawnProtocol}
import akka.stream.scaladsl.Sink
import org.scalatest.Assertion
import org.scalatest.concurrent.Eventually
import stasis.core.persistence.backends.EventLogBackend
import stasis.core.persistence.backends.memory.EventLogMemoryBackend
import stasis.core.persistence.events.EventLog
import stasis.core.telemetry.TelemetryContext
import stasis.test.specs.unit.AsyncUnitSpec
import stasis.test.specs.unit.core.telemetry.MockTelemetryContext

import scala.collection.immutable.Queue
import scala.concurrent.duration._

class EventLogSpec extends AsyncUnitSpec with Eventually {
  "An EventLog" should "store events" in {
    implicit val telemetry: MockTelemetryContext = MockTelemetryContext()

    val backend = createBackend()
    val store = EventLog[String, Queue[String]](backend, updateState)

    val testEvent = "test-event"

    for {
      eventsBefore <- backend.getEvents
      stateBefore <- backend.getState
      _ <- store.store(testEvent)
      eventsAfter <- backend.getEvents
      stateAfter <- backend.getState
    } yield {
      eventsBefore should be(Queue.empty)
      stateBefore should be(Queue.empty)
      eventsAfter should be(Queue(testEvent))
      stateAfter should be(Queue(testEvent))

      telemetry.persistence.eventLog.event should be(1)
    }
  }

  it should "retrieve state" in {
    implicit val telemetry: MockTelemetryContext = MockTelemetryContext()

    val backend = createBackend()
    val store = EventLog[String, Queue[String]](backend, updateState)

    val testEvent = "test-event"

    for {
      stateBefore <- store.state
      _ <- backend.storeEventAndUpdateState(event = testEvent, update = updateState)
      stateAfter <- store.state
    } yield {
      stateBefore should be(Queue.empty)
      stateAfter should be(Queue(testEvent))

      telemetry.persistence.eventLog.event should be(1)
    }
  }

  it should "provide a state update stream" in {
    eventually[Assertion] {
      implicit val telemetry: MockTelemetryContext = MockTelemetryContext()

      val backend = createBackend()
      val store = EventLog[String, Queue[String]](backend, updateState)

      val testEvent1 = "test-event-1"
      val testEvent2 = "test-event-2"
      val testEvent3 = "test-event-3"

      val updates = store.stateStream.take(3).runWith(Sink.seq)

      val stateBefore = store.state.await
      stateBefore should be(Queue.empty)

      backend.storeEventAndUpdateState(event = testEvent1, update = updateState).await
      backend.storeEventAndUpdateState(event = testEvent2, update = updateState).await
      backend.storeEventAndUpdateState(event = testEvent3, update = updateState).await

      val stateAfter = store.state.await
      stateAfter should be(Queue(testEvent1, testEvent2, testEvent3))

      updates.await.toList match {
        case first :: second :: third :: Nil =>
          first should be(Queue(testEvent1))
          second should be(Queue(testEvent1, testEvent2))
          third should be(Queue(testEvent1, testEvent2, testEvent3))

          telemetry.persistence.eventLog.event should be(3)

        case other =>
          fail(s"Received unexpected result: [$other]")
      }
    }
  }

  it should "provide a read-only view" in {
    implicit val telemetry: MockTelemetryContext = MockTelemetryContext()

    val backend = createBackend()
    val store = EventLog[String, Queue[String]](backend, updateState)
    val storeView = store.view

    val testEvent = "test-event"

    val updates = storeView.stateStream.take(1).runWith(Sink.seq)

    for {
      stateBefore <- storeView.state
      _ <- backend.storeEventAndUpdateState(event = testEvent, update = updateState)
      stateAfter <- storeView.state
      updates <- updates
    } yield {
      stateBefore should be(Queue.empty)
      updates should be(Seq(Queue(testEvent)))
      stateAfter should be(Queue(testEvent))

      telemetry.persistence.eventLog.event should be(1)

      a[ClassCastException] should be thrownBy { val _ = storeView.asInstanceOf[EventLog[_, _]] }
    }
  }

  private def createBackend()(implicit telemetry: TelemetryContext): EventLogBackend[String, Queue[String]] =
    EventLogMemoryBackend(
      name = s"event-log-backend-${java.util.UUID.randomUUID()}",
      initialState = Queue.empty[String]
    )

  private implicit val system: ActorSystem[SpawnProtocol.Command] = ActorSystem(
    guardianBehavior = Behaviors.setup(_ => SpawnProtocol()): Behavior[SpawnProtocol.Command],
    name = "EventLogSpec"
  )

  private def updateState(event: String, state: Queue[String]): Queue[String] = state :+ event

  override implicit val patienceConfig: PatienceConfig = PatienceConfig(5.seconds, 250.milliseconds)
}
