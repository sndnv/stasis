package stasis.client.api.http.routes

import org.apache.pekko.NotUsed
import org.apache.pekko.actor.typed.scaladsl.LoggerOps
import org.apache.pekko.http.scaladsl.model.StatusCodes
import org.apache.pekko.http.scaladsl.model.sse.ServerSentEvent
import org.apache.pekko.http.scaladsl.server.Directives._
import org.apache.pekko.http.scaladsl.server.Route
import org.apache.pekko.http.scaladsl.unmarshalling.Unmarshaller
import org.apache.pekko.stream.OverflowStrategy
import org.apache.pekko.stream.scaladsl.Source
import play.api.libs.json.{Format, Json, Writes}
import stasis.client.api.Context
import stasis.client.api.http.Formats._
import stasis.client.collection.rules.{Rule, Specification}
import stasis.client.ops.recovery.Recovery
import stasis.client.tracking.state.{BackupState, OperationState, RecoveryState}
import stasis.shared.api.Formats._
import stasis.shared.ops.Operation

import java.nio.file.Path
import scala.concurrent.Future
import scala.concurrent.duration._

class Operations()(implicit context: Context) extends ApiRoutes {
  import Operations._
  import org.apache.pekko.http.scaladsl.marshalling.sse.EventStreamMarshalling._
  import com.github.pjfanning.pekkohttpplayjson.PlayJsonSupport._
  import stasis.core.api.Matchers._

