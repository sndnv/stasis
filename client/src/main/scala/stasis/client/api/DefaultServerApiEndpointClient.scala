package stasis.client.api

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshalling.Marshal
import akka.http.scaladsl.model.headers.HttpCredentials
import akka.http.scaladsl.model.{HttpMethods, HttpRequest, RequestEntity}
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.ActorMaterializer
import stasis.shared.api.requests.CreateDatasetEntry
import stasis.shared.api.responses.CreatedDatasetEntry
import stasis.shared.model.datasets.DatasetEntry
import stasis.shared.model.devices.Device

import scala.concurrent.{ExecutionContext, Future}

class DefaultServerApiEndpointClient(
  apiUrl: String,
  apiCredentials: HttpCredentials,
  override val self: Device.Id
)(implicit system: ActorSystem)
    extends ServerApiEndpointClient {
  import de.heikoseeberger.akkahttpplayjson.PlayJsonSupport._
  import stasis.shared.api.Formats._

  private implicit val mat: ActorMaterializer = ActorMaterializer()
  private implicit val ec: ExecutionContext = system.dispatcher

  override def createDatasetEntry(request: CreateDatasetEntry): Future[DatasetEntry.Id] =
    for {
      entity <- Marshal(request).to[RequestEntity]
      response <- Http()
        .singleRequest(
          request = HttpRequest(
            method = HttpMethods.POST,
            uri = s"$apiUrl/own/for-definition/${request.definition}",
            entity = entity
          ).addCredentials(credentials = apiCredentials)
        )
      entry <- Unmarshal(response).to[CreatedDatasetEntry].map(_.entry)
    } yield {
      entry
    }
}
