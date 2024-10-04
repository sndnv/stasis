package stasis.client.api.clients

import java.time.Instant

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.util.control.NonFatal

import org.apache.pekko.Done
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.http.scaladsl.marshalling.Marshal
import org.apache.pekko.http.scaladsl.model._
import org.apache.pekko.http.scaladsl.model.headers.HttpCredentials
import org.apache.pekko.http.scaladsl.unmarshalling.Unmarshal
import org.apache.pekko.util.ByteString
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import stasis.client.api.clients.exceptions.ServerApiFailure
import stasis.client.encryption.Decoder
import stasis.client.encryption.secrets.DeviceSecret
import stasis.client.model.DatasetMetadata
import stasis.core.api.PoolClient
import stasis.core.networking.exceptions.ClientFailure
import stasis.layers.security.tls.EndpointContext
import stasis.layers.streaming.Operators.ExtendedSource
import stasis.shared.api.requests.CreateDatasetDefinition
import stasis.shared.api.requests.CreateDatasetEntry
import stasis.shared.api.requests.ResetUserPassword
import stasis.shared.api.responses.CreatedDatasetDefinition
import stasis.shared.api.responses.CreatedDatasetEntry
import stasis.shared.api.responses.Ping
import stasis.shared.api.responses.UpdatedUserSalt
import stasis.shared.model.datasets.DatasetDefinition
import stasis.shared.model.datasets.DatasetEntry
import stasis.shared.model.devices.Device
import stasis.shared.model.schedules.Schedule
import stasis.shared.model.users.User