  def routes(): Route =
    concat(
      pathEndOrSingleSlash {
        get {
          parameter("state".as[State].?[State](default = State.Active)) { state =>
            extractExecutionContext { implicit ec =>
              val result = for {
                backups <- context.trackers.backup.state
                recoveries <- context.trackers.recovery.state
                all = (backups: Map[Operation.Id, OperationState]) ++ (recoveries: Map[Operation.Id, OperationState])
                active <- context.executor.active
              } yield {
                val collected = state match {
                  case State.Active    => all.filter(e => active.contains(e._1))
                  case State.Completed => all.filter(_._2.isCompleted)
                  case State.All       => all
                }

                collected.map { case (operation, operationState) =>
                  OperationProgress(
                    operation = operation,
                    isActive = active.contains(operation),
                    `type` = operationState.`type`,
                    progress = operationState.asProgress
                  )
                }
              }

              onSuccess(result) { operations =>
                log.debugN("API successfully retrieved progress of [{}] operations", operations.size)
                consumeEntity & complete(operations)
              }
            }
          }
        }
      },
      pathPrefix("backup") {
        concat(
          path("rules") {
            get {
              onSuccess(context.executor.rules) { rules =>
                log.debugN("API successfully retrieved backup rules specification")
                consumeEntity & complete(SpecificationRules(rules))
              }
            }
          },
          path(JavaUUID) { definition =>
            put {
              onSuccess(context.executor.startBackupWithRules(definition)) { operation =>
                log.debugN("API started backup operation [{}]", operation)
                consumeEntity & complete(StatusCodes.Accepted, OperationStarted(operation))
              }
            }
          }
        )
      },
      pathPrefix("recover" / JavaUUID) { definition =>
        put {
          parameter("query".as[Recovery.PathQuery].?) { query =>
            extractDestination(destinationParam = "destination", keepStructureParam = "keep_structure") { destination =>
              concat(
                path("latest") {
                  val result = context.executor.startRecoveryWithDefinition(
                    definition = definition,
                    until = None,
                    query = query,
                    destination = destination
                  )

                  onSuccess(result) { operation =>
                    log.debugN(
                      "API started recovery operation [{}] for definition [{}] with latest entry",
                      operation,
                      definition
                    )
                    consumeEntity & complete(StatusCodes.Accepted, OperationStarted(operation))
                  }

                },
                path("until" / IsoInstant) { until =>
                  val result = context.executor.startRecoveryWithDefinition(
                    definition = definition,
                    until = Some(until),
                    query = query,
                    destination = destination
                  )

                  onSuccess(result) { operation =>
                    log.debugN(
                      "API started recovery operation [{}] for definition [{}] until [{}]",
                      operation,
                      definition,
                      until
                    )
                    consumeEntity & complete(StatusCodes.Accepted, OperationStarted(operation))
                  }

                },
                path("from" / JavaUUID) { entry =>
                  val result = context.executor.startRecoveryWithEntry(
                    entry = entry,
                    query = query,
                    destination = destination
                  )

                  onSuccess(result) { operation =>
                    log.debugN(
                      "API started recovery operation [{}] for definition [{}] with entry [{}]",
                      operation,
                      definition,
                      entry
                    )
                    consumeEntity & complete(StatusCodes.Accepted, OperationStarted(operation))
                  }
                }
              )
            }
          }
        }
      },
      pathPrefix(JavaUUID) { operation =>
        concat(
          pathEndOrSingleSlash {
            delete {
              onSuccess(context.executor.active) { active =>
                if (active.contains(operation)) {
                  log.debugN("API could not remove operation [{}]; operation is active", operation)
                  consumeEntity & complete(StatusCodes.Conflict)
                } else {
                  log.debugN("API removed operation [{}]", operation)
                  context.trackers.backup.remove(operation)
                  context.trackers.recovery.remove(operation)
                  consumeEntity & complete(StatusCodes.Accepted)
                }
              }
            }
          },
          path("progress") {
            get {
              extractExecutionContext { implicit ec =>
                val result: Future[Option[OperationState]] = context.trackers.backup.state.flatMap { backups =>
                  backups.get(operation) match {
                    case Some(state) => Future.successful(Some(state))
                    case None        => context.trackers.recovery.state.map(_.get(operation))
                  }
                }

                onSuccess(result) {
                  case Some(backup: BackupState) =>
                    log.debugN("API successfully retrieved progress of backup operation [{}]", operation)
                    consumeEntity & complete(backup)

                  case Some(recovery: RecoveryState) =>
                    log.debugN("API successfully retrieved progress of recovery operation [{}]", operation)
                    consumeEntity & complete(recovery)

                  case _ =>
                    log.debugN("API could not retrieve progress of operation [{}]; unexpected or missing operation", operation)
                    consumeEntity & complete(StatusCodes.NotFound)
                }
              }
            }
          },
          path("follow") {
            parameters(
              "heartbeat".as[FiniteDuration].?(default = DefaultSseHeartbeatInterval),
              "throttle_events_per_interval".as[Int].?(default = DefaultSseThrottleEventsPerInterval),
              "throttle_interval".as[FiniteDuration].?(default = DefaultSseThrottleInterval)
            ) { case (heartbeatInterval, eventsPerInterval, eventsInterval) =>
              get {
                extractExecutionContext { implicit ec =>
                  def sseSource[T](source: Source[T, NotUsed])(implicit format: Writes[T]): Source[ServerSentEvent, NotUsed] =
                    source
                      .buffer(size = 1, overflowStrategy = OverflowStrategy.dropHead)
                      .throttle(elements = math.max(eventsPerInterval, DefaultSseThrottleEventsPerInterval), per = eventsInterval)
                      .map(update => Json.toJson(update).toString)
                      .map(update => ServerSentEvent.apply(update))
                      .keepAlive(maxIdle = heartbeatInterval, injectedElem = () => ServerSentEvent.heartbeat)

                  def backupUpdates: Future[Source[ServerSentEvent, NotUsed]] = for {
                    isBackup <- context.trackers.backup.exists(operation) if isBackup
                  } yield {
                    log.debugN("API successfully retrieved progress stream for backup operation [{}]", operation)
                    sseSource(context.trackers.backup.updates(operation))
                  }

                  def recoveryUpdates: Future[Source[ServerSentEvent, NotUsed]] = for {
                    isRecovery <- context.trackers.recovery.exists(operation) if isRecovery
                  } yield {
                    log.debugN("API successfully retrieved progress stream for recovery operation [{}]", operation)
                    sseSource(context.trackers.recovery.updates(operation))
                  }

                  val updates = backupUpdates.map(Option.apply).recoverWith { case _: NoSuchElementException =>
                    recoveryUpdates.map(Option.apply).recover { case _: NoSuchElementException =>
                      log.debugN(
                        "API could not retrieve progress stream of operation [{}]; operation not found",
                        operation
                      )
                      None
                    }
                  }

                  onSuccess(updates) {
                    case Some(source) => consumeEntity & complete(source)
                    case None         => consumeEntity & complete(StatusCodes.NotFound)
                  }
                }
              }
            }
          },
          path("stop") {
            put {
              onSuccess(context.executor.stop(operation)) {
                case Some(_) =>
                  log.debugN("API stopped operation [{}]", operation)
                  consumeEntity & complete(StatusCodes.NoContent)

                case None =>
                  log.debugN("API failed to stop operation [{}]; operation not found", operation)
                  consumeEntity & complete(StatusCodes.NotFound)
              }
            }
          },
          path("resume") {
            put {
              onSuccess(context.executor.resumeBackup(operation)) { _ =>
                log.debugN("API resumed backup operation [{}]", operation)
                consumeEntity & complete(StatusCodes.Accepted, OperationStarted(operation))
              }
            }
          }
        )
      }
    )
}

