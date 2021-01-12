package stasis.client.ops.scheduling

import java.nio.file.Path
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.concurrent.ThreadLocalRandom

import akka.Done
import akka.actor.typed._
import akka.actor.typed.scaladsl.AskPattern._
import akka.actor.typed.scaladsl.{Behaviors, LoggerOps, TimerScheduler}
import akka.util.Timeout
import stasis.client.api.clients.ServerApiEndpointClient
import stasis.client.ops.exceptions.ScheduleRetrievalFailure
import stasis.client.ops.scheduling.OperationScheduler.ActiveSchedule

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal
import scala.util.{Failure, Success}

class DefaultOperationScheduler private (
  schedulerRef: Future[ActorRef[DefaultOperationScheduler.Message]]
)(implicit scheduler: Scheduler, ec: ExecutionContext, timeout: Timeout)
    extends OperationScheduler {
  import DefaultOperationScheduler._

  locally { val _ = refresh() }

  override def schedules: Future[Seq[ActiveSchedule]] =
    schedulerRef.flatMap(_ ? (ref => GetSchedules(ref)))

  override def refresh(): Future[Done] =
    schedulerRef.flatMap(_ ? (ref => RefreshSchedules(ref)))

  override def stop(): Future[Done] =
    schedulerRef.flatMap(_ ? (ref => StopScheduler(ref)))
}

object DefaultOperationScheduler {
  final case class Config(
    schedulesFile: Path,
    minDelay: FiniteDuration,
    maxExtraDelay: FiniteDuration
  )

  def apply(
    config: Config,
    api: ServerApiEndpointClient,
    executor: OperationExecutor
  )(implicit system: ActorSystem[SpawnProtocol.Command], timeout: Timeout): DefaultOperationScheduler = {
    implicit val executionContext: ExecutionContext = system.executionContext
    implicit val schedulerConfig: Config = config
    implicit val apiClient: ServerApiEndpointClient = api
    implicit val operationExecutor: OperationExecutor = executor

    val behaviour = Behaviors.withTimers[Message] { timers =>
      implicit val akkaTimers: TimerScheduler[Message] = timers

      scheduler(
        activeSchedules = Seq.empty,
        activeOperations = Set.empty
      )
    }

    new DefaultOperationScheduler(
      schedulerRef = system ? (SpawnProtocol.Spawn(behaviour, name = "operation-scheduler", props = Props.empty, _))
    )
  }

  private sealed trait Message
  private final case class GetSchedules(replyTo: ActorRef[Seq[ActiveSchedule]]) extends Message
  private final case class RefreshSchedules(replyTo: ActorRef[Done]) extends Message
  private final case class StopScheduler(replyTo: ActorRef[Done]) extends Message

  private final case class UpdateActiveSchedules(schedules: Seq[ActiveSchedule]) extends Message
  private final case object SetupNextScheduleExecution extends Message
  private final case class ExecuteSchedule(assignment: OperationScheduleAssignment) extends Message
  private final case class ScheduleExecuted(assignment: OperationScheduleAssignment) extends Message

  private case object PendingScheduleKey

  private implicit val localDateTimeOrdering: Ordering[LocalDateTime] = _ compareTo _

