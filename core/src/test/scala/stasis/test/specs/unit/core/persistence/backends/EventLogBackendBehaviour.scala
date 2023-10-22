package stasis.test.specs.unit.core.persistence.backends

import org.apache.pekko.actor.typed.{ActorSystem, SpawnProtocol}
import org.apache.pekko.stream.scaladsl.Sink
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

    it should "store events and update state" in withRetry {
      val telemetry: MockTelemetryContext = MockTelemetryContext()

      val store = createBackend(telemetry)

      for {
        stateBefore <- store.getState
        _ <- store.storeEventAndUpdateState(event = testEvent, update = (event, state) => state :+ event)
        stateAfter <- store.getState
      } yield {
        stateBefore should be(Queue.empty)
        stateAfter should be(Queue(testEvent))

        telemetry.persistence.eventLog.event should be(1)
        telemetry.persistence.eventLog.eventFailure should be(0)
      }
    }

    it should "handle state update failures" in withRetry {
      val telemetry: MockTelemetryContext = MockTelemetryContext()

      val store = createBackend(telemetry)

      for {
        stateBefore <- store.getState
        _ <- store.storeEventAndUpdateState(
          event = testEvent,
          update = (event, state) => state :+ event
        )
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
        stateAfterFailure <- store.getState
      } yield {
        stateBefore should be(Queue.empty)
        stateBeforeFailure should be(Queue(testEvent))
        stateAfterFailure should be(Queue(testEvent))

        failure should be(a[RuntimeException])
        failure.getMessage should be("Test failure")

        telemetry.persistence.eventLog.event should be(1)
        telemetry.persistence.eventLog.eventFailure should be(1)
      }
    }

    it should "provide a state update stream" in withRetry {
      val telemetry: MockTelemetryContext = MockTelemetryContext()

      eventually[Assertion] {
        val store = createBackend(telemetry)

        val updates = store.getStateStream.take(3).runWith(Sink.seq)

        val stateBefore = store.getState.await
        stateBefore should be(Queue.empty)

        store.storeEventAndUpdateState(event = testEvent, update = (event, state) => state :+ event).await
        store.storeEventAndUpdateState(event = testEvent, update = (event, state) => state :+ event).await
        store.storeEventAndUpdateState(event = testEvent, update = (event, state) => state :+ event).await

        val stateAfter = store.getState.await
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
