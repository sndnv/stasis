package stasis.core.api

import akka.actor.typed.scaladsl.adapter._
import akka.actor.typed.{ActorSystem, SpawnProtocol}
import akka.http.scaladsl.model.Uri
import akka.util.Timeout
import stasis.core.networking.http.HttpEndpointAddress
import stasis.core.packaging.Manifest
import stasis.core.persistence.backends.StreamingBackend
import stasis.core.persistence.backends.file.{ContainerBackend, FileBackend}
import stasis.core.persistence.backends.memory.StreamingMemoryBackend
import stasis.core.persistence.crates.CrateStore
import stasis.core.persistence.reservations.ReservationStore
import stasis.core.persistence.{CrateStorageRequest, CrateStorageReservation}
import stasis.core.routing.Node

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

object Formats {
  import play.api.libs.json._

  implicit val finiteDurationFormat: Format[FiniteDuration] = Format(
    fjs = js => js.validate[Long].map(seconds => seconds.seconds),
    tjs = duration => JsNumber(duration.toSeconds)
  )

  implicit val uriFormat: Format[Uri] = Format(
    fjs = _.validate[String].map(Uri.apply),
    tjs = uri => JsString(uri.toString)
  )

  implicit val httpEndpointAddressFormat: Format[HttpEndpointAddress] =
    Json.format[HttpEndpointAddress]

  implicit def streamingBackendReads(
    implicit system: ActorSystem[SpawnProtocol],
    timeout: Timeout
  ): Reads[StreamingBackend] = Reads {
    _.validate[JsObject].flatMap { backend =>
      implicit val ec: ExecutionContext = system.executionContext

      (backend \ "backend-type").validate[String].map {
        case "memory" =>
          StreamingMemoryBackend(
            maxSize = (backend \ "max-size").as[Long],
            name = (backend \ "name").as[String]
          )

        case "container" =>
          new ContainerBackend(
            path = (backend \ "path").as[String],
            maxChunkSize = (backend \ "max-chunk-size").as[Int],
            maxChunks = (backend \ "max-chunks").as[Int]
          )

        case "file" =>
          new FileBackend(
            parentDirectory = (backend \ "parent-directory").as[String]
          )
      }
    }
  }

  implicit val streamingBackendWrites: Writes[StreamingBackend] = Writes {
    case backend: StreamingMemoryBackend =>
      Json.obj(
        "backend-type" -> JsString("memory"),
        "max-size" -> JsNumber(backend.maxSize),
        "name" -> JsString(backend.name)
      )

    case backend: ContainerBackend =>
      Json.obj(
        "backend-type" -> JsString("container"),
        "path" -> JsString(backend.path),
        "max-chunk-size" -> JsNumber(backend.maxChunkSize),
        "max-chunks" -> JsNumber(backend.maxChunks)
      )

    case backend: FileBackend =>
      Json.obj(
        "backend-type" -> JsString("file"),
        "parent-directory" -> JsString(backend.parentDirectory)
      )
  }

  implicit def nodeReads(
    implicit system: ActorSystem[SpawnProtocol],
    timeout: Timeout,
    reservationStore: ReservationStore
  ): Reads[Node] = Reads {
    _.validate[JsObject].flatMap { node =>
      (node \ "node-type").validate[String].map {
        case "local" =>
          val id = (node \ "id").as[Node.Id]
          Node.Local(
            id = id,
            crateStore = CrateStore(
              streamingBackend = (node \ "backend").as[StreamingBackend],
              reservationStore = reservationStore,
              storeId = id
            )(system.toUntyped)
          )

        case "remote-http" =>
          Node.Remote.Http(
            id = (node \ "id").as[Node.Id],
            address = (node \ "address").as[HttpEndpointAddress]
          )
      }
    }
  }

  implicit val nodeWrites: Writes[Node] = Writes {
    case node: Node.Local =>
      Json.obj(
        "node-type" -> JsString("local"),
        "id" -> Json.toJson(node.id),
        "backend" -> Json.toJson(node.crateStore.backend)
      )

    case node: Node.Remote.Http =>
      Json.obj(
        "node-type" -> JsString("remote-http"),
        "id" -> Json.toJson(node.id),
        "address" -> Json.toJson(node.address)
      )
  }

  implicit val manifestFormat: Format[Manifest] = Json.format[Manifest]

  implicit val crateStorageRequestFormat: Format[CrateStorageRequest] = Json.format[CrateStorageRequest]

  implicit val crateStorageReservationFormat: Format[CrateStorageReservation] = Json.format[CrateStorageReservation]
}
