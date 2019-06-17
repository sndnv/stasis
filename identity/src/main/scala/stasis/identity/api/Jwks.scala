package stasis.identity.api

import akka.actor.ActorSystem
import akka.event.{Logging, LoggingAdapter}
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server._
import org.jose4j.jwk.JsonWebKey

class Jwks(keys: Seq[JsonWebKey])(implicit system: ActorSystem) {
  import Jwks._
  import de.heikoseeberger.akkahttpplayjson.PlayJsonSupport._

  private val log: LoggingAdapter = Logging(system, this.getClass.getName)

  def routes: Route =
    path("jwks.json") {
      get {
        extractClientIP { remoteAddress =>
          log.debug("Successfully provided [{}] JWKs to [{}]", keys.size, remoteAddress)

          complete(
            StatusCodes.OK,
            JwksResponse(keys = keys)
          )
        }
      }
    }
}

object Jwks {
  import play.api.libs.json._

  implicit val jwksResponseFormat: Writes[JwksResponse] = Writes[JwksResponse] { jwks =>
    Json.obj(
      "keys" -> JsArray(jwks.keys.map(jwk => JsString(jwk.toJson)))
    )
  }

  final case class JwksResponse(keys: Seq[JsonWebKey])
}
