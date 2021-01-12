package stasis.server.security.devices

import akka.Done
import akka.actor.typed.scaladsl.LoggerOps
import akka.actor.typed.{ActorSystem, SpawnProtocol}
import akka.http.scaladsl.marshalling.Marshal
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.{HttpCredentials, OAuth2BearerToken}
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.scaladsl.Sink
import akka.util.ByteString
import org.slf4j.LoggerFactory
import play.api.libs.json.Format
import stasis.core.api.PoolClient
import stasis.core.security.jwt.JwtProvider
import stasis.core.security.tls.EndpointContext
import stasis.server.security.exceptions.CredentialsManagementFailure
import stasis.shared.model.devices.Device

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

class IdentityDeviceCredentialsManager(
  identityUrl: String,
  identityCredentials: IdentityDeviceCredentialsManager.CredentialsProvider,
  redirectUri: String,
  tokenExpiration: FiniteDuration,
  override protected val context: Option[EndpointContext],
  override protected val requestBufferSize: Int
)(implicit override protected val system: ActorSystem[SpawnProtocol.Command])
    extends DeviceCredentialsManager
    with PoolClient {
  import IdentityDeviceCredentialsManager._

  private implicit val ec: ExecutionContext = system.executionContext

  private val log = LoggerFactory.getLogger(this.getClass.getName)

  override def setClientSecret(device: Device, clientSecret: String): Future[Done] =
    findClient(device).flatMap {
      case Some(client) => updateClientCredentials(device, client, clientSecret)
      case None         => createClient(device, clientSecret)
    }

  private def findClient(device: Device): Future[Option[String]] =
    for {
      credentials <- identityCredentials.provide()
      response <- offer(
        request = HttpRequest(
          method = HttpMethods.GET,
          uri = s"$identityUrl/manage/clients/search/by-subject/${device.node.toString}"
        ).addCredentials(credentials)
      )
      result <- response match {
        case response if response.status.isSuccess() =>
          unmarshalResponseEntity[List[Client]](response).flatMap {
            case client :: Nil =>
              Future.successful(Some(client.id))

            case Nil =>
              Future.successful(None)

            case other =>
              Future.failed(
                CredentialsManagementFailure(
                  s"Expected only one client to match node [${device.node.toString}] " +
                    s"but [${other.length.toString}] clients found: [${other.map(_.id).mkString(", ")}]"
                )
              )
          }

        case response =>
          unmarshalResponseFailure(response)
      }
    } yield {
      result
    }

  private def createClient(device: Device, clientSecret: String): Future[Done] = {
    val request = CreateClient(
      redirectUri = redirectUri,
      tokenExpiration = tokenExpiration.toSeconds,
      rawSecret = clientSecret,
      subject = device.node.toString
    )

    for {
      credentials <- identityCredentials.provide()
      entity <- marshalRequestEntity[CreateClient](request)
      response <- offer(
        request = HttpRequest(
          method = HttpMethods.POST,
          uri = s"$identityUrl/manage/clients",
          entity = entity
        ).addCredentials(credentials)
      )
      result <- response match {
        case response if response.status.isSuccess() => unmarshalResponseEntity[CreatedClient](response)
        case response                                => unmarshalResponseFailure(response)
      }
    } yield {
      log.debugN("Created client [{}] for device [{}] with node [{}]", result.client, device.id, device.node)
      Done
    }
  }

  private def updateClientCredentials(device: Device, client: String, clientSecret: String): Future[Done] = {
    val request = UpdateClientCredentials(rawSecret = clientSecret)

    for {
      credentials <- identityCredentials.provide()
      entity <- marshalRequestEntity[UpdateClientCredentials](request)
      response <- offer(
        request = HttpRequest(
          method = HttpMethods.PUT,
          uri = s"$identityUrl/manage/clients/$client/credentials",
          entity = entity
        ).addCredentials(credentials)
      )
      result <- response match {
        case response if response.status.isSuccess() =>
          val _ = response.entity.dataBytes.runWith(Sink.cancelled[ByteString])
          Future.successful(Done)

        case response =>
          unmarshalResponseFailure(response)
      }
    } yield {
      log.debugN("Updated client [{}] for device [{}] with node [{}]", client, device.id, device.node)
      result
    }
  }

  private def marshalRequestEntity[T](request: T)(implicit format: Format[T]): Future[RequestEntity] = {
    import de.heikoseeberger.akkahttpplayjson.PlayJsonSupport._
    Marshal(request).to[RequestEntity]
  }

  private def unmarshalResponseEntity[T](response: HttpResponse)(implicit format: Format[T]): Future[T] = {
    import de.heikoseeberger.akkahttpplayjson.PlayJsonSupport._

    Unmarshal(response)
      .to[T]
      .recoverWith { case NonFatal(e) =>
        val _ = response.entity.dataBytes.runWith(Sink.cancelled[ByteString])
        Future.failed(
          CredentialsManagementFailure(
            message = s"Identity response unmarshalling failed with: [${e.getMessage}]"
          )
        )
      }
  }

  private def unmarshalResponseFailure[T](response: HttpResponse): Future[T] =
    Unmarshal(response)
      .to[String]
      .flatMap { responseContent =>
        Future.failed(
          CredentialsManagementFailure(
            message = s"Identity request failed with [${response.status.value}]: [$responseContent]"
          )
        )
      }

}

object IdentityDeviceCredentialsManager {
  import play.api.libs.json.{Format, Json}
  import stasis.core.api.Formats.jsonConfig

  private implicit val clientFormat: Format[Client] =
    Json.format[Client]

  private implicit val createClientFormat: Format[CreateClient] =
    Json.format[CreateClient]

  private implicit val createdClientFormat: Format[CreatedClient] =
    Json.format[CreatedClient]

  private implicit val updateClientCredentialsFormat: Format[UpdateClientCredentials] =
    Json.format[UpdateClientCredentials]

  private final case class Client(
    id: String
  )

  private final case class CreateClient(
    redirectUri: String,
    tokenExpiration: Long,
    rawSecret: String,
    subject: String
  )

  private final case class UpdateClientCredentials(
    rawSecret: String
  )

  private final case class CreatedClient(
    client: String
  )

  def apply(
    identityUrl: String,
    identityCredentials: CredentialsProvider,
    redirectUri: String,
    tokenExpiration: FiniteDuration,
    context: Option[EndpointContext],
    requestBufferSize: Int
  )(implicit system: ActorSystem[SpawnProtocol.Command]): IdentityDeviceCredentialsManager =
    new IdentityDeviceCredentialsManager(
      identityUrl = identityUrl,
      identityCredentials = identityCredentials,
      redirectUri = redirectUri,
      tokenExpiration = tokenExpiration,
      context = context,
      requestBufferSize = requestBufferSize
    )

  trait CredentialsProvider {
    def provide(): Future[HttpCredentials]
  }

  object CredentialsProvider {
    class Default(
      scope: String,
      underlying: JwtProvider
    )(implicit ec: ExecutionContext)
        extends CredentialsProvider {
      override def provide(): Future[HttpCredentials] =
        underlying.provide(scope = scope).map(OAuth2BearerToken)
    }

    object Default {
      def apply(scope: String, underlying: JwtProvider)(implicit ec: ExecutionContext): Default =
        new Default(scope, underlying)
    }
  }
}
