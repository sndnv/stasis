package stasis.core.discovery.http

import scala.concurrent.Future
import scala.util.control.NonFatal

import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.http.scaladsl.marshalling.Marshal
import org.apache.pekko.http.scaladsl.model._
import org.apache.pekko.http.scaladsl.model.headers.HttpCredentials
import org.apache.pekko.http.scaladsl.unmarshalling.Unmarshal
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import play.api.libs.json.Format

import stasis.core.api.PoolClient
import stasis.core.discovery.ServiceDiscoveryClient
import stasis.core.discovery.ServiceDiscoveryResult
import stasis.core.discovery.exceptions.DiscoveryFailure
import stasis.layers.security.tls.EndpointContext
import stasis.layers.streaming.Operators.ExtendedSource

class HttpServiceDiscoveryClient(
  apiUrl: String,
  credentials: => Future[HttpCredentials],
  override val attributes: ServiceDiscoveryClient.Attributes,
  override protected val context: Option[EndpointContext],
  override protected val config: PoolClient.Config
)(
  override protected implicit val system: ActorSystem[Nothing]
) extends ServiceDiscoveryClient
    with PoolClient {
  import system.executionContext

  import HttpServiceDiscoveryClient._
  import stasis.core.api.Formats.serviceDiscoveryRequestFormat
  import stasis.core.api.Formats.serviceDiscoveryResultFormat

  override protected val log: Logger = LoggerFactory.getLogger(this.getClass.getName)

  override def latest(isInitialRequest: Boolean): Future[ServiceDiscoveryResult] = {
    import com.github.pjfanning.pekkohttpplayjson.PlayJsonSupport._

    for {
      credentials <- credentials
      entity <- Marshal(attributes.asServiceDiscoveryRequest(isInitialRequest)).to[RequestEntity]
      response <- offer(
        request = HttpRequest(
          method = HttpMethods.POST,
          uri = s"$apiUrl/v1/discovery/provide",
          entity = entity
        ).addCredentials(credentials = credentials)
      )
      result <- response.to[ServiceDiscoveryResult]
    } yield {
      result
    }
  }
}

object HttpServiceDiscoveryClient {
  def apply(
    apiUrl: String,
    credentials: => Future[HttpCredentials],
    attributes: ServiceDiscoveryClient.Attributes,
    context: Option[EndpointContext]
  )(implicit system: ActorSystem[Nothing]): HttpServiceDiscoveryClient =
    HttpServiceDiscoveryClient(
      apiUrl = apiUrl,
      credentials = credentials,
      attributes = attributes,
      context = context,
      config = PoolClient.Config.Default
    )

  def apply(
    apiUrl: String,
    credentials: => Future[HttpCredentials],
    attributes: ServiceDiscoveryClient.Attributes,
    context: Option[EndpointContext],
    config: PoolClient.Config
  )(implicit system: ActorSystem[Nothing]): HttpServiceDiscoveryClient =
    new HttpServiceDiscoveryClient(
      apiUrl = apiUrl,
      credentials = credentials,
      attributes = attributes,
      context = context,
      config = config
    )

  implicit class ExtendedResponseEntity(response: HttpResponse) {
    def to[M](implicit format: Format[M], system: ActorSystem[Nothing]): Future[M] = {
      import system.executionContext

      if (response.status.isSuccess()) {
        import com.github.pjfanning.pekkohttpplayjson.PlayJsonSupport._
        Unmarshal(response)
          .to[M]
          .recoverWith { case NonFatal(e) =>
            response.entity.dataBytes.cancelled().flatMap { _ =>
              Future.failed(
                new DiscoveryFailure(
                  message = s"Discovery API request unmarshalling failed with: [${e.getMessage}]"
                )
              )
            }
          }
      } else {
        Unmarshal(response)
          .to[String]
          .flatMap { responseContent =>
            Future.failed(
              new DiscoveryFailure(
                message = s"Discovery API request failed with [${response.status.value}]: [$responseContent]"
              )
            )
          }
      }
    }
  }
}
