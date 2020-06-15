package stasis.core.security.oauth

import akka.actor.typed.{ActorSystem, SpawnProtocol}
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.BasicHttpCredentials
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.http.scaladsl.{Http, HttpsConnectionContext}
import akka.stream.scaladsl.Sink
import akka.util.ByteString
import stasis.core.security.exceptions.ProviderFailure
import stasis.core.security.oauth.OAuthClient.{AccessTokenResponse, GrantType}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

class DefaultOAuthClient(
  tokenEndpoint: String,
  client: String,
  clientSecret: String,
  useQueryString: Boolean,
  context: Option[HttpsConnectionContext]
)(implicit system: ActorSystem[SpawnProtocol.Command])
    extends OAuthClient {

  private implicit val ec: ExecutionContext = system.executionContext

  private val credentials: BasicHttpCredentials = BasicHttpCredentials(username = client, password = clientSecret)

  private val http = Http()(system.classicSystem)

  private val clientContext: HttpsConnectionContext = context match {
    case Some(context) => context
    case None          => http.defaultClientHttpsContext
  }

  override def token(
    scope: Option[String],
    parameters: OAuthClient.GrantParameters
  ): Future[AccessTokenResponse] = {
    val scopeParams = scope match {
      case Some(scope) => Map("scope" -> scope)
      case None        => Map.empty
    }

    val grantParams = parameters match {
      case OAuthClient.GrantParameters.ClientCredentials() =>
        Map("grant_type" -> GrantType.ClientCredentials)

      case OAuthClient.GrantParameters.ResourceOwnerPasswordCredentials(username, password) =>
        Map(
          "grant_type" -> GrantType.ResourceOwnerPasswordCredentials,
          "username" -> username,
          "password" -> password
        )

      case OAuthClient.GrantParameters.RefreshToken(refreshToken) =>
        Map(
          "grant_type" -> GrantType.RefreshToken,
          "refresh_token" -> refreshToken
        )
    }

    val request =
      if (useQueryString) {
        HttpRequest(
          method = HttpMethods.POST,
          uri = Uri(tokenEndpoint).withQuery(Uri.Query((scopeParams ++ grantParams).toMap))
        )
      } else {
        HttpRequest(
          method = HttpMethods.POST,
          uri = Uri(tokenEndpoint),
          entity = FormData((scopeParams ++ grantParams).toMap).toEntity
        )
      }

    processRequest(request)
  }

  private def processRequest(request: HttpRequest): Future[AccessTokenResponse] =
    http
      .singleRequest(
        request = request.addCredentials(credentials),
        connectionContext = clientContext
      )
      .recoverWith {
        case NonFatal(e) =>
          val message = s"Failed to retrieve token from [$tokenEndpoint]: [${e.getClass.getSimpleName}: ${e.getMessage}]"
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
                val message = s"Failed to unmarshal response [${code.value}] from [$tokenEndpoint]: " +
                  s"[${e.getClass.getSimpleName}: ${e.getMessage}]"
                Future.failed(ProviderFailure(message))
            }

        case HttpResponse(code, _, entity, _) =>
          Unmarshal(entity)
            .to[String]
            .flatMap { response =>
              val message = s"Token retrieval from [$tokenEndpoint] failed with [${code.value}]: [$response]"
              Future.failed(ProviderFailure(message))
            }
      }
}
