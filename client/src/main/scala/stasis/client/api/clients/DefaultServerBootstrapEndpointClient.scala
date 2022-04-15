package stasis.client.api.clients

import akka.actor.typed.{ActorSystem, SpawnProtocol}
import akka.http.scaladsl.model.headers.OAuth2BearerToken
import akka.http.scaladsl.model.{HttpMethods, HttpRequest, HttpResponse}
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.http.scaladsl.{ConnectionContext, Http}
import akka.stream.scaladsl.Sink
import akka.util.ByteString
import play.api.libs.json.Format
import stasis.client.api.clients.exceptions.ServerBootstrapFailure
import stasis.client.api.clients.internal.InsecureX509TrustManager
import stasis.shared.model.devices.DeviceBootstrapParameters

import java.security.SecureRandom
import javax.net.ssl.SSLContext
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

class DefaultServerBootstrapEndpointClient(
  serverBootstrapUrl: String,
  acceptSelfSignedCertificates: Boolean
)(implicit system: ActorSystem[SpawnProtocol.Command])
    extends ServerBootstrapEndpointClient {
  import DefaultServerBootstrapEndpointClient._
  import stasis.shared.api.Formats._

  override val server: String = serverBootstrapUrl

  private val http = Http()(system.classicSystem)

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
          uri = s"$serverBootstrapUrl/devices/execute"
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
  )(implicit system: ActorSystem[SpawnProtocol.Command]): DefaultServerBootstrapEndpointClient =
    new DefaultServerBootstrapEndpointClient(
      serverBootstrapUrl = serverBootstrapUrl,
      acceptSelfSignedCertificates = acceptSelfSignedCertificates
    )

  implicit class ResponseEntityToModel(response: HttpResponse) {
    def to[M](implicit format: Format[M], system: ActorSystem[SpawnProtocol.Command]): Future[M] = {
      implicit val ec: ExecutionContext = system.executionContext

      if (response.status.isSuccess()) {
        import de.heikoseeberger.akkahttpplayjson.PlayJsonSupport._

        Unmarshal(response)
          .to[M]
          .recoverWith { case NonFatal(e) =>
            val _ = response.entity.dataBytes.runWith(Sink.cancelled[ByteString])
            Future.failed(
              new ServerBootstrapFailure(
                message = s"Server bootstrap request unmarshalling failed with: [${e.getMessage}]"
              )
            )
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
