package stasis.core.persistence.backends.file

import scala.collection.immutable.Queue
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration
import scala.reflect.ClassTag
import scala.util.Failure
import scala.util.Success
import scala.util.Try

import org.apache.pekko.Done
import org.apache.pekko.NotUsed
import org.apache.pekko.actor.typed._
import org.apache.pekko.actor.typed.scaladsl.AskPattern._
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.apache.pekko.actor.typed.scaladsl.TimerScheduler
import org.apache.pekko.stream.CompletionStrategy
import org.apache.pekko.stream.OverflowStrategy
import org.apache.pekko.stream.scaladsl.Source
import org.apache.pekko.util.Timeout
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import stasis.core.persistence.Metrics
import stasis.core.persistence.backends.EventLogBackend
import stasis.core.persistence.backends.file.state.StateStore
import stasis.layers.telemetry.TelemetryContext

class EventLogFileBackend[E, S] private (
  config: EventLogFileBackend.Config,
  storeRef: ActorRef[EventLogFileBackend.Message[E, S]]
)(implicit
  system: ActorSystem[Nothing],
  telemetry: TelemetryContext,
  timeout: Timeout,
  tag: ClassTag[S]
) extends EventLogBackend[E, S] {
  import EventLogFileBackend._

  private implicit val ec: ExecutionContext = system.executionContext

  private val metrics = telemetry.metrics[Metrics.EventLogBackend]

  override def getState: Future[S] =
    storeRef ? ((ref: ActorRef[S]) => GetState(ref))

  override def getStateStream: Source[S, NotUsed] = {
    val (actor, source) = Source
      .actorRef[S](
        completionMatcher = PartialFunction.empty[Any, CompletionStrategy],
        failureMatcher = PartialFunction.empty[Any, Throwable],
        bufferSize = config.stateStreamBufferSize,
        overflowStrategy = OverflowStrategy.dropHead
      )
      .preMaterialize()

    val _ = system.classicSystem.eventStream.subscribe(subscriber = actor, channel = tag.runtimeClass)

    source
  }

  override def storeEventAndUpdateState(event: E, update: (E, S) => S): Future[Done] =
    (storeRef ? ((responseRef: ActorRef[Try[Done]]) => StoreEventAndUpdateState(event, update, responseRef)))
      .flatMap {
        case Success(result) =>
          metrics.recordEvent(backend = config.name)
          Future.successful(result)

        case Failure(e) =>
          metrics.recordEventFailure(backend = config.name)
          Future.failed(e)
      }
}

object EventLogFileBackend {
  final val DefaultStreamBufferSize: Int = 0

  private case object PersistenceTimerKey

  def apply[E, S: ClassTag](
    config: Config,
    initialState: => S,
    stateStore: StateStore[S]
  )(implicit
    system: ActorSystem[Nothing],
    telemetry: TelemetryContext,
    timeout: Timeout
  ): EventLogFileBackend[E, S] = {

    val log: Logger = LoggerFactory.getLogger(this.getClass.getName)

    val behaviour = Behaviors.withTimers[Message[E, S]] { timers =>
      store(state = initialState, events = Queue.empty[E])(log, config, stateStore, timers)
    }

    val storeRef: ActorRef[EventLogFileBackend.Message[E, S]] =
      system.systemActorOf(behavior = behaviour, name = s"${config.name}-${java.util.UUID.randomUUID().toString}")

    storeRef ! EventLogFileBackend.StartStateRestore()

    new EventLogFileBackend[E, S](
      config = config,
      storeRef = storeRef
    )
  }

  final case class Config(
    name: String,
    stateStreamBufferSize: Int,
    persistAfterEvents: Int,
    persistAfterPeriod: FiniteDuration
  )

  object Config {
    def apply(
      name: String,
      persistAfterEvents: Int,
      persistAfterPeriod: FiniteDuration
    ): Config =
      new Config(
        name = name,
        stateStreamBufferSize = DefaultStreamBufferSize,
        persistAfterEvents = persistAfterEvents,
        persistAfterPeriod = persistAfterPeriod
      )
  }