class DefaultServerApiEndpointClient(
  apiUrl: String,
  credentials: => Future[HttpCredentials],
  decryption: DefaultServerApiEndpointClient.DecryptionContext,
  override val self: Device.Id,
  override protected val context: Option[EndpointContext],
  override protected val config: PoolClient.Config
)(implicit override protected val system: ActorSystem[Nothing])
    extends ServerApiEndpointClient
    with PoolClient {
  import DefaultServerApiEndpointClient._
  import stasis.shared.api.Formats._

  override protected val log: Logger = LoggerFactory.getLogger(this.getClass.getName)

  private implicit val ec: ExecutionContext = system.executionContext

  override val server: String = apiUrl

  override def createDatasetDefinition(request: CreateDatasetDefinition): Future[CreatedDatasetDefinition] =
    if (request.device == self) {
      for {
        entity <- request.toEntity
        credentials <- credentials
        response <- offer(
          request = HttpRequest(
            method = HttpMethods.POST,
            uri = s"$apiUrl/v1/datasets/definitions/own",
            entity = entity
          ).addCredentials(credentials = credentials)
        ).transformClientFailures()
        created <- response.to[CreatedDatasetDefinition]
      } yield {
        created
      }
    } else {
      Future.failed(
        new IllegalArgumentException(
          s"Cannot create dataset definition for a different device: [${request.device.toString}]"
        )
      )
    }

  override def createDatasetEntry(request: CreateDatasetEntry): Future[CreatedDatasetEntry] =
    for {
      entity <- request.toEntity
      credentials <- credentials
      response <- offer(
        request = HttpRequest(
          method = HttpMethods.POST,
          uri = s"$apiUrl/v1/datasets/entries/own/for-definition/${request.definition.toString}",
          entity = entity
        ).addCredentials(credentials = credentials)
      ).transformClientFailures()
      created <- response.to[CreatedDatasetEntry]
    } yield {
      created
    }

  override def datasetDefinitions(): Future[Seq[DatasetDefinition]] =
    for {
      credentials <- credentials
      response <- offer(
        request = HttpRequest(
          method = HttpMethods.GET,
          uri = s"$apiUrl/v1/datasets/definitions/own"
        ).addCredentials(credentials = credentials)
      ).transformClientFailures()
      definitions <- response.to[Seq[DatasetDefinition]]
    } yield {
      definitions.filter(_.device == self)
    }

  override def datasetEntries(definition: DatasetDefinition.Id): Future[Seq[DatasetEntry]] =
    for {
      credentials <- credentials
      response <- offer(
        request = HttpRequest(
          method = HttpMethods.GET,
          uri = s"$apiUrl/v1/datasets/entries/own/for-definition/${definition.toString}"
        ).addCredentials(credentials = credentials)
      ).transformClientFailures()
      entries <- response.to[Seq[DatasetEntry]]
    } yield {
      entries
    }

  override def datasetDefinition(definition: DatasetDefinition.Id): Future[DatasetDefinition] =
    for {
      credentials <- credentials
      response <- offer(
        request = HttpRequest(
          method = HttpMethods.GET,
          uri = s"$apiUrl/v1/datasets/definitions/own/${definition.toString}"
        ).addCredentials(credentials = credentials)
      ).transformClientFailures()
      definition <- response.to[DatasetDefinition]
      _ <-
        if (definition.device == self) {
          Future.successful(())
        } else {
          Future.failed(
            new IllegalArgumentException(
              "Cannot retrieve dataset definition for a different device"
            )
          )
        }
    } yield {
      definition
    }

  override def datasetEntry(entry: DatasetEntry.Id): Future[DatasetEntry] =
    for {
      credentials <- credentials
      response <- offer(
        request = HttpRequest(
          method = HttpMethods.GET,
          uri = s"$apiUrl/v1/datasets/entries/own/${entry.toString}"
        ).addCredentials(credentials = credentials)
      ).transformClientFailures()
      entry <- response.to[DatasetEntry]
    } yield {
      entry
    }

  override def latestEntry(definition: DatasetDefinition.Id, until: Option[Instant]): Future[Option[DatasetEntry]] = {
    val baseUrl = s"$apiUrl/v1/datasets/entries/own/for-definition/${definition.toString}/latest"
    for {
      credentials <- credentials
      response <- offer(
        request = HttpRequest(
          method = HttpMethods.GET,
          uri = until match {
            case Some(until) => s"$baseUrl?until=${until.toString}"
            case None        => baseUrl
          }
        ).addCredentials(credentials = credentials)
      ).transformClientFailures()
      entry <- response match {
        case HttpResponse(StatusCodes.NotFound, _, _, _) => Future.successful(None)
        case _                                           => response.to[DatasetEntry].map(Some.apply)
      }
    } yield {
      entry
    }
  }

  override def publicSchedules(): Future[Seq[Schedule]] =
    for {
      credentials <- credentials
      response <- offer(
        request = HttpRequest(
          method = HttpMethods.GET,
          uri = s"$apiUrl/v1/schedules/public"
        ).addCredentials(credentials = credentials)
      ).transformClientFailures()
      schedules <- response.to[Seq[Schedule]]
    } yield {
      schedules
    }

  override def publicSchedule(schedule: Schedule.Id): Future[Schedule] =
    for {
      credentials <- credentials
      response <- offer(
        request = HttpRequest(
          method = HttpMethods.GET,
          uri = s"$apiUrl/v1/schedules/public/${schedule.toString}"
        ).addCredentials(credentials = credentials)
      ).transformClientFailures()
      schedule <- response.to[Schedule]
    } yield {
      schedule
    }

  override def datasetMetadata(entry: DatasetEntry.Id): Future[DatasetMetadata] =
    for {
      entry <- datasetEntry(entry)
      metadata <- datasetMetadata(entry)
    } yield {
      metadata
    }

  override def datasetMetadata(entry: DatasetEntry): Future[DatasetMetadata] =
    decryption match {
      case DecryptionContext.Default(core, deviceSecret, decoder) =>
        for {
          entryMetadata <- core.pull(crate = entry.metadata)
          entryMetadata <- DatasetMetadata.decrypt(
            metadataCrate = entry.metadata,
            metadataSecret = deviceSecret.toMetadataSecret(metadataCrate = entry.metadata),
            metadata = entryMetadata,
            decoder = decoder
          )
        } yield {
          entryMetadata
        }

      case DecryptionContext.Disabled =>
        Future.failed(
          new IllegalStateException("Cannot retrieve dataset metadata; decryption context is disabled")
        )
    }

  override def user(): Future[User] =
    for {
      credentials <- credentials
      response <- offer(
        request = HttpRequest(
          method = HttpMethods.GET,
          uri = s"$apiUrl/v1/users/self"
        ).addCredentials(credentials = credentials)
      ).transformClientFailures()
      user <- response.to[User]
    } yield {
      user
    }

  override def resetUserSalt(): Future[UpdatedUserSalt] =
    for {
      credentials <- credentials
      response <- offer(
        request = HttpRequest(
          method = HttpMethods.PUT,
          uri = s"$apiUrl/v1/users/self/salt"
        ).addCredentials(credentials = credentials)
      ).transformClientFailures()
      salt <- response.to[UpdatedUserSalt]
    } yield {
      salt
    }

  override def resetUserPassword(request: ResetUserPassword): Future[Done] =
    for {
      entity <- request.toEntity
      credentials <- credentials
      response <- offer(
        request = HttpRequest(
          method = HttpMethods.PUT,
          uri = s"$apiUrl/v1/users/self/password",
          entity = entity
        ).addCredentials(credentials = credentials)
      ).transformClientFailures()
      _ <- response.processed()
    } yield {
      Done
    }

  override def device(): Future[Device] =
    for {
      credentials <- credentials
      response <- offer(
        request = HttpRequest(
          method = HttpMethods.GET,
          uri = s"$apiUrl/v1/devices/own/${self.toString}"
        ).addCredentials(credentials = credentials)
      ).transformClientFailures()
      device <- response.to[Device]
    } yield {
      device
    }

  override def pushDeviceKey(key: ByteString): Future[Done] =
    for {
      credentials <- credentials
      response <- offer(
        request = HttpRequest(
          method = HttpMethods.PUT,
          uri = s"$apiUrl/v1/devices/own/${self.toString}/key"
        ).withEntity(key).addCredentials(credentials = credentials)
      ).transformClientFailures()
      _ <- response.processed()
    } yield {
      Done
    }

  override def pullDeviceKey(): Future[Option[ByteString]] =
    for {
      credentials <- credentials
      response <- offer(
        request = HttpRequest(
          method = HttpMethods.GET,
          uri = s"$apiUrl/v1/devices/own/${self.toString}/key"
        ).addCredentials(credentials = credentials)
      ).transformClientFailures()
      key <- response match {
        case HttpResponse(status, _, _, _) if status.isSuccess() => response.toByteString.map(Some.apply)
        case HttpResponse(StatusCodes.NotFound, _, _, _)         => Future.successful(None)
        case _                                                   => response.asFailure
      }
    } yield {
      key
    }

  override def deviceKeyExists(): Future[Boolean] =
    for {
      credentials <- credentials
      response <- offer(
        request = HttpRequest(
          method = HttpMethods.HEAD,
          uri = s"$apiUrl/v1/devices/own/${self.toString}/key"
        ).addCredentials(credentials = credentials)
      ).transformClientFailures()
      result <- response match {
        case HttpResponse(status, _, _, _) if status.isSuccess() => Future.successful(true)
        case HttpResponse(StatusCodes.NotFound, _, _, _)         => Future.successful(false)
        case _                                                   => response.asFailure
      }
    } yield {
      result
    }

  override def ping(): Future[Ping] =
    for {
      credentials <- credentials
      response <- offer(
        request = HttpRequest(
          method = HttpMethods.GET,
          uri = s"$apiUrl/v1/service/ping"
        ).addCredentials(credentials = credentials)
      ).transformClientFailures()
      ping <- response.to[Ping]
    } yield {
      ping
    }
}

