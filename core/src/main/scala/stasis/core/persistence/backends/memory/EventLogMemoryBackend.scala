package stasis.core.persistence.backends.memory

import scala.collection.immutable.Queue
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.reflect.ClassTag
import scala.util.Failure
import scala.util.Success
import scala.util.Try

import org.apache.pekko.Done
import org.apache.pekko.NotUsed
import org.apache.pekko.actor.typed._
import org.apache.pekko.actor.typed.scaladsl.AskPattern._
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.apache.pekko.stream.CompletionStrategy
import org.apache.pekko.stream.OverflowStrategy
import org.apache.pekko.stream.scaladsl.Source
import org.apache.pekko.util.Timeout

import stasis.core.persistence.Metrics
import stasis.core.persistence.backends.EventLogBackend
import io.github.sndnv.layers.telemetry.TelemetryContext

class EventLogMemoryBackend[E, S] private (
  name: String,
  storeRef: ActorRef[EventLogMemoryBackend.Message[E, S]],
  stateStreamBufferSize: Int
)(implicit
  system: ActorSystem[Nothing],
  telemetry: TelemetryContext,
  timeout: Timeout,
  tag: ClassTag[S]
) extends EventLogBackend[E, S] {
  import EventLogMemoryBackend._

  private implicit val ec: ExecutionContext = system.executionContext

  private val metrics = telemetry.metrics[Metrics.EventLogBackend]

  override def getState: Future[S] =
    storeRef ? ((ref: ActorRef[S]) => GetState(ref))

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

  override def storeEventAndUpdateState(event: E, update: (E, S) => S): Future[Done] =
    (storeRef ? ((responseRef: ActorRef[Try[Done]]) => StoreEventAndUpdateState(event, update, responseRef))).flatMap {
      case Success(result) =>
        metrics.recordEvent(backend = name)
        Future.successful(result)

      case Failure(e) =>
        metrics.recordEventFailure(backend = name)
        Future.failed(e)
    }
}

object EventLogMemoryBackend {
  final val DefaultStreamBufferSize: Int = 0

  def apply[E, S: ClassTag](
    name: String,
    initialState: => S
  )(implicit
    system: ActorSystem[Nothing],
    telemetry: TelemetryContext,
    timeout: Timeout
  ): EventLogMemoryBackend[E, S] =
    apply(
      name = name,
      stateStreamBufferSize = DefaultStreamBufferSize,
      initialState = initialState
    )

  def apply[E, S: ClassTag](
    name: String,
    stateStreamBufferSize: Int,
    initialState: => S
  )(implicit
    system: ActorSystem[Nothing],
    telemetry: TelemetryContext,
    timeout: Timeout
  ): EventLogMemoryBackend[E, S] = {
    val behaviour = store(state = initialState, events = Queue.empty[E])

    new EventLogMemoryBackend[E, S](
      name = name,
      storeRef = system.systemActorOf(behavior = behaviour, name = s"$name-${java.util.UUID.randomUUID().toString}"),
      stateStreamBufferSize = stateStreamBufferSize
    )
  }

  private sealed trait Message[E, S]

  private final case class GetState[E, S](
    replyTo: ActorRef[S]
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
      }
    }
}
