package stasis.client.api.http.routes

import java.nio.file.Path

import akka.actor.typed.scaladsl.LoggerOps
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.sse.ServerSentEvent
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.unmarshalling.Unmarshaller
import akka.stream.Materializer
import play.api.libs.json.{Format, Json}
import stasis.client.api.http.Context
import stasis.client.api.http.Formats._
import stasis.client.collection.rules.{Rule, Specification}
import stasis.client.ops.recovery.Recovery
import stasis.shared.api.Formats._
import stasis.shared.ops.Operation

import scala.concurrent.duration._

class Operations()(implicit override val mat: Materializer, context: Context) extends ApiRoutes {
  import Operations._
  import akka.http.scaladsl.marshalling.sse.EventStreamMarshalling._
  import de.heikoseeberger.akkahttpplayjson.PlayJsonSupport._
  import mat.executionContext
  import stasis.core.api.Matchers._

  def routes(): Route =
    concat(
      pathEndOrSingleSlash {
        get {
          val operationsState = for {
            operations <- context.executor.operations
            progress <- context.tracker.state.map(_.operations)
          } yield {
            operations.map { case (operation, operationType) =>
              val operationProgress = progress.getOrElse(
                operation,
                Operation.Progress.empty
              )

              OperationState(
                operation = operation,
                `type` = operationType,
                progress = operationProgress
              )
            }
          }

          onSuccess(operationsState) { operations =>
            log.debugN("API successfully retrieved state of [{}] operations", operations.size)
            discardEntity & complete(operations)
          }
        }
      },
      pathPrefix("backup") {
        concat(
          path("rules") {
            get {
              onSuccess(context.executor.rules) { rules =>
                log.debugN("API successfully retrieved backup rules specification")
                discardEntity & complete(StatusCodes.OK, SpecificationRules(rules))
              }
            }
          },
          path(JavaUUID) { definition =>
            put {
              onSuccess(context.executor.startBackupWithRules(definition)) { operation =>
                log.debugN("API started backup operation [{}]", operation)
                discardEntity & complete(StatusCodes.Accepted, OperationStarted(operation))
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
                    discardEntity & complete(StatusCodes.Accepted, OperationStarted(operation))
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
                    discardEntity & complete(StatusCodes.Accepted, OperationStarted(operation))
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
                    discardEntity & complete(StatusCodes.Accepted, OperationStarted(operation))
                  }
                }
              )
            }
          }
        }
      },
      pathPrefix(JavaUUID) { operation =>
        concat(
          path("progress") {
            get {
              onSuccess(context.tracker.state.map(_.operations.get(operation))) {
                case Some(operation) =>
                  log.debugN("API successfully retrieved progress of operation [{}]", operation)
                  discardEntity & complete(operation)

                case None =>
                  log.debugN(
                    "API could not retrieve progress of operation [{}]; operation not found",
                    operation
                  )
                  discardEntity & complete(StatusCodes.NotFound)
              }
            }
          },
          path("follow") {
            parameter("heartbeat".as[FiniteDuration].?(default = DefaultSseHeartbeatInterval)) { heartbeatInterval =>
              get {
                val sseSource = context.tracker
                  .operationUpdates(operation)
                  .map(update => Json.toJson(update).toString)
                  .map(update => ServerSentEvent.apply(update))
                  .keepAlive(maxIdle = heartbeatInterval, injectedElem = () => ServerSentEvent.heartbeat)

                log.debugN("API successfully retrieved progress stream for operation [{}]", operation)
                discardEntity & complete(sseSource)
              }
            }
          },
          path("stop") {
            put {
              onSuccess(context.executor.stop(operation)) { _ =>
                log.debugN("API stopped backup operation [{}]", operation)
                discardEntity & complete(StatusCodes.OK)
              }
            }
          }
        )
      }
    )
}

object Operations {
  import akka.http.scaladsl.server.{Directive, Directive1}

  def apply()(implicit mat: Materializer, context: Context): Operations =
    new Operations()

  final val DefaultSseHeartbeatInterval: FiniteDuration = 3.seconds

  final case class OperationStarted(operation: Operation.Id)

  final case class OperationState(operation: Operation.Id, `type`: Operation.Type, progress: Operation.Progress)

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

  implicit val operationStateFormat: Format[OperationState] =
    Json.format[OperationState]

  implicit val specificationRulesFormat: Format[SpecificationRules] =
    Json.format[SpecificationRules]

  implicit val stringToRegexQuery: Unmarshaller[String, Recovery.PathQuery] =
    Unmarshaller.strict(Recovery.PathQuery.apply)

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
