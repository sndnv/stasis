package stasis.test.specs.unit.core.persistence.backends

import akka.actor.typed.{ActorSystem, SpawnProtocol}
import akka.stream.scaladsl.Sink
import org.scalatest.Assertion
import org.scalatest.concurrent.Eventually
import stasis.core.persistence.backends.EventLogBackend
import stasis.core.telemetry.TelemetryContext
import stasis.test.specs.unit.AsyncUnitSpec
import stasis.test.specs.unit.core.telemetry.MockTelemetryContext

import scala.collection.immutable.Queue

trait EventLogBackendBehaviour { _: AsyncUnitSpec with Eventually =>
  def eventLogBackend[B <: EventLogBackend[String, Queue[String]]](
    createBackend: TelemetryContext => B
  )(implicit system: ActorSystem[SpawnProtocol.Command]): Unit = {

    val testEvent = "test-event"

    it should "store events and update state" in {
      val telemetry: MockTelemetryContext = MockTelemetryContext()

      val store = createBackend(telemetry)

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

        telemetry.persistence.eventLog.event should be(1)
      }
    }

    it should "provide a state update stream" in {
      val telemetry: MockTelemetryContext = MockTelemetryContext()

      eventually[Assertion] {
        val store = createBackend(telemetry)

        val updates = store.getStateStream.take(3).runWith(Sink.seq)

        val eventsBefore = store.getEvents.await
        val stateBefore = store.getState.await
        eventsBefore should be(Queue.empty)
        stateBefore should be(Queue.empty)

        store.storeEventAndUpdateState(event = testEvent, update = (event, state) => state :+ event).await
        store.storeEventAndUpdateState(event = testEvent, update = (event, state) => state :+ event).await
        store.storeEventAndUpdateState(event = testEvent, update = (event, state) => state :+ event).await

        val eventsAfter = store.getEvents.await
        val stateAfter = store.getState.await
        eventsAfter should be(Queue(testEvent, testEvent, testEvent))
        stateAfter should be(Queue(testEvent, testEvent, testEvent))

        updates.await.toList match {
          case first :: second :: third :: Nil =>
            first should be(Queue(testEvent))
            second should be(Queue(testEvent, testEvent))
            third should be(Queue(testEvent, testEvent, testEvent))

            telemetry.persistence.eventLog.event should be >= 3

          case other =>
            fail(s"Received unexpected result: [$other]")
        }
      }
    }
  }
}
