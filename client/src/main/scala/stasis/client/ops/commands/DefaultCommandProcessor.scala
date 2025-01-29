package stasis.client.ops.commands

import java.util.concurrent.ThreadLocalRandom

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.Failure
import scala.util.Success
import scala.util.Try

import org.apache.pekko.Done
import org.apache.pekko.actor.typed.ActorRef
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.Behavior
import org.apache.pekko.actor.typed.Scheduler
import org.apache.pekko.actor.typed.scaladsl.AskPattern._
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.apache.pekko.util.Timeout
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import stasis.client.api.clients.ServerApiEndpointClient
import stasis.core.commands.proto.Command

class DefaultCommandProcessor private (
  processorRef: ActorRef[DefaultCommandProcessor.Message],
  override protected val handlers: CommandProcessor.Handlers
)(implicit scheduler: Scheduler, timeout: Timeout, ec: ExecutionContext)
    extends CommandProcessor {
  override def all(): Future[Seq[Command]] =
    (processorRef ? (ref => DefaultCommandProcessor.GetAll(ref))).flatMap(Future.fromTry)

  override def latest(): Future[Seq[Command]] =
    (processorRef ? (ref => DefaultCommandProcessor.GetLatest(ref))).flatMap(Future.fromTry)

  override def stop(): Future[Done] =
    processorRef ? (ref => DefaultCommandProcessor.Stop(ref))
}

object DefaultCommandProcessor {
  def apply(
    initialDelay: FiniteDuration,
    interval: FiniteDuration,
    api: ServerApiEndpointClient,
    handlers: CommandProcessor.Handlers
  )(implicit system: ActorSystem[Nothing], timeout: Timeout): DefaultCommandProcessor = {
    import system.executionContext

    implicit val log: Logger = LoggerFactory.getLogger(this.getClass.getName)

    val behaviour = processor(
      initialDelay = initialDelay,
      interval = interval,
      api = api,
      commandHandlers = handlers,
      resultHandlers = new CommandProcessingResultHandlers.Default(log)
    )

    new DefaultCommandProcessor(
      processorRef = system.systemActorOf(behaviour, name = s"command-processor-${java.util.UUID.randomUUID().toString}"),
      handlers = handlers
    )
  }

  private sealed trait Message
  private case object RetrieveCommands extends Message
  private final case class ScheduleNextRetrieval(after: FiniteDuration) extends Message
  private final case class GetAll(replyTo: ActorRef[Try[Seq[Command]]]) extends Message
  private final case class GetLatest(replyTo: ActorRef[Try[Seq[Command]]]) extends Message
  private final case class Stop(replyTo: ActorRef[Done]) extends Message

  private case object RetrievalTimerKey

