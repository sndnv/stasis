package stasis.test.specs.unit.core.persistence.events

import scala.collection.immutable.Queue

import akka.actor.typed.{ActorSystem, Behavior, SpawnProtocol}
import akka.actor.typed.scaladsl.Behaviors
import stasis.core.persistence.backends.EventLogBackend
import stasis.core.persistence.backends.memory.EventLogMemoryBackend
import stasis.core.persistence.events.EventLog
import stasis.test.specs.unit.AsyncUnitSpec

class EventLogSpec extends AsyncUnitSpec {
  "An EventLog" should "store events" in {
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
    }
  }

  it should "retrieve state" in {
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
    }
  }

  it should "provide a read-only view" in {
    val backend = createBackend()
    val store = EventLog[String, Queue[String]](backend, updateState)
    val storeView = store.view

    val testEvent = "test-event"

    for {
      stateBefore <- storeView.state
      _ <- backend.storeEventAndUpdateState(event = testEvent, update = updateState)
      stateAfter <- storeView.state
    } yield {
      stateBefore should be(Queue.empty)
      stateAfter should be(Queue(testEvent))
      a[ClassCastException] should be thrownBy { val _ = storeView.asInstanceOf[EventLog[_, _]] }
    }
  }

  private def createBackend(): EventLogBackend[String, Queue[String]] =
    EventLogMemoryBackend(
      name = s"event-log-backend-${java.util.UUID.randomUUID()}",
      initialState = Queue.empty[String]
    )

  private implicit val system: ActorSystem[SpawnProtocol] = ActorSystem(
    guardianBehavior = Behaviors.setup(_ => SpawnProtocol.behavior): Behavior[SpawnProtocol],
    name = "EventLogSpec"
  )

  private def updateState(event: String, state: Queue[String]): Queue[String] = state :+ event
}
