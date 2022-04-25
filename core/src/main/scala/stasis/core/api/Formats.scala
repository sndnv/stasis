package stasis.core.api

import java.util.UUID

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
    tjs = duration => Json.toJson(duration.toSeconds)
  )

  implicit def uuidMapFormat[V](implicit format: Format[V]): Format[Map[UUID, V]] =
    Format(
      fjs = _.validate[Map[String, V]].map(_.map { case (k, v) => UUID.fromString(k) -> v }),
      tjs = map => Json.toJson(map.map { case (k, v) => k.toString -> format.writes(v) })
    )

  implicit def optionFormat[V](implicit format: Format[V]): Format[Option[V]] =
    Format(
      fjs = _.validateOpt[V],
      tjs = _.map(Json.toJson(_)).getOrElse(JsNull)
    )

  implicit val uriFormat: Format[Uri] = Format(
    fjs = _.validate[String].map(Uri.apply),
    tjs = uri => Json.toJson(uri.toString)
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
            maxChunkSize = (descriptor \ "max_chunk_size").as[Int],
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
        "backend_type" -> Json.toJson("memory"),
        "max_size" -> Json.toJson(backend.maxSize),
        "max_chunk_size" -> Json.toJson(backend.maxChunkSize),
        "name" -> Json.toJson(backend.name)
      )

    case backend: CrateStore.Descriptor.ForContainerBackend =>
      Json.obj(
        "backend_type" -> Json.toJson("container"),
        "path" -> Json.toJson(backend.path),
        "max_chunk_size" -> Json.toJson(backend.maxChunkSize),
        "max_chunks" -> Json.toJson(backend.maxChunks)
      )

    case backend: CrateStore.Descriptor.ForFileBackend =>
      Json.obj(
        "backend_type" -> Json.toJson("file"),
        "parent_directory" -> Json.toJson(backend.parentDirectory)
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
            address = (node \ "address").as[HttpEndpointAddress],
            storageAllowed = (node \ "storage_allowed").as[Boolean]
          )

        case "remote-grpc" =>
          Node.Remote.Grpc(
            id = (node \ "id").as[Node.Id],
            address = (node \ "address").as[GrpcEndpointAddress],
            storageAllowed = (node \ "storage_allowed").as[Boolean]
          )
      }
    }
  }

  implicit val nodeWrites: Writes[Node] = Writes {
    case node: Node.Local =>
      Json.obj(
        "node_type" -> Json.toJson("local"),
        "id" -> Json.toJson(node.id),
        "store_descriptor" -> Json.toJson(node.storeDescriptor)
      )

    case node: Node.Remote.Http =>
      Json.obj(
        "node_type" -> Json.toJson("remote-http"),
        "id" -> Json.toJson(node.id),
        "address" -> Json.toJson(node.address),
        "storage_allowed" -> Json.toJson(node.storageAllowed)
      )

    case node: Node.Remote.Grpc =>
      Json.obj(
        "node_type" -> Json.toJson("remote-grpc"),
        "id" -> Json.toJson(node.id),
        "address" -> Json.toJson(node.address),
        "storage_allowed" -> Json.toJson(node.storageAllowed)
      )
  }

  implicit val manifestFormat: Format[Manifest] = Json.format[Manifest]

  implicit val crateStorageRequestFormat: Format[CrateStorageRequest] = Json.format[CrateStorageRequest]

  implicit val crateStorageReservationFormat: Format[CrateStorageReservation] = Json.format[CrateStorageReservation]
}
