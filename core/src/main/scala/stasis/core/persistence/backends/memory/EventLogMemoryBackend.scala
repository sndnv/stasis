package stasis.core.persistence.backends.memory

import akka.actor.Scheduler
import akka.actor.typed.scaladsl.AskPattern._
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.scaladsl.adapter._
import akka.actor.typed.{ActorRef, ActorSystem, Behavior, SpawnProtocol}
import akka.stream.scaladsl.Source
import akka.stream.{ActorMaterializer, Materializer, OverflowStrategy}
import akka.util.Timeout
import akka.{Done, NotUsed}
import stasis.core.persistence.backends.EventLogBackend

import scala.collection.immutable.Queue
import scala.concurrent.{ExecutionContext, Future}
import scala.reflect.ClassTag

class EventLogMemoryBackend[E, S] private (
  storeRef: Future[ActorRef[EventLogMemoryBackend.Message[E, S]]],
  stateStreamBufferSize: Int
)(implicit system: ActorSystem[SpawnProtocol], timeout: Timeout, tag: ClassTag[S])
    extends EventLogBackend[E, S] {
  import EventLogMemoryBackend._

  private implicit val untypedSystem: akka.actor.ActorSystem = system.toUntyped
  private implicit val ec: ExecutionContext = system.executionContext
  private implicit val scheduler: Scheduler = system.scheduler
  private implicit val mat: Materializer = ActorMaterializer()

  override def getState: Future[S] =
    storeRef.flatMap(_ ? (ref => GetState(ref)))

  override def getStateStream: Source[S, NotUsed] = {
    val (actor, source) = Source
      .actorRef[S](
        bufferSize = stateStreamBufferSize,
        overflowStrategy = OverflowStrategy.dropHead
      )
      .preMaterialize()

    val _ = untypedSystem.eventStream.subscribe(subscriber = actor, channel = tag.runtimeClass)

    source
  }

  override def getEvents: Future[Queue[E]] =
    storeRef.flatMap(_ ? (ref => GetEvents(ref)))

  override def storeEventAndUpdateState(event: E, update: (E, S) => S): Future[Done] =
    storeRef.flatMap(_ ? (ref => StoreEventAndUpdateState(event, update, ref)))
}

object EventLogMemoryBackend {
  final val DefaultStreamBufferSize: Int = 0

  def apply[E, S: ClassTag](
    name: String,
    initialState: => S
  )(implicit system: ActorSystem[SpawnProtocol], timeout: Timeout): EventLogMemoryBackend[E, S] =
    apply(name = name, stateStreamBufferSize = DefaultStreamBufferSize, initialState = initialState)

  def apply[E, S: ClassTag](
    name: String,
    stateStreamBufferSize: Int,
    initialState: => S
  )(implicit system: ActorSystem[SpawnProtocol], timeout: Timeout): EventLogMemoryBackend[E, S] = {
    implicit val scheduler: Scheduler = system.scheduler

    val behaviour = store(state = initialState, events = Queue.empty[E])
    new EventLogMemoryBackend[E, S](
      storeRef = system ? SpawnProtocol.Spawn(behaviour, name),
      stateStreamBufferSize = stateStreamBufferSize
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
  ): Behavior[Message[E, S]] = Behaviors.receive { (context, message) =>
    message match {
      case StoreEventAndUpdateState(event, update, replyTo) =>
        val updated = update(event, state)
        context.system.toUntyped.eventStream.publish(updated)

        replyTo ! Done
        store(state = updated, events = events :+ event)

      case GetState(replyTo) =>
        replyTo ! state
        Behaviors.same

      case GetEvents(replyTo) =>
        replyTo ! events
        Behaviors.same
    }
  }
}
