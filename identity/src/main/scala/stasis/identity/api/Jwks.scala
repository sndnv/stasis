package stasis.identity.api

import akka.actor.ActorSystem
import akka.event.{Logging, LoggingAdapter}
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server._
import akka.stream.Materializer
import org.jose4j.jwk.JsonWebKey
import stasis.core.api.directives.EntityDiscardingDirectives

class Jwks(keys: Seq[JsonWebKey])(implicit system: ActorSystem, override val mat: Materializer)
    extends EntityDiscardingDirectives {
  import Jwks._
  import de.heikoseeberger.akkahttpplayjson.PlayJsonSupport._

  private val log: LoggingAdapter = Logging(system, this.getClass.getName)

  def routes: Route =
    path("jwks.json") {
      get {
        extractClientIP { remoteAddress =>
          log.debug("Successfully provided [{}] JWKs to [{}]", keys.size, remoteAddress)

          discardEntity {
            complete(
              StatusCodes.OK,
              JwksResponse(keys = keys)
            )
          }
        }
      }
    }
}

object Jwks {
  import play.api.libs.json._

  def apply(keys: Seq[JsonWebKey])(implicit system: ActorSystem, mat: Materializer): Jwks =
    new Jwks(
      keys = keys
    )

  implicit val jwkFormat: Writes[JsonWebKey] = Writes[JsonWebKey](jwk => Json.parse(jwk.toJson))
  implicit val jwksResponseFormat: Writes[JwksResponse] = Json.writes[JwksResponse]

  final case class JwksResponse(keys: Seq[JsonWebKey])
}
