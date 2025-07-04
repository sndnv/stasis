package stasis.client.api.clients

import java.security.SecureRandom

import javax.net.ssl.SSLContext

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.util.control.NonFatal

import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.http.scaladsl.ConnectionContext
import org.apache.pekko.http.scaladsl.Http
import org.apache.pekko.http.scaladsl.model.HttpMethods
import org.apache.pekko.http.scaladsl.model.HttpRequest
import org.apache.pekko.http.scaladsl.model.HttpResponse
import org.apache.pekko.http.scaladsl.model.headers.OAuth2BearerToken
import org.apache.pekko.http.scaladsl.unmarshalling.Unmarshal
import play.api.libs.json.Format

import stasis.client.api.clients.exceptions.ServerBootstrapFailure
import stasis.client.api.clients.internal.InsecureX509TrustManager
import io.github.sndnv.layers.streaming.Operators.ExtendedSource
import stasis.shared.model.devices.DeviceBootstrapParameters

class DefaultServerBootstrapEndpointClient(
  serverBootstrapUrl: String,
  acceptSelfSignedCertificates: Boolean
)(implicit system: ActorSystem[Nothing])
    extends ServerBootstrapEndpointClient {
  import DefaultServerBootstrapEndpointClient._
  import stasis.shared.api.Formats._

  override val server: String = serverBootstrapUrl

  private val http = Http()

  @SuppressWarnings(Array("org.wartremover.warts.Null"))
  private val clientContext = if (acceptSelfSignedCertificates) {
    val sslContext = SSLContext.getInstance(DefaultSslProtocol)
    sslContext.init(
      None.orNull, // km
      Array(InsecureX509TrustManager()), // tm
      new SecureRandom() // random
    )

    ConnectionContext.httpsClient(sslContext)
  } else {
    http.defaultClientHttpsContext
  }

  override def execute(bootstrapCode: String): Future[DeviceBootstrapParameters] = {
    implicit val ec: ExecutionContext = system.executionContext

    for {
      response <- http.singleRequest(
        request = HttpRequest(
          method = HttpMethods.PUT,
          uri = s"$serverBootstrapUrl/v1/devices/execute"
        ).addCredentials(credentials = OAuth2BearerToken(token = bootstrapCode)),
        connectionContext = clientContext
      )
      params <- response.to[DeviceBootstrapParameters]
    } yield {
      params
    }
  }
}

object DefaultServerBootstrapEndpointClient {
  final val DefaultSslProtocol: String = "TLS"

  def apply(
    serverBootstrapUrl: String,
    acceptSelfSignedCertificates: Boolean
  )(implicit system: ActorSystem[Nothing]): DefaultServerBootstrapEndpointClient =
    new DefaultServerBootstrapEndpointClient(
      serverBootstrapUrl = serverBootstrapUrl,
      acceptSelfSignedCertificates = acceptSelfSignedCertificates
    )

  implicit class ResponseEntityToModel(response: HttpResponse) {
    def to[M](implicit format: Format[M], system: ActorSystem[Nothing]): Future[M] = {
      implicit val ec: ExecutionContext = system.executionContext

      if (response.status.isSuccess()) {
        import com.github.pjfanning.pekkohttpplayjson.PlayJsonSupport._

        Unmarshal(response)
          .to[M]
          .recoverWith { case NonFatal(e) =>
            response.entity.dataBytes.cancelled().flatMap { _ =>
              Future.failed(
                new ServerBootstrapFailure(
                  message = s"Server bootstrap request unmarshalling failed with: [${e.getMessage}]"
                )
              )
            }
          }
      } else {
        Unmarshal(response)
          .to[String]
          .flatMap { responseContent =>
            Future.failed(
              new ServerBootstrapFailure(
                message = s"Server bootstrap request failed with [${response.status.value}]: [$responseContent]"
              )
            )
          }
      }
    }
  }
}
