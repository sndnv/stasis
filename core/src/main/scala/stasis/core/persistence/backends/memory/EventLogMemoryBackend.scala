package stasis.core.persistence.backends.memory

import akka.actor.typed._
import akka.actor.typed.scaladsl.AskPattern._
import akka.actor.typed.scaladsl.Behaviors
import akka.stream.scaladsl.Source
import akka.stream.{CompletionStrategy, OverflowStrategy}
import akka.util.Timeout
import akka.{Done, NotUsed}
import stasis.core.persistence.Metrics
import stasis.core.persistence.backends.EventLogBackend
import stasis.core.telemetry.TelemetryContext

import scala.collection.immutable.Queue
import scala.concurrent.{ExecutionContext, Future}
import scala.reflect.ClassTag
import scala.util.{Failure, Success, Try}

class EventLogMemoryBackend[E, S] private (
  name: String,
  storeRef: Future[ActorRef[EventLogMemoryBackend.Message[E, S]]],
  stateStreamBufferSize: Int
)(implicit
  system: ActorSystem[SpawnProtocol.Command],
  telemetry: TelemetryContext,
  timeout: Timeout,
  tag: ClassTag[S]
) extends EventLogBackend[E, S] {
  import EventLogMemoryBackend._

  private implicit val ec: ExecutionContext = system.executionContext

  private val metrics = telemetry.metrics[Metrics.EventLogBackend]

  override def getState: Future[S] =
    storeRef.flatMap(_ ? ((ref: ActorRef[S]) => GetState(ref)))

  override def getStateStream: Source[S, NotUsed] = {
    val (actor, source) = Source
      .actorRef[S](
        completionMatcher = PartialFunction.empty[Any, CompletionStrategy],
        failureMatcher = PartialFunction.empty[Any, Throwable],
        bufferSize = stateStreamBufferSize,
        overflowStrategy = OverflowStrategy.dropHead
      )
      .preMaterialize()

    val _ = system.classicSystem.eventStream.subscribe(subscriber = actor, channel = tag.runtimeClass)

    source
  }

  override def getEvents: Future[Queue[E]] =
    storeRef.flatMap(_ ? ((ref: ActorRef[Queue[E]]) => GetEvents(ref)))

  override def storeEventAndUpdateState(event: E, update: (E, S) => S): Future[Done] =
    storeRef
      .flatMap { messageRef =>
        (messageRef ? ((responseRef: ActorRef[Try[Done]]) => StoreEventAndUpdateState(event, update, responseRef))).flatMap {
          case Success(result) =>
            metrics.recordEvent(backend = name)
            Future.successful(result)

          case Failure(e) =>
            metrics.recordEventFailure(backend = name)
            Future.failed(e)
        }
      }
}

object EventLogMemoryBackend {
  final val DefaultStreamBufferSize: Int = 0

  def apply[E, S: ClassTag](
    name: String,
    initialState: => S
  )(implicit
    system: ActorSystem[SpawnProtocol.Command],
    telemetry: TelemetryContext,
    timeout: Timeout
  ): EventLogMemoryBackend[E, S] =
    apply(name = name, stateStreamBufferSize = DefaultStreamBufferSize, initialState = initialState)

  def apply[E, S: ClassTag](
    name: String,
    stateStreamBufferSize: Int,
    initialState: => S
  )(implicit
    system: ActorSystem[SpawnProtocol.Command],
    telemetry: TelemetryContext,
    timeout: Timeout
  ): EventLogMemoryBackend[E, S] = {
    val behaviour = store(state = initialState, events = Queue.empty[E])

    new EventLogMemoryBackend[E, S](
      name = name,
      storeRef = system ? (SpawnProtocol.Spawn(behaviour, name, Props.empty, _)),
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
    replyTo: ActorRef[Try[Done]]
  ) extends Message[E, S]

  private def store[E, S](
    state: S,
    events: Queue[E]
  ): Behavior[Message[E, S]] =
    Behaviors.receive { (context, message) =>
      message match {
        case StoreEventAndUpdateState(event, update, replyTo) =>
          Try(update(event, state)) match {
            case Success(updated) =>
              context.system.classicSystem.eventStream.publish(updated)

              replyTo ! Success(Done)
              store(state = updated, events = events :+ event)

            case Failure(e) =>
              replyTo ! Failure(e)
              Behaviors.same
          }

        case GetState(replyTo) =>
          replyTo ! state
          Behaviors.same

        case GetEvents(replyTo) =>
          replyTo ! events
          Behaviors.same
      }
    }
}
