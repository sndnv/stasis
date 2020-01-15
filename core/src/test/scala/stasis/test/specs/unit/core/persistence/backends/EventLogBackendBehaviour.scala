package stasis.test.specs.unit.core.persistence.backends

import scala.collection.immutable.Queue

import stasis.core.persistence.backends.EventLogBackend
import stasis.test.specs.unit.AsyncUnitSpec

trait EventLogBackendBehaviour { _: AsyncUnitSpec =>
  def eventLogBackend[B <: EventLogBackend[String, Queue[String]]](
    createBackend: () => B
  ): Unit = {
    val testEvent = "test-event"

    it should "store events and update state" in {
      val store = createBackend()

      for {
        eventsBefore <- store.getEvents
        stateBefore <- store.getState
        _ <- store.storeEventAndUpdateState(event = testEvent, update = (event, state) => state :+ event)
        eventsAfter <- store.getEvents
        stateAfter <- store.getState
      } yield {
        eventsBefore should be(Queue.empty)
        stateBefore should be(Queue.empty)
        eventsAfter should be(Queue(testEvent))
        stateAfter should be(Queue(testEvent))
      }
    }
  }
}
