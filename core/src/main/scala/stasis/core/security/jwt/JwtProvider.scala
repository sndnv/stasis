package stasis.core.security.jwt

import akka.actor.typed.scaladsl.adapter._
import akka.actor.typed.{ActorSystem, SpawnProtocol}
import akka.http.scaladsl.{Http, HttpsConnectionContext}
import akka.http.scaladsl.model.headers.BasicHttpCredentials
import akka.http.scaladsl.model.{HttpMethods, HttpRequest, HttpResponse, Uri}
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Sink
import akka.util.{ByteString, Timeout}
import play.api.libs.json.{Format, Json}
import stasis.core.persistence.backends.memory.MemoryBackend
import stasis.core.security.exceptions.ProviderFailure
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

class JwtProvider(
  tokenEndpoint: String,
  client: String,
  clientSecret: String,
  expirationTolerance: FiniteDuration,
  context: Option[HttpsConnectionContext]
)(implicit system: ActorSystem[SpawnProtocol], timeout: Timeout) {
  import JwtProvider._

  private implicit val untypedSystem: akka.actor.ActorSystem = system.toUntyped
  private implicit val mat: ActorMaterializer = ActorMaterializer()
  private implicit val ec: ExecutionContext = system.executionContext

  private val credentials: BasicHttpCredentials = BasicHttpCredentials(username = client, password = clientSecret)

  private val http = Http()

  private val clientContext: HttpsConnectionContext = context match {
    case Some(context) => context
    case None          => http.defaultClientHttpsContext
  }

  private val cache = MemoryBackend[String, AccessTokenResponse](name = "jwt-provider-cache")

  def provide(scope: String): Future[String] =
    cache.get(scope).flatMap {
      case Some(response) =>
        Future.successful(response.access_token)

      case None =>
        for {
          response <- request(scope)
          _ <- cache.put(scope, response)
        } yield {
          val _ = akka.pattern.after(
            duration = response.expires_in.seconds - expirationTolerance,
            using = system.scheduler
          )(cache.delete(scope))

          response.access_token
        }
    }

  def request(scope: String): Future[AccessTokenResponse] =
    http
      .singleRequest(
        request = HttpRequest(
          method = HttpMethods.POST,
          uri = Uri(tokenEndpoint).withQuery(Uri.Query("grant_type" -> GrantType, "scope" -> scope))
        ).addCredentials(credentials),
        connectionContext = clientContext
      )
      .recoverWith {
        case NonFatal(e) =>
          val message = s"Failed to retrieve token from [$tokenEndpoint]: [${e.getMessage}]"
          Future.failed(ProviderFailure(message))
      }
      .flatMap {
        case HttpResponse(code, _, entity, _) if code.isSuccess() =>
          import de.heikoseeberger.akkahttpplayjson.PlayJsonSupport._

          Unmarshal(entity)
            .to[AccessTokenResponse]
            .recoverWith {
              case NonFatal(e) =>
                val _ = entity.dataBytes.runWith(Sink.cancelled[ByteString])
                val message = s"Failed to unmarshal response [$code] from [$tokenEndpoint]: [${e.getMessage}]"
                Future.failed(ProviderFailure(message))
            }

        case HttpResponse(code, _, entity, _) =>
          Unmarshal(entity)
            .to[String]
            .flatMap { response =>
              val message = s"Token retrieval from [$tokenEndpoint] failed with [$code]: [$response]"
              Future.failed(ProviderFailure(message))
            }
      }
}

object JwtProvider {
  final val GrantType: String = "client_credentials"

  implicit val accessTokenResponseFormat: Format[AccessTokenResponse] = Json.format[AccessTokenResponse]

  final case class AccessTokenResponse(
    access_token: String,
    expires_in: Long,
    scope: Option[String]
  )
}
