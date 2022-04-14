package stasis.identity.api

import akka.actor.typed.scaladsl.LoggerOps
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server._
import org.jose4j.jwk.JsonWebKey
import org.slf4j.{Logger, LoggerFactory}
import stasis.core.api.directives.EntityDiscardingDirectives

class Jwks(keys: Seq[JsonWebKey]) extends EntityDiscardingDirectives {
  import Jwks._
  import de.heikoseeberger.akkahttpplayjson.PlayJsonSupport._

  private val log: Logger = LoggerFactory.getLogger(this.getClass.getName)

  def routes: Route =
    path("jwks.json") {
      get {
        extractClientIP { remoteAddress =>
          log.debugN("Successfully provided [{}] JWKs to [{}]", keys.size, remoteAddress)

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

  def apply(keys: Seq[JsonWebKey]): Jwks = new Jwks(keys = keys)

  implicit val jwkFormat: Writes[JsonWebKey] = Writes[JsonWebKey](jwk => Json.parse(jwk.toJson))
  implicit val jwksResponseFormat: Writes[JwksResponse] = Json.writes[JwksResponse]

  final case class JwksResponse(keys: Seq[JsonWebKey])
}
