package stasis.server.security.devices

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration
import scala.util.control.NonFatal

import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.scaladsl.LoggerOps
import org.apache.pekko.http.scaladsl.marshalling.Marshal
import org.apache.pekko.http.scaladsl.model._
import org.apache.pekko.http.scaladsl.unmarshalling.Unmarshal
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import play.api.libs.json.Format
import play.api.libs.json.Json

import stasis.core.api.PoolClient
import io.github.sndnv.layers.security.tls.EndpointContext
import io.github.sndnv.layers.streaming.Operators.ExtendedSource
import stasis.server.security.HttpCredentialsProvider
import stasis.server.security.exceptions.CredentialsManagementFailure
import stasis.shared.model.devices.Device

class IdentityDeviceCredentialsManager(
  identityUrl: String,
  identityCredentials: HttpCredentialsProvider,
  redirectUri: String,
  tokenExpiration: FiniteDuration,
  override protected val context: Option[EndpointContext]
)(implicit override protected val system: ActorSystem[Nothing])
    extends DeviceCredentialsManager
    with PoolClient {
  import IdentityDeviceCredentialsManager._

  private implicit val ec: ExecutionContext = system.executionContext

  override protected val log: Logger = LoggerFactory.getLogger(this.getClass.getName)

  override def setClientSecret(device: Device, clientSecret: String): Future[String] =
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

  private def createClient(device: Device, clientSecret: String): Future[String] = {
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
      result.client
    }
  }

  private def updateClientCredentials(device: Device, client: String, clientSecret: String): Future[String] = {
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
      _ <- response match {
        case response if response.status.isSuccess() =>
          response.entity.dataBytes.cancelled()

        case response =>
          unmarshalResponseFailure(response)
      }
    } yield {
      log.debugN("Updated client [{}] for device [{}] with node [{}]", client, device.id, device.node)
      client
    }
  }

  private def marshalRequestEntity[T](request: T)(implicit format: Format[T]): Future[RequestEntity] = {
    import com.github.pjfanning.pekkohttpplayjson.PlayJsonSupport._
    Marshal(request).to[RequestEntity]
  }

  private def unmarshalResponseEntity[T](response: HttpResponse)(implicit format: Format[T]): Future[T] = {
    import com.github.pjfanning.pekkohttpplayjson.PlayJsonSupport._

    Unmarshal(response)
      .to[T]
      .recoverWith { case NonFatal(e) =>
        response.entity.dataBytes.cancelled().flatMap { _ =>
          Future.failed(
            CredentialsManagementFailure(
              message = s"Identity response unmarshalling failed with: [${e.getMessage}]"
            )
          )
        }
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
  import io.github.sndnv.layers.api.Formats.jsonConfig

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
    identityCredentials: HttpCredentialsProvider,
    redirectUri: String,
    tokenExpiration: FiniteDuration,
    context: Option[EndpointContext]
  )(implicit system: ActorSystem[Nothing]): IdentityDeviceCredentialsManager =
    new IdentityDeviceCredentialsManager(
      identityUrl = identityUrl,
      identityCredentials = identityCredentials,
      redirectUri = redirectUri,
      tokenExpiration = tokenExpiration,
      context = context
    )
}