object Operations {
  import org.apache.pekko.http.scaladsl.server.{Directive, Directive1}

  def apply()(implicit context: Context): Operations =
    new Operations()

  final val DefaultSseHeartbeatInterval: FiniteDuration = 3.seconds
  final val DefaultSseThrottleEventsPerInterval: Int = 1
  final val DefaultSseThrottleInterval: FiniteDuration = 2.seconds

  sealed trait State
  object State {
    @SuppressWarnings(Array("org.wartremover.warts.Throw"))
    def apply(state: String): State =
      state.trim.toLowerCase match {
        case "active"    => State.Active
        case "completed" => State.Completed
        case "all"       => State.All
        case other       => throw new IllegalArgumentException(s"Expected operation state but [$other] provided")
      }

    case object Active extends State
    case object Completed extends State
    case object All extends State
  }

  final case class OperationStarted(operation: Operation.Id)

  final case class OperationProgress(
    operation: Operation.Id,
    isActive: Boolean,
    `type`: Operation.Type,
    progress: Operation.Progress
  )

  final case class SpecificationRules(
    included: Seq[Path],
    excluded: Seq[Path],
    explanation: Map[Path, Seq[Specification.Entry.Explanation]],
    unmatched: Seq[(Rule.Original, String)]
  )

  object SpecificationRules {
    def apply(specification: Specification): SpecificationRules =
      new SpecificationRules(
        included = specification.included,
        excluded = specification.excluded,
        explanation = specification.explanation,
        unmatched = specification.unmatched.map { case (rule, reason) =>
          (rule.original, s"${reason.getClass.getSimpleName}: ${reason.getMessage}")
        }
      )
  }

  implicit val operationStartedFormat: Format[OperationStarted] =
    Json.format[OperationStarted]

  implicit val fullOperationProgressFormat: Format[OperationProgress] =
    Json.format[OperationProgress]

  implicit val specificationRulesFormat: Format[SpecificationRules] =
    Json.format[SpecificationRules]

  implicit val stringToRegexQuery: Unmarshaller[String, Recovery.PathQuery] =
    Unmarshaller.strict(Recovery.PathQuery.apply)

  implicit val stringToOperationState: Unmarshaller[String, State] =
    Unmarshaller.strict(State.apply)

  def extractDestination(
    destinationParam: String,
    keepStructureParam: String
  ): Directive1[Option[Recovery.Destination]] =
    Directive { inner =>
      parameters(destinationParam.as[String].?, keepStructureParam.as[Boolean].?) {
        case (Some(destination), keepStructure) =>
          val recoveryDestination = Recovery.Destination(
            path = destination,
            keepStructure = keepStructure.getOrElse(true)
          )
          inner(Tuple1(Some(recoveryDestination)))

        case (None, _) =>
          inner(Tuple1(None))
      }
    }
}
