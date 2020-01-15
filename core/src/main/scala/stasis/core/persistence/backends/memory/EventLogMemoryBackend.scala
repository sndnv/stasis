package stasis.core.persistence.backends.memory

import scala.collection.immutable.Queue
import scala.concurrent.{ExecutionContext, Future}

import akka.Done
import akka.actor.Scheduler
import akka.actor.typed.{ActorRef, ActorSystem, Behavior, SpawnProtocol}
import akka.actor.typed.scaladsl.AskPattern._
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.scaladsl.adapter._
import akka.util.Timeout
import stasis.core.persistence.backends.EventLogBackend

class EventLogMemoryBackend[E, S] private (
  storeRef: Future[ActorRef[EventLogMemoryBackend.Message[E, S]]]
)(implicit scheduler: Scheduler, ec: ExecutionContext, timeout: Timeout)
    extends EventLogBackend[E, S] {
  import EventLogMemoryBackend._

  override def getState: Future[S] =
    storeRef.flatMap(_ ? (ref => GetState(ref)))

  override def getEvents: Future[Queue[E]] =
    storeRef.flatMap(_ ? (ref => GetEvents(ref)))

  override def storeEventAndUpdateState(event: E, update: (E, S) => S): Future[Done] =
    storeRef.flatMap(_ ? (ref => StoreEventAndUpdateState(event, update, ref)))
}

object EventLogMemoryBackend {
  def apply[E, S](
    name: String,
    initialState: => S
  )(implicit s: ActorSystem[SpawnProtocol], t: Timeout): EventLogMemoryBackend[E, S] =
    typed(name, initialState)

  def typed[E, S](
    name: String,
    initialState: => S
  )(implicit s: ActorSystem[SpawnProtocol], t: Timeout): EventLogMemoryBackend[E, S] = {
    implicit val scheduler: Scheduler = s.scheduler
    implicit val ec: ExecutionContext = s.executionContext

    val behaviour = store(state = initialState, events = Queue.empty[E])

    new EventLogMemoryBackend[E, S](
      storeRef = s ? SpawnProtocol.Spawn(behaviour, name)
    )
  }

  def untyped[E, S](
    name: String,
    initialState: => S
  )(implicit s: akka.actor.ActorSystem, t: Timeout): EventLogMemoryBackend[E, S] = {
    implicit val scheduler: Scheduler = s.scheduler
    implicit val ec: ExecutionContext = s.dispatcher

    val behaviour = store(state = initialState, events = Queue.empty[E])

    new EventLogMemoryBackend[E, S](
      storeRef = Future.successful(s.spawn(behaviour, name))
    )
  }

  private sealed trait Message[E, S]

  private final case class GetState[E, S](
    replyTo: ActorRef[S]
  ) extends Message[E, S]

  private final case class GetEvents[E, S](
    replyTo: ActorRef[Queue[E]]
  ) extends Message[E, S]

  private final case class StoreEventAndUpdateState[E, S](
    event: E,
    update: (E, S) => S,
    replyTo: ActorRef[Done]
  ) extends Message[E, S]

  private def store[E, S](
    state: S,
    events: Queue[E]
  ): Behavior[Message[E, S]] = Behaviors.receive { (_, message) =>
    message match {
      case StoreEventAndUpdateState(event, update, replyTo) =>
        replyTo ! Done
        store(state = update(event, state), events = events :+ event)

      case GetState(replyTo) =>
        replyTo ! state
        Behaviors.same

      case GetEvents(replyTo) =>
        replyTo ! events
        Behaviors.same
    }
  }
}
