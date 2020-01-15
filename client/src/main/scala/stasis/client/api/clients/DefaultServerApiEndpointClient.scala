package stasis.client.api.clients

import java.time.Instant

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshalling.Marshal
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.HttpCredentials
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.{ActorMaterializer, Materializer}
import akka.stream.scaladsl.Sink
import akka.util.ByteString
import stasis.client.api.clients.exceptions.ServerApiFailure
import stasis.client.encryption.secrets.DeviceSecret
import stasis.client.encryption.Decoder
import stasis.client.model.DatasetMetadata
import stasis.shared.api.requests.{CreateDatasetDefinition, CreateDatasetEntry}
import stasis.shared.api.responses.{CreatedDatasetDefinition, CreatedDatasetEntry, Ping}
import stasis.shared.model.datasets.{DatasetDefinition, DatasetEntry}
import stasis.shared.model.devices.Device
import stasis.shared.model.schedules.Schedule
import stasis.shared.model.users.User

import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

class DefaultServerApiEndpointClient(
  apiUrl: String,
  apiCredentials: HttpCredentials,
  decryption: DefaultServerApiEndpointClient.DecryptionContext,
  override val self: Device.Id
)(implicit system: ActorSystem)
    extends ServerApiEndpointClient {
  import stasis.shared.api.Formats._
  import DefaultServerApiEndpointClient._

  private implicit val mat: ActorMaterializer = ActorMaterializer()
  private implicit val ec: ExecutionContext = system.dispatcher

  override val server: String = apiUrl

  override def createDatasetDefinition(request: CreateDatasetDefinition): Future[CreatedDatasetDefinition] =
    for {
      entity <- request.toEntity
      response <- Http()
        .singleRequest(
          request = HttpRequest(
            method = HttpMethods.POST,
            uri = s"$apiUrl/datasets/definitions/own",
            entity = entity
          ).addCredentials(credentials = apiCredentials)
        )
      created <- response.to[CreatedDatasetDefinition]
    } yield {
      created
    }

  override def createDatasetEntry(request: CreateDatasetEntry): Future[CreatedDatasetEntry] =
    for {
      entity <- request.toEntity
      response <- Http()
        .singleRequest(
          request = HttpRequest(
            method = HttpMethods.POST,
            uri = s"$apiUrl/datasets/entries/own/for-definition/${request.definition}",
            entity = entity
          ).addCredentials(credentials = apiCredentials)
        )
      created <- response.to[CreatedDatasetEntry]
    } yield {
      created
    }

  override def datasetDefinitions(): Future[Seq[DatasetDefinition]] =
    for {
      response <- Http()
        .singleRequest(
          request = HttpRequest(
            method = HttpMethods.GET,
            uri = s"$apiUrl/datasets/definitions/own"
          ).addCredentials(credentials = apiCredentials)
        )
      definitions <- response.to[Seq[DatasetDefinition]]
    } yield {
      definitions
    }

  override def datasetEntries(definition: DatasetDefinition.Id): Future[Seq[DatasetEntry]] =
    for {
      response <- Http()
        .singleRequest(
          request = HttpRequest(
            method = HttpMethods.GET,
            uri = s"$apiUrl/datasets/entries/own/for-definition/$definition"
          ).addCredentials(credentials = apiCredentials)
        )
      entries <- response.to[Seq[DatasetEntry]]
    } yield {
      entries
    }

  override def datasetDefinition(definition: DatasetDefinition.Id): Future[DatasetDefinition] =
    for {
      response <- Http()
        .singleRequest(
          request = HttpRequest(
            method = HttpMethods.GET,
            uri = s"$apiUrl/datasets/definitions/own/$definition"
          ).addCredentials(credentials = apiCredentials)
        )
      definition <- response.to[DatasetDefinition]
    } yield {
      definition
    }

  override def datasetEntry(entry: DatasetEntry.Id): Future[DatasetEntry] =
    for {
      response <- Http()
        .singleRequest(
          request = HttpRequest(
            method = HttpMethods.GET,
            uri = s"$apiUrl/datasets/entries/own/$entry"
          ).addCredentials(credentials = apiCredentials)
        )
      entry <- response.to[DatasetEntry]
    } yield {
      entry
    }

  override def latestEntry(definition: DatasetDefinition.Id, until: Option[Instant]): Future[Option[DatasetEntry]] =
    for {
      response <- Http()
        .singleRequest(
          request = HttpRequest(
            method = HttpMethods.GET,
            uri = s"$apiUrl/datasets/entries/own/for-definition/$definition/latest?until=$until"
          ).addCredentials(credentials = apiCredentials)
        )
      entry <- response match {
        case HttpResponse(StatusCodes.NotFound, _, _, _) => Future.successful(None)
        case _                                           => response.to[DatasetEntry].map(Some.apply)
      }
    } yield {
      entry
    }

  def publicSchedules(): Future[Seq[Schedule]] =
    for {
      response <- Http()
        .singleRequest(
          request = HttpRequest(
            method = HttpMethods.GET,
            uri = s"$apiUrl/schedules/public"
          ).addCredentials(credentials = apiCredentials)
        )
      schedules <- response.to[Seq[Schedule]]
    } yield {
      schedules
    }

  def publicSchedule(schedule: Schedule.Id): Future[Schedule] =
    for {
      response <- Http()
        .singleRequest(
          request = HttpRequest(
            method = HttpMethods.GET,
            uri = s"$apiUrl/schedules/public/$schedule"
          ).addCredentials(credentials = apiCredentials)
        )
      schedule <- response.to[Schedule]
    } yield {
      schedule
    }

  def datasetMetadata(entry: DatasetEntry.Id): Future[DatasetMetadata] =
    for {
      entry <- datasetEntry(entry)
      metadata <- datasetMetadata(entry)
    } yield {
      metadata
    }

  def datasetMetadata(entry: DatasetEntry): Future[DatasetMetadata] =
    for {
      entryMetadata <- decryption.core.pull(crate = entry.metadata)
      entryMetadata <- DatasetMetadata.decrypt(
        metadataCrate = entry.metadata,
        metadataSecret = decryption.deviceSecret.toMetadataSecret(metadataCrate = entry.metadata),
        metadata = entryMetadata,
        decoder = decryption.decoder
      )
    } yield {
      entryMetadata
    }

  override def user(): Future[User] =
    for {
      response <- Http()
        .singleRequest(
          request = HttpRequest(
            method = HttpMethods.GET,
            uri = s"$apiUrl/users/self"
          ).addCredentials(credentials = apiCredentials)
        )
      user <- response.to[User]
    } yield {
      user
    }

  override def device(): Future[Device] =
    for {
      response <- Http()
        .singleRequest(
          request = HttpRequest(
            method = HttpMethods.GET,
            uri = s"$apiUrl/devices/own/$self"
          ).addCredentials(credentials = apiCredentials)
        )
      device <- response.to[Device]
    } yield {
      device
    }

  override def ping(): Future[Ping] =
    for {
      response <- Http()
        .singleRequest(
          request = HttpRequest(
            method = HttpMethods.GET,
            uri = s"$apiUrl/service/ping"
          ).addCredentials(credentials = apiCredentials)
        )
      ping <- response.to[Ping]
    } yield {
      ping
    }
}

