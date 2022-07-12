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
import scala.concurrent.Future
import scala.util.control.NonFatal

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
        telemetry.persistence.eventLog.eventFailure should be(0)
      }
    }

    it should "handle state update failures" in {
      val telemetry: MockTelemetryContext = MockTelemetryContext()

      val store = createBackend(telemetry)

      for {
        eventsBefore <- store.getEvents
        stateBefore <- store.getState
        _ <- store.storeEventAndUpdateState(
          event = testEvent,
          update = (event, state) => state :+ event
        )
        eventsBeforeFailure <- store.getEvents
        stateBeforeFailure <- store.getState
        failure <- store
          .storeEventAndUpdateState(
            event = testEvent,
            update = (_, _) => throw new RuntimeException("Test failure")
          )
          .map { response =>
            fail(s"Received unexpected response: [$response]")
          }
          .recoverWith { case NonFatal(e) => Future.successful(e) }
        eventsAfterFailure <- store.getEvents
        stateAfterFailure <- store.getState
      } yield {
        eventsBefore should be(Queue.empty)
        stateBefore should be(Queue.empty)
        eventsBeforeFailure should be(Queue(testEvent))
        stateBeforeFailure should be(Queue(testEvent))
        eventsAfterFailure should be(Queue(testEvent))
        stateAfterFailure should be(Queue(testEvent))

        failure should be(a[RuntimeException])
        failure.getMessage should be("Test failure")

        telemetry.persistence.eventLog.event should be(1)
        telemetry.persistence.eventLog.eventFailure should be(1)
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
            telemetry.persistence.eventLog.eventFailure should be(0)

          case other =>
            fail(s"Received unexpected result: [$other]")
        }
      }
    }
  }
}