  private def scheduler(
    activeSchedules: Seq[ActiveSchedule],
    activeOperations: Set[OperationScheduleAssignment]
  )(implicit
    ec: ExecutionContext,
    timers: TimerScheduler[DefaultOperationScheduler.Message],
    config: Config,
    api: ServerApiEndpointClient,
    executor: OperationExecutor
  ): Behavior[Message] =
    Behaviors.receive { case (ctx, message) =>
      message match {
        case GetSchedules(replyTo) =>
          ctx.log.debugN("Responding with [{}] active schedule(s)", activeSchedules.size)
          replyTo ! activeSchedules
          Behaviors.same

        case RefreshSchedules(replyTo) =>
          val log = ctx.log
          val self = ctx.self
          log.debugN("Refreshing schedules from [{}]", config.schedulesFile.toAbsolutePath)

          val _ = for {
            configuredSchedules <- SchedulingConfig.schedules(file = config.schedulesFile)
            loadedSchedules <- Future.sequence(
              configuredSchedules.map { assignment =>
                api
                  .publicSchedule(assignment.schedule)
                  .map { schedule =>
                    log.debugN(
                      "Loaded [{}] schedule for [{}]",
                      assignment.getClass.getSimpleName,
                      assignment.schedule
                    )
                    ActiveSchedule(assignment = assignment, schedule = Right(schedule))
                  }
                  .recover { case NonFatal(e) =>
                    val operation = assignment.getClass.getSimpleName
                    val schedule = assignment.schedule.toString
                    val message = s"Failed to load [$operation] schedule for [$schedule]: [${e.getMessage}]"
                    log.errorN(message, e)
                    ActiveSchedule(assignment = assignment, schedule = Left(ScheduleRetrievalFailure(message)))
                  }
              }
            )
          } yield {
            self ! UpdateActiveSchedules(loadedSchedules)
            replyTo ! Done
          }

          Behaviors.same

        case UpdateActiveSchedules(schedules) =>
          ctx.log.debugN("Loaded [{}] schedule(s)", schedules.size)
          ctx.self ! SetupNextScheduleExecution
          scheduler(activeSchedules = schedules, activeOperations = activeOperations)

        case SetupNextScheduleExecution =>
          val next = activeSchedules
            .collect { case ActiveSchedule(assignment, Right(schedule)) => (assignment, schedule.nextInvocation) }
            .sortBy(_._2)
            .headOption

          timers.cancel(key = PendingScheduleKey)

          next match {
            case Some((assignment, nextInvocation)) =>
              val executionDelay = math.max(
                LocalDateTime.now().until(nextInvocation, ChronoUnit.MILLIS),
                config.minDelay.toMillis
              )

              val randomDelay = ThreadLocalRandom.current().nextLong(0, config.maxExtraDelay.toMillis)

              val actualDelay = (executionDelay + randomDelay).millis

              ctx.log.debugN(
                "Scheduling execution of [{}] in [{}] second(s)",
                assignment.schedule,
                actualDelay.toSeconds
              )

              timers.startSingleTimer(key = PendingScheduleKey, msg = ExecuteSchedule(assignment), delay = actualDelay)

              scheduler(activeSchedules = activeSchedules, activeOperations = activeOperations)

            case None =>
              ctx.log.warnN("No active schedules found")
              Behaviors.same
          }

        case ExecuteSchedule(assignment) if activeOperations.contains(assignment) =>
          ctx.log.warnN(
            "[{}] operation with schedule [{}] is already running; execution skipped",
            assignment.getClass.getSimpleName,
            assignment.schedule
          )
          Behaviors.same

        case ExecuteSchedule(assignment) =>
          val log = ctx.log
          val self = ctx.self

          log.debugN(
            "Starting [{}] operation with schedule [{}]",
            assignment.getClass.getSimpleName,
            assignment.schedule
          )

          val result = assignment match {
            case OperationScheduleAssignment.Backup(_, definition, entities) if entities.nonEmpty =>
              executor.startBackupWithEntities(definition, entities)

            case OperationScheduleAssignment.Backup(_, definition, _) =>
              executor.startBackupWithRules(definition)

            case OperationScheduleAssignment.Expiration(_) =>
              executor.startExpiration()

            case OperationScheduleAssignment.Validation(_) =>
              executor.startValidation()

            case OperationScheduleAssignment.KeyRotation(_) =>
              executor.startKeyRotation()
          }

          result.onComplete {
            case Success(operation) =>
              log.debugN(
                "[{}] operation [{}] with schedule [{}] completed successfully",
                assignment.getClass.getSimpleName,
                operation,
                assignment.schedule
              )
              self ! ScheduleExecuted(assignment)

            case Failure(e) =>
              log.errorN(
                "[{}] operation with schedule [{}] failed: [{}]",
                assignment.getClass.getSimpleName,
                assignment.schedule,
                e.getMessage,
                e
              )
              self ! ScheduleExecuted(assignment)
          }

          scheduler(activeSchedules = activeSchedules, activeOperations = activeOperations + assignment)

        case ScheduleExecuted(assignment) =>
          ctx.self ! SetupNextScheduleExecution
          scheduler(activeSchedules = activeSchedules, activeOperations = activeOperations - assignment)

        case StopScheduler(replyTo) =>
          ctx.log.debugN("Stopping scheduler")
          replyTo ! Done
          Behaviors.stopped
      }
    }
}
