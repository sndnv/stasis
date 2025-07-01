package stasis.core.api

import java.time.Instant

import stasis.core.discovery.ServiceApiEndpoint
import stasis.core.discovery.ServiceDiscoveryRequest
import stasis.core.discovery.ServiceDiscoveryResult
import stasis.core.networking.EndpointAddress
import stasis.core.networking.grpc.GrpcEndpointAddress
import stasis.core.networking.http.HttpEndpointAddress
import stasis.core.packaging.Manifest
import stasis.core.persistence.CrateStorageRequest
import stasis.core.persistence.CrateStorageReservation
import stasis.core.persistence.crates.CrateStore
import stasis.core.routing.Node

object Formats {
  import play.api.libs.json._

  import io.github.sndnv.layers.api.Formats.jsonConfig
  import io.github.sndnv.layers.api.Formats.uriFormat

  implicit val httpEndpointAddressFormat: Format[HttpEndpointAddress] =
    Json.format[HttpEndpointAddress]

  implicit val grpcEndpointAddressFormat: Format[GrpcEndpointAddress] =
    Json.format[GrpcEndpointAddress]

  implicit val addressFormat: Format[EndpointAddress] = Format(
    _.validate[JsObject].flatMap { address =>
      (address \ "address_type").validate[String].map {
        case "http" => (address \ "address").as[HttpEndpointAddress]
        case "grpc" => (address \ "address").as[GrpcEndpointAddress]
      }
    },
    {
      case address: HttpEndpointAddress => Json.obj("address_type" -> Json.toJson("http"), "address" -> Json.toJson(address))
      case address: GrpcEndpointAddress => Json.obj("address_type" -> Json.toJson("grpc"), "address" -> Json.toJson(address))
    }
  )

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
            storeDescriptor = (node \ "store_descriptor").as[CrateStore.Descriptor],
            created = (node \ "created").as[Instant],
            updated = (node \ "updated").as[Instant]
          )

        case "remote-http" =>
          Node.Remote.Http(
            id = (node \ "id").as[Node.Id],
            address = (node \ "address").as[HttpEndpointAddress],
            storageAllowed = (node \ "storage_allowed").as[Boolean],
            created = (node \ "created").as[Instant],
            updated = (node \ "updated").as[Instant]
          )

        case "remote-grpc" =>
          Node.Remote.Grpc(
            id = (node \ "id").as[Node.Id],
            address = (node \ "address").as[GrpcEndpointAddress],
            storageAllowed = (node \ "storage_allowed").as[Boolean],
            created = (node \ "created").as[Instant],
            updated = (node \ "updated").as[Instant]
          )
      }
    }
  }

  implicit val nodeWrites: Writes[Node] = Writes {
    case node: Node.Local =>
      Json.obj(
        "node_type" -> Json.toJson("local"),
        "id" -> Json.toJson(node.id),
        "store_descriptor" -> Json.toJson(node.storeDescriptor),
        "created" -> Json.toJson(node.created),
        "updated" -> Json.toJson(node.updated)
      )

    case node: Node.Remote.Http =>
      Json.obj(
        "node_type" -> Json.toJson("remote-http"),
        "id" -> Json.toJson(node.id),
        "address" -> Json.toJson(node.address),
        "storage_allowed" -> Json.toJson(node.storageAllowed),
        "created" -> Json.toJson(node.created),
        "updated" -> Json.toJson(node.updated)
      )

    case node: Node.Remote.Grpc =>
      Json.obj(
        "node_type" -> Json.toJson("remote-grpc"),
        "id" -> Json.toJson(node.id),
        "address" -> Json.toJson(node.address),
        "storage_allowed" -> Json.toJson(node.storageAllowed),
        "created" -> Json.toJson(node.created),
        "updated" -> Json.toJson(node.updated)
      )
  }

  implicit val manifestFormat: Format[Manifest] = Json.format[Manifest]

  implicit val crateStorageRequestFormat: Format[CrateStorageRequest] = Json.format[CrateStorageRequest]

  implicit val crateStorageReservationFormat: Format[CrateStorageReservation] = Json.format[CrateStorageReservation]

  implicit val serviceApiEndpointApiFormat: Format[ServiceApiEndpoint.Api] =
    Json.format[ServiceApiEndpoint.Api]

  implicit val serviceApiEndpointCoreFormat: Format[ServiceApiEndpoint.Core] =
    Json.format[ServiceApiEndpoint.Core]

  implicit val serviceApiEndpointDiscoveryFormat: Format[ServiceApiEndpoint.Discovery] =
    Json.format[ServiceApiEndpoint.Discovery]

  implicit val serviceApiEndpointFormat: Format[ServiceApiEndpoint] = Format(
    _.validate[JsObject].flatMap { endpoint =>
      (endpoint \ "endpoint_type").validate[String].map {
        case "api"       => endpoint.as[ServiceApiEndpoint.Api]
        case "core"      => endpoint.as[ServiceApiEndpoint.Core]
        case "discovery" => endpoint.as[ServiceApiEndpoint.Discovery]
      }
    },
    {
      case endpoint: ServiceApiEndpoint.Api =>
        serviceApiEndpointApiFormat.writes(endpoint).as[JsObject] ++ Json.obj(
          "endpoint_type" -> Json.toJson("api")
        )

      case endpoint: ServiceApiEndpoint.Core =>
        serviceApiEndpointCoreFormat.writes(endpoint).as[JsObject] ++ Json.obj(
          "endpoint_type" -> Json.toJson("core")
        )

      case endpoint: ServiceApiEndpoint.Discovery =>
        serviceApiEndpointDiscoveryFormat.writes(endpoint).as[JsObject] ++ Json.obj(
          "endpoint_type" -> Json.toJson("discovery")
        )
    }
  )

  implicit val serviceDiscoveryRequestFormat: Format[ServiceDiscoveryRequest] =
    Json.format[ServiceDiscoveryRequest]

  implicit val serviceDiscoveryResultEndpointsFormat: Format[ServiceDiscoveryResult.Endpoints] =
    Json.format[ServiceDiscoveryResult.Endpoints]

  implicit val serviceDiscoveryResultSwitchToFormat: Format[ServiceDiscoveryResult.SwitchTo] =
    Json.format[ServiceDiscoveryResult.SwitchTo]

  implicit val serviceDiscoveryResultFormat: Format[ServiceDiscoveryResult] = Format(
    _.validate[JsObject].flatMap { result =>
      (result \ "result").validate[String].map {
        case "keep-existing" => ServiceDiscoveryResult.KeepExisting
        case "switch-to"     => result.as[ServiceDiscoveryResult.SwitchTo]
      }
    },
    {
      case ServiceDiscoveryResult.KeepExisting =>
        Json.obj("result" -> Json.toJson("keep-existing"))

      case result: ServiceDiscoveryResult.SwitchTo =>
        serviceDiscoveryResultSwitchToFormat.writes(result).as[JsObject] ++ Json.obj(
          "result" -> Json.toJson("switch-to")
        )
    }
  )
}