  sealed trait Message[E, S]

  final case class StoreEventAndUpdateState[E, S](
    event: E,
    update: (E, S) => S,
    replyTo: ActorRef[Try[Done]]
  ) extends Message[E, S]

  final case class GetState[E, S](
    replyTo: ActorRef[S]
  ) extends Message[E, S]

  final case class StartStateRestore[E, S]() extends Message[E, S]
  final case class RestoreState[E, S]() extends Message[E, S]
  final case class StateRestored[E, S](state: S) extends Message[E, S]

  final case class StartStatePersist[E, S]() extends Message[E, S]
  final case class PersistState[E, S](state: S) extends Message[E, S]
  final case class StatePersisted[E, S](state: S) extends Message[E, S]

  private def store[E, S](
    state: S,
    events: Queue[E]
  )(implicit
    log: Logger,
    config: Config,
    stateStore: StateStore[S],
    timers: TimerScheduler[Message[E, S]]
  ): Behavior[Message[E, S]] =
    Behaviors.receivePartial {
      case (context, StoreEventAndUpdateState(event, update, replyTo)) =>
        Try(update(event, state)) match {
          case Success(updated) if events.sizeIs >= (config.persistAfterEvents - 1) =>
            context.system.classicSystem.eventStream.publish(updated)

            timers.cancel(key = PersistenceTimerKey)
            context.self ! PersistState(updated)

            replyTo ! Success(Done)
            persisting()

          case Success(updated) =>
            context.system.classicSystem.eventStream.publish(updated)

            if (!timers.isTimerActive(key = PersistenceTimerKey)) {
              timers.startSingleTimer(
                key = PersistenceTimerKey,
                msg = StartStatePersist(),
                delay = config.persistAfterPeriod
              )
            }

            replyTo ! Success(Done)
            store(state = updated, events = events :+ event)

          case Failure(e) =>
            replyTo ! Failure(e)
            Behaviors.same
        }

      case (_, GetState(replyTo)) =>
        replyTo ! state
        Behaviors.same

      case (context, StartStatePersist()) =>
        context.self ! PersistState(state)
        persisting()

      case (context, StartStateRestore()) =>
        context.self ! RestoreState()
        restoring(original = state)
    }

  private def persisting[E, S]()(implicit
    log: Logger,
    config: Config,
    stateStore: StateStore[S],
    timers: TimerScheduler[Message[E, S]]
  ): Behavior[Message[E, S]] =
    Behaviors.withStash(capacity = Int.MaxValue) { buffer =>
      Behaviors.receive { (context, message) =>
        message match {
          case PersistState(state) =>
            context.pipeToSelf(stateStore.persist(state)) {
              case Success(_) =>
                StatePersisted(state)

              case Failure(e) =>
                log.error("State persistence failed: [{} - {}]", e.getClass.getSimpleName, e.getMessage)
                StatePersisted(state)
            }

            Behaviors.same

          case StatePersisted(state) =>
            buffer.unstashAll(store(state = state, events = Queue.empty))

          case other =>
            val _ = buffer.stash(other)
            Behaviors.same
        }
      }
    }

  private def restoring[E, S](
    original: S
  )(implicit
    log: Logger,
    config: Config,
    stateStore: StateStore[S],
    timers: TimerScheduler[Message[E, S]]
  ): Behavior[Message[E, S]] =
    Behaviors.withStash(capacity = Int.MaxValue) { buffer =>
      Behaviors.receive { (context, message) =>
        message match {
          case RestoreState() =>
            context.pipeToSelf(stateStore.restore()) {
              case Success(Some(state)) =>
                StateRestored(state)

              case Success(None) =>
                StateRestored(state = original)

              case Failure(e) =>
                log.error("State restore failed: [{} - {}]", e.getClass.getSimpleName, e.getMessage)
                StateRestored(state = original)
            }

            Behaviors.same

          case StateRestored(state) =>
            buffer.unstashAll(store(state = state, events = Queue.empty))

          case other =>
            val _ = buffer.stash(other)
            Behaviors.same
        }
      }
    }

}
