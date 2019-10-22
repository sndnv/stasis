package stasis.core.api

import akka.http.scaladsl.model.Uri
import stasis.core.networking.grpc.GrpcEndpointAddress
import stasis.core.networking.http.HttpEndpointAddress
import stasis.core.packaging.Manifest
import stasis.core.persistence.crates.CrateStore
import stasis.core.persistence.{CrateStorageRequest, CrateStorageReservation}
import stasis.core.routing.Node

import scala.concurrent.duration._

object Formats {
  import play.api.libs.json._

  implicit val jsonConfig: JsonConfiguration = JsonConfiguration(JsonNaming.SnakeCase)

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

  implicit val grpcEndpointAddressFormat: Format[GrpcEndpointAddress] =
    Json.format[GrpcEndpointAddress]

  implicit val crateStoreDescriptorReads: Reads[CrateStore.Descriptor] = Reads {
    _.validate[JsObject].flatMap { descriptor =>
      (descriptor \ "backend_type").validate[String].map {
        case "memory" =>
          CrateStore.Descriptor.ForStreamingMemoryBackend(
            maxSize = (descriptor \ "max_size").as[Long],
            name = (descriptor \ "name").as[String]
          )

        case "container" =>
          CrateStore.Descriptor.ForContainerBackend(
            path = (descriptor \ "path").as[String],
            maxChunkSize = (descriptor \ "max_chunk_size").as[Int],
            maxChunks = (descriptor \ "max_chunks").as[Int]
          )

        case "file" =>
          CrateStore.Descriptor.ForFileBackend(
            parentDirectory = (descriptor \ "parent_directory").as[String]
          )
      }
    }
  }

  implicit val crateStoreDescriptorWrites: Writes[CrateStore.Descriptor] = Writes {
    case backend: CrateStore.Descriptor.ForStreamingMemoryBackend =>
      Json.obj(
        "backend_type" -> JsString("memory"),
        "max_size" -> JsNumber(backend.maxSize),
        "name" -> JsString(backend.name)
      )

    case backend: CrateStore.Descriptor.ForContainerBackend =>
      Json.obj(
        "backend_type" -> JsString("container"),
        "path" -> JsString(backend.path),
        "max_chunk_size" -> JsNumber(backend.maxChunkSize),
        "max_chunks" -> JsNumber(backend.maxChunks)
      )

    case backend: CrateStore.Descriptor.ForFileBackend =>
      Json.obj(
        "backend_type" -> JsString("file"),
        "parent_directory" -> JsString(backend.parentDirectory)
      )
  }

  implicit val nodeReads: Reads[Node] = Reads {
    _.validate[JsObject].flatMap { node =>
      (node \ "node_type").validate[String].map {
        case "local" =>
          val id = (node \ "id").as[Node.Id]
          Node.Local(
            id = id,
            storeDescriptor = (node \ "store_descriptor").as[CrateStore.Descriptor]
          )

        case "remote-http" =>
          Node.Remote.Http(
            id = (node \ "id").as[Node.Id],
            address = (node \ "address").as[HttpEndpointAddress]
          )

        case "remote-grpc" =>
          Node.Remote.Grpc(
            id = (node \ "id").as[Node.Id],
            address = (node \ "address").as[GrpcEndpointAddress]
          )
      }
    }
  }

  implicit val nodeWrites: Writes[Node] = Writes {
    case node: Node.Local =>
      Json.obj(
        "node_type" -> JsString("local"),
        "id" -> Json.toJson(node.id),
        "store_descriptor" -> Json.toJson(node.storeDescriptor)
      )

    case node: Node.Remote.Http =>
      Json.obj(
        "node_type" -> JsString("remote-http"),
        "id" -> Json.toJson(node.id),
        "address" -> Json.toJson(node.address)
      )

    case node: Node.Remote.Grpc =>
      Json.obj(
        "node_type" -> JsString("remote-grpc"),
        "id" -> Json.toJson(node.id),
        "address" -> Json.toJson(node.address)
      )
  }

  implicit val manifestFormat: Format[Manifest] = Json.format[Manifest]

  implicit val crateStorageRequestFormat: Format[CrateStorageRequest] = Json.format[CrateStorageRequest]

  implicit val crateStorageReservationFormat: Format[CrateStorageReservation] = Json.format[CrateStorageReservation]
}
