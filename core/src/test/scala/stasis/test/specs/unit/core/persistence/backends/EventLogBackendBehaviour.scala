package stasis.test.specs.unit.core.persistence.backends

import akka.actor.typed.scaladsl.adapter._
import akka.actor.typed.{ActorSystem, SpawnProtocol}
import akka.stream.scaladsl.Sink
import akka.stream.{ActorMaterializer, Materializer}
import org.scalatest.concurrent.Eventually
import stasis.core.persistence.backends.EventLogBackend
import stasis.test.specs.unit.AsyncUnitSpec

import scala.collection.immutable.Queue

trait EventLogBackendBehaviour { _: AsyncUnitSpec with Eventually =>
  def eventLogBackend[B <: EventLogBackend[String, Queue[String]]](
    createBackend: () => B
  )(implicit system: ActorSystem[SpawnProtocol]): Unit = {
    implicit val untyped: akka.actor.ActorSystem = system.toUntyped
    implicit val mat: Materializer = ActorMaterializer()

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

    it should "provide a state update stream" in {
      eventually {
        val store = createBackend()

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

          case other =>
            fail(s"Received unexpected result: [$other]")
        }
      }
    }
  }
}
