package stasis.layers.security.oauth

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.util.control.NonFatal

import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.http.scaladsl.model._
import org.apache.pekko.http.scaladsl.model.headers.BasicHttpCredentials
import org.apache.pekko.http.scaladsl.unmarshalling.Unmarshal
import org.apache.pekko.http.scaladsl.Http
import org.apache.pekko.http.scaladsl.HttpsConnectionContext
import OAuthClient.AccessTokenResponse
import OAuthClient.GrantType
import stasis.layers.security.Metrics
import stasis.layers.security.exceptions.ProviderFailure
import stasis.layers.security.tls.EndpointContext
import stasis.layers.streaming.Operators.ExtendedSource
import stasis.layers.telemetry.TelemetryContext

class DefaultOAuthClient(
  override val tokenEndpoint: String,
  client: String,
  clientSecret: String,
  useQueryString: Boolean,
  context: Option[EndpointContext]
)(implicit system: ActorSystem[Nothing], telemetry: TelemetryContext)
    extends OAuthClient {

  private implicit val ec: ExecutionContext = system.executionContext

  private val metrics = telemetry.metrics[Metrics.OAuthClient]

  private val credentials: BasicHttpCredentials = BasicHttpCredentials(username = client, password = clientSecret)

  private val http = Http()

  private val clientContext: HttpsConnectionContext = context match {
    case Some(context) => context.connection
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
      .map { result =>
        metrics.recordToken(endpoint = tokenEndpoint, grantType = grantParams("grant_type"))
        result
      }
  }

  private def processRequest(request: HttpRequest): Future[AccessTokenResponse] =
    http
      .singleRequest(
        request = request.addCredentials(credentials),
        connectionContext = clientContext
      )
      .recoverWith { case NonFatal(e) =>
        val message = s"Failed to retrieve token from [$tokenEndpoint]: [${e.getClass.getSimpleName}: ${e.getMessage}]"
        Future.failed(ProviderFailure(message))
      }
      .flatMap {
        case HttpResponse(code, _, entity, _) if code.isSuccess() =>
          import com.github.pjfanning.pekkohttpplayjson.PlayJsonSupport._

          Unmarshal(entity)
            .to[AccessTokenResponse]
            .recoverWith { case NonFatal(e) =>
              val message = s"Failed to unmarshal response [${code.value}] from [$tokenEndpoint]: " +
                s"[${e.getClass.getSimpleName}: ${e.getMessage}]"
              entity.dataBytes.cancelled().flatMap { _ =>
                Future.failed(ProviderFailure(message))
              }
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

object DefaultOAuthClient {
  def apply(
    tokenEndpoint: String,
    client: String,
    clientSecret: String,
    useQueryString: Boolean,
    context: Option[EndpointContext]
  )(implicit system: ActorSystem[Nothing], telemetry: TelemetryContext): DefaultOAuthClient =
    new DefaultOAuthClient(
      tokenEndpoint = tokenEndpoint,
      client = client,
      clientSecret = clientSecret,
      useQueryString = useQueryString,
      context = context
    )
}
