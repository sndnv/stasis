package stasis.identity.api.oauth.directives

import akka.event.LoggingAdapter
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{Directive, Directive1}
import stasis.core.api.directives.EntityDiscardingDirectives
import stasis.identity.api.Formats._
import stasis.identity.model.apis.{Api, ApiStoreView}
import stasis.identity.model.clients.{Client, ClientStoreView}
import stasis.identity.model.errors.TokenError

import scala.concurrent.{ExecutionContext, Future}
import scala.util.matching.Regex
import scala.util.{Failure, Success, Try}

trait AudienceExtraction extends EntityDiscardingDirectives {
  import de.heikoseeberger.akkahttpplayjson.PlayJsonSupport._

  protected implicit def ec: ExecutionContext
  protected def log: LoggingAdapter

  protected def clientStore: ClientStoreView
  protected def apiStore: ApiStoreView

  private val audienceUrn: Regex = s"${AudienceExtraction.UrnPrefix}:(.+)".r

  def extractClientAudience(scopeOpt: Option[String]): Directive1[Seq[Client]] =
    Directive { inner =>
      extractAudienceFromScope(scopeOpt) { audience =>
        Try(audience.map(java.util.UUID.fromString)) match {
          case Success(clientIds) =>
            getAudienceFromStore(
              audience = clientIds,
              audienceType = "client",
              get = clientStore.get
            )(result => inner(Tuple1(result)))

          case Failure(_) =>
            log.warning(
              "One or more invalid client identifiers found in provided audience: [{}]",
              audience
            )

            discardEntity {
              complete(
                StatusCodes.BadRequest,
                TokenError.InvalidScope: TokenError
              )
            }
        }
      }
    }

  def extractApiAudience(scopeOpt: Option[String]): Directive1[Seq[Api]] =
    Directive { inner =>
      extractAudienceFromScope(scopeOpt) { apiIds =>
        getAudienceFromStore(
          audience = apiIds,
          audienceType = "API",
          get = (id: Api.Id) => apiStore.get(id)
        )(result => inner(Tuple1(result)))
      }
    }

  def clientAudienceToScope(audience: Seq[Client]): Option[String] =
    if (audience.nonEmpty) {
      Some(audience.map(client => s"${AudienceExtraction.UrnPrefix}:${client.id.toString}").mkString(" "))
    } else {
      None
    }

  def apiAudienceToScope(audience: Seq[Api]): Option[String] =
    if (audience.nonEmpty) {
      Some(audience.map(api => s"${AudienceExtraction.UrnPrefix}:${api.id}").mkString(" "))
    } else {
      None
    }

  private def extractAudienceFromScope(scopeOpt: Option[String]): Directive1[Seq[String]] =
    Directive { inner =>
      scopeOpt match {
        case Some(scope) =>
          audienceFromScope(scope) match {
            case audience @ _ :: _ =>
              inner(Tuple1(audience))

            case Nil =>
              log.warning(
                "No matching audience found in scope [{}]",
                scope
              )

              discardEntity {
                complete(
                  StatusCodes.BadRequest,
                  TokenError.InvalidScope: TokenError
                )
              }
          }

        case None =>
          inner(Tuple1(Seq.empty))
      }
    }

  private def audienceFromScope(scope: String): List[String] =
    scope
      .split(" ")
      .flatMap {
        case audienceUrn(audience) => Some(audience)
        case _                     => None
      }
      .toList

  private def getAudienceFromStore[I, A](
    audience: Seq[I],
    audienceType: String,
    get: I => Future[Option[A]]
  ): Directive1[Seq[A]] =
    Directive { inner =>
      onComplete(Future.sequence(audience.map(get(_))).map(_.flatten)) {
        case Success(result) if result.nonEmpty =>
          inner(Tuple1(result))

        case Success(_) =>
          log.warning(
            "No {} audience found with provided identifiers [{}]",
            audienceType,
            audience,
          )

          discardEntity {
            complete(
              StatusCodes.BadRequest,
              TokenError.InvalidScope: TokenError
            )
          }

        case Failure(e) =>
          log.error(
            e,
            "Failed to retrieve {} audience with provided identifiers [{}]: [{}]",
            audienceType,
            audience,
            e.getMessage
          )

          discardEntity {
            complete(
              StatusCodes.InternalServerError
            )
          }
      }
    }
}

object AudienceExtraction {
  final val UrnPrefix: String = "urn:stasis:identity:audience"
}
