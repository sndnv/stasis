package stasis.server.service.bootstrap

import java.time.Instant
import java.util.UUID

import scala.concurrent.Future

import com.typesafe.config.Config
import org.apache.pekko.Done

import stasis.core.networking.EndpointAddress
import stasis.core.networking.grpc.GrpcEndpointAddress
import stasis.core.networking.http.HttpEndpointAddress
import stasis.core.persistence.crates.CrateStore
import stasis.core.persistence.nodes.NodeStore
import stasis.core.routing.Node
import stasis.layers.service.bootstrap.BootstrapEntityProvider

class NodeBootstrapEntityProvider(store: NodeStore) extends BootstrapEntityProvider[Node] {
  override val name: String = "nodes"

  override def default: Seq[Node] =
    Seq.empty

  override def load(config: Config): Node = {
    val now = Instant.now()
    config.getString("type").toLowerCase match {
      case "local" =>
        Node.Local(
          id = UUID.fromString(config.getString("id")),
          storeDescriptor = CrateStore.Descriptor(config.getConfig("store")),
          created = now,
          updated = now
        )

      case "remote-http" =>
        Node.Remote.Http(
          id = UUID.fromString(config.getString("id")),
          address = HttpEndpointAddress(config.getString("address")),
          storageAllowed = config.getBoolean("storage-allowed"),
          created = now,
          updated = now
        )

      case "remote-grpc" =>
        Node.Remote.Grpc(
          id = UUID.fromString(config.getString("id")),
          address = GrpcEndpointAddress(
            host = config.getString("address.host"),
            port = config.getInt("address.port"),
            tlsEnabled = config.getBoolean("address.tls-enabled")
          ),
          storageAllowed = config.getBoolean("storage-allowed"),
          created = now,
          updated = now
        )
    }
  }

  override def validate(entities: Seq[Node]): Future[Done] =
    requireNonDuplicateField(entities, _.id)

  override def create(entity: Node): Future[Done] =
    store.put(entity)

  override def render(entity: Node, withPrefix: String): String = {
    val (id, nodeType, storageAllowed, address, storeDescriptor, created, updated) =
      extractNodeInformation(entity.id, Some(entity))

    val addressAsString = address.collect {
      case HttpEndpointAddress(uri)                    => uri.toString()
      case GrpcEndpointAddress(host, port, tlsEnabled) => s"$host:${port.toString}, tls-enabled=${tlsEnabled.toString}"
    }

    s"""
       |$withPrefix  node:
       |$withPrefix    id:               ${id.toString}
       |$withPrefix    type:             $nodeType
       |$withPrefix    storage-allowed:  ${storageAllowed.toString}
       |$withPrefix    address:          ${addressAsString.getOrElse("-")}
       |$withPrefix    store-descriptor: ${storeDescriptor.map(_.toString).getOrElse("-")}
       |$withPrefix    created:          ${created.toString}
       |$withPrefix    updated:          ${updated.toString}""".stripMargin
  }

  override def extractId(entity: Node): String =
    entity.id.toString

  @SuppressWarnings(Array("org.wartremover.warts.Throw"))
  def extractNodeInformation(
    id: Node.Id,
    entity: Option[Node]
  ): (Node.Id, String, Boolean, Option[EndpointAddress], Option[CrateStore.Descriptor], Instant, Instant) = {
    val result = entity.collect { case Node(id, nodeType, storageAllowed, address, storeDescriptor, created, updated) =>
      (id, nodeType, storageAllowed, address, storeDescriptor, created, updated)
    }

    result match {
      case Some(values) => values
      case None         => throw new IllegalArgumentException(s"Failed to extract information from node [${id.toString}]")
    }
  }
}
