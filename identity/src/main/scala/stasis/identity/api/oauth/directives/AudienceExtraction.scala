package stasis.identity.api.oauth.directives

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.util.Failure
import scala.util.Success
import scala.util.Try
import scala.util.matching.Regex

import org.apache.pekko.actor.typed.scaladsl.LoggerOps
import org.apache.pekko.http.scaladsl.model.StatusCodes
import org.apache.pekko.http.scaladsl.server.Directive
import org.apache.pekko.http.scaladsl.server.Directive1
import org.apache.pekko.http.scaladsl.server.Directives._
import org.slf4j.Logger

import stasis.identity.api.Formats._
import stasis.identity.model.apis.Api
import stasis.identity.model.clients.Client
import stasis.identity.model.errors.TokenError
import stasis.identity.persistence.apis.ApiStore
import stasis.identity.persistence.clients.ClientStore
import io.github.sndnv.layers.api.directives.EntityDiscardingDirectives

trait AudienceExtraction extends EntityDiscardingDirectives {
  import com.github.pjfanning.pekkohttpplayjson.PlayJsonSupport._

  protected implicit def ec: ExecutionContext
  protected def log: Logger

  protected def clientStore: ClientStore.View
  protected def apiStore: ApiStore.View

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
            log.warnN(
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
              log.warnN(
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
          log.warnN(
            "No {} audience found with provided identifiers [{}]",
            audienceType,
            audience.mkString(", ")
          )

          discardEntity {
            complete(
              StatusCodes.BadRequest,
              TokenError.InvalidScope: TokenError
            )
          }

        case Failure(e) =>
          log.errorN(
            "Failed to retrieve {} audience with provided identifiers [{}]: [{} - {}]",
            audienceType,
            audience.mkString(", "),
            e.getClass.getSimpleName,
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