  private def processor(
    initialDelay: FiniteDuration,
    interval: FiniteDuration,
    api: ServerApiEndpointClient,
    commandHandlers: CommandProcessor.Handlers,
    resultHandlers: CommandProcessingResultHandlers
  )(implicit log: Logger): Behavior[Message] =
    Behaviors.withTimers[Message] { timers =>
      timers.startSingleTimer(RetrievalTimerKey, RetrieveCommands, initialDelay)

      Behaviors.receive { case (ctx, message) =>
        message match {
          case GetAll(replyTo) =>
            val self = ctx.self

            import ctx.executionContext

            implicit val rnd: ThreadLocalRandom = ThreadLocalRandom.current()

            timers.cancel(RetrievalTimerKey)

            commandHandlers
              .retrieveLastProcessedCommand()
              .flatMap { lastProcessedCommand =>
                api.commands(lastSequenceId = None).map { commands => (commands, lastProcessedCommand) }
              }
              .onComplete {
                case Success((commands, lastProcessedCommand)) =>
                  log.debug("Server [{}] responded with [{}] command(s)", api.server, commands.length)

                  process(
                    commandHandlers = commandHandlers,
                    resultHandlers = resultHandlers,
                    commands = commands.filter(command => lastProcessedCommand.forall(command.sequenceId > _))
                  )

                  self ! ScheduleNextRetrieval(after = fullInterval(interval))
                  replyTo ! Success(commands)

                case Failure(e) =>
                  log.error(
                    "Failed to retrieve commands from server [{}]: [{} - {}]",
                    api.server,
                    e.getClass.getSimpleName,
                    e.getMessage
                  )
                  self ! ScheduleNextRetrieval(after = reducedInterval(initialDelay, interval))
                  replyTo ! Failure(e)

              }(ctx.executionContext)

            Behaviors.same

          case GetLatest(replyTo) =>
            val self = ctx.self

            import ctx.executionContext

            implicit val rnd: ThreadLocalRandom = ThreadLocalRandom.current()

            timers.cancel(RetrievalTimerKey)

            commandHandlers
              .retrieveLastProcessedCommand()
              .flatMap(api.commands)
              .onComplete {
                case Success(commands) =>
                  log.debug("Server [{}] responded with [{}] command(s)", api.server, commands.length)
                  process(commandHandlers = commandHandlers, resultHandlers = resultHandlers, commands = commands)
                  self ! ScheduleNextRetrieval(after = fullInterval(interval))
                  replyTo ! Success(commands)

                case Failure(e) =>
                  log.error(
                    "Failed to retrieve commands from server [{}]: [{} - {}]",
                    api.server,
                    e.getClass.getSimpleName,
                    e.getMessage
                  )
                  self ! ScheduleNextRetrieval(after = reducedInterval(initialDelay, interval))
                  replyTo ! Failure(e)

              }(ctx.executionContext)

            Behaviors.same

          case RetrieveCommands =>
            val self = ctx.self

            import ctx.executionContext

            implicit val rnd: ThreadLocalRandom = ThreadLocalRandom.current()

            commandHandlers
              .retrieveLastProcessedCommand()
              .flatMap(api.commands)
              .onComplete {
                case Success(commands) =>
                  log.debug("Server [{}] responded with [{}] command(s)", api.server, commands.length)
                  process(commandHandlers = commandHandlers, resultHandlers = resultHandlers, commands = commands)
                  self ! ScheduleNextRetrieval(after = fullInterval(interval))

                case Failure(e) =>
                  log.error(
                    "Failed to retrieve commands from server [{}]: [{} - {}]",
                    api.server,
                    e.getClass.getSimpleName,
                    e.getMessage
                  )
                  self ! ScheduleNextRetrieval(after = reducedInterval(initialDelay, interval))
              }

            Behaviors.same

          case ScheduleNextRetrieval(after) =>
            log.debug("Scheduling next command retrieval in [{}] second(s)", after.toSeconds)
            timers.startSingleTimer(RetrievalTimerKey, RetrieveCommands, after)
            Behaviors.same

          case Stop(replyTo) =>
            log.debug("Stopping command processor")
            replyTo ! Done
            Behaviors.stopped
        }
      }
    }

  private val FailureIntervalReduction: Long = 10L

  def fullInterval(
    interval: FiniteDuration
  )(implicit rnd: ThreadLocalRandom): FiniteDuration = {
    val original = interval.toMillis
    val low = (original - (original * 0.02)).toLong
    val high = (original + (original * 0.03)).toLong

    rnd.nextLong(low, high).millis
  }

  def reducedInterval(
    initialDelay: FiniteDuration,
    interval: FiniteDuration
  )(implicit rnd: ThreadLocalRandom): FiniteDuration =
    initialDelay.max(fullInterval(interval / FailureIntervalReduction))

  def process(
    commandHandlers: CommandProcessor.Handlers,
    resultHandlers: CommandProcessingResultHandlers,
    commands: Seq[Command]
  )(implicit ec: ExecutionContext): Unit =
    if (commands.nonEmpty) {
      val result = for {
        lastExecutedCommand <- commandHandlers.executeCommands(commands = commands)
        result <- commandHandlers.persistLastProcessedCommand(sequenceId = lastExecutedCommand)
      } yield {
        result
      }

      result.onComplete {
        case Success(_) => resultHandlers.onSuccess(commands = commands.length)
        case Failure(e) => resultHandlers.onFailure(commands = commands.length, e = e)
      }
    }

  trait CommandProcessingResultHandlers {
    def onSuccess(commands: Int): Unit
    def onFailure(commands: Int, e: Throwable): Unit
  }

  object CommandProcessingResultHandlers {
    class Default(log: Logger) extends CommandProcessingResultHandlers {
      override def onSuccess(commands: Int): Unit =
        log.debug("Successfully processed [{}] command(s)", commands)

      override def onFailure(commands: Int, e: Throwable): Unit =
        log.error("Processing of [{}] command(s) failed: [{} - {}]", commands, e.getClass.getSimpleName, e.getMessage)
    }
  }
}