object DefaultServerApiEndpointClient {
  import play.api.libs.json.Format

  final case class DecryptionContext(
    core: ServerCoreEndpointClient,
    deviceSecret: DeviceSecret,
    decoder: Decoder
  )

  implicit class ModelToRequestEntity[M](m: M) {
    import de.heikoseeberger.akkahttpplayjson.PlayJsonSupport._

    def toEntity(implicit format: Format[M], ec: ExecutionContext): Future[RequestEntity] =
      Marshal(m).to[RequestEntity]
  }

  implicit class ResponseEntityToModel(response: HttpResponse) {
    def to[M](implicit format: Format[M], ec: ExecutionContext, mat: Materializer): Future[M] =
      if (response.status.isSuccess()) {
        import de.heikoseeberger.akkahttpplayjson.PlayJsonSupport._

        Unmarshal(response)
          .to[M]
          .recoverWith {
            case NonFatal(e) =>
              val _ = response.entity.dataBytes.runWith(Sink.cancelled[ByteString])
              Future.failed(
                new ServerApiFailure(
                  message = s"Server API request unmarshalling failed with: [${e.getMessage}]"
                )
              )
          }
      } else {
        Unmarshal(response)
          .to[String]
          .flatMap { responseContent =>
            Future.failed(
              new ServerApiFailure(
                message = s"Server API request failed with [${response.status}]: [$responseContent]"
              )
            )
          }
      }
  }
}