object DefaultServerApiEndpointClient {
  import play.api.libs.json.Format

  def apply(
    apiUrl: String,
    credentials: => Future[HttpCredentials],
    decryption: DefaultServerApiEndpointClient.DecryptionContext,
    self: Device.Id,
    context: Option[EndpointContext],
    config: PoolClient.Config
  )(implicit system: ActorSystem[Nothing]): DefaultServerApiEndpointClient =
    new DefaultServerApiEndpointClient(
      apiUrl = apiUrl,
      credentials = credentials,
      decryption = decryption,
      self = self,
      context = context,
      config = config
    )

  sealed trait DecryptionContext

  object DecryptionContext {
    def apply(core: ServerCoreEndpointClient, deviceSecret: DeviceSecret, decoder: Decoder): DecryptionContext =
      Default(core = core, deviceSecret = deviceSecret, decoder = decoder)

    final case class Default(
      core: ServerCoreEndpointClient,
      deviceSecret: DeviceSecret,
      decoder: Decoder
    ) extends DecryptionContext

    case object Disabled extends DecryptionContext
  }

  implicit class ModelToRequestEntity[M](m: M) {
    import com.github.pjfanning.pekkohttpplayjson.PlayJsonSupport._

    def toEntity(implicit format: Format[M], ec: ExecutionContext): Future[RequestEntity] =
      Marshal(m).to[RequestEntity]
  }

  implicit class ExtendedResponseEntity(response: HttpResponse) {
    def processed[T](f: () => Future[T])(implicit system: ActorSystem[Nothing]): Future[T] = {
      import system.executionContext

      if (response.status.isSuccess()) {
        f()
          .recoverWith { case NonFatal(e) =>
            response.entity.dataBytes.cancelled().flatMap { _ =>
              Future.failed(
                new ServerApiFailure(
                  status = StatusCodes.InternalServerError,
                  message = s"Server API request unmarshalling failed with: [${e.getMessage}]"
                )
              )
            }
          }
      } else {
        response.asFailure
      }
    }

    def processed()(implicit system: ActorSystem[Nothing]): Future[Done] =
      processed[Done](f = () => Future.successful(Done))

    def to[M](implicit format: Format[M], ec: ExecutionContext, system: ActorSystem[Nothing]): Future[M] =
      processed[M] { () =>
        import com.github.pjfanning.pekkohttpplayjson.PlayJsonSupport._
        Unmarshal(response).to[M]
      }

    def toByteString(implicit ec: ExecutionContext, system: ActorSystem[Nothing]): Future[ByteString] =
      processed[ByteString] { () =>
        Unmarshal(response).to[ByteString]
      }

    def asFailure[T](implicit system: ActorSystem[Nothing]): Future[T] = {
      import system.executionContext

      Unmarshal(response)
        .to[String]
        .flatMap { responseContent =>
          Future.failed(
            new ServerApiFailure(
              status = response.status,
              message = s"Server API request failed with [${response.status.value}]: [$responseContent]"
            )
          )
        }
    }
  }

  implicit class HttpResponseWithTransformedFailures(op: => Future[HttpResponse]) {
    def transformClientFailures()(implicit ec: ExecutionContext): Future[HttpResponse] =
      op.recoverWith { case NonFatal(e: ClientFailure) =>
        Future.failed(
          new ServerApiFailure(
            status = StatusCodes.InternalServerError,
            message = e.getMessage
          )
        )
      }
  }
}
