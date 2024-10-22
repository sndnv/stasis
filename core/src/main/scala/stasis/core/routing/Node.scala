package stasis.core.routing

import java.time.Instant

import stasis.core.networking.EndpointAddress
import stasis.core.networking.grpc.GrpcEndpointAddress
import stasis.core.networking.http.HttpEndpointAddress
import stasis.core.persistence.crates.CrateStore

sealed trait Node {
  def id: Node.Id
  def storageAllowed: Boolean
  def created: Instant
  def updated: Instant
}

object Node {
  type Id = java.util.UUID

  def generateId(): Id = java.util.UUID.randomUUID()

  @SuppressWarnings(Array("org.wartremover.warts.Throw"))
  def apply(
    id: Node.Id,
    nodeType: String,
    storageAllowed: Boolean,
    address: Option[EndpointAddress],
    descriptor: Option[CrateStore.Descriptor],
    created: Instant,
    updated: Instant
  ): Node =
    (nodeType, address, descriptor) match {
      case ("local", None, Some(descriptor)) =>
        Node.Local(id = id, storeDescriptor = descriptor, created = created, updated = updated)

      case ("remote-http", Some(address: HttpEndpointAddress), None) =>
        Node.Remote.Http(id = id, address = address, storageAllowed = storageAllowed, created = created, updated = updated)

      case ("remote-grpc", Some(address: GrpcEndpointAddress), None) =>
        Node.Remote.Grpc(id = id, address = address, storageAllowed = storageAllowed, created = created, updated = updated)

      case _ =>
        throw new IllegalArgumentException(
          s"Unexpected address [${address.toString}] and descriptor [${descriptor.toString}] provided for node [$nodeType / ${id.toString}]"
        )
    }

  def unapply(
    node: Node
  ): Option[(Node.Id, String, Boolean, Option[EndpointAddress], Option[CrateStore.Descriptor], Instant, Instant)] =
    Some(
      node match {
        case local: Local =>
          (node.id, "local", node.storageAllowed, None, Some(local.storeDescriptor), node.created, node.updated)
        case remote: Remote.Http =>
          (node.id, "remote-http", node.storageAllowed, Some(remote.address), None, node.created, node.updated)
        case remote: Remote.Grpc =>
          (node.id, "remote-grpc", node.storageAllowed, Some(remote.address), None, node.created, node.updated)
      }
    )

  final case class Local(
    override val id: Node.Id,
    storeDescriptor: CrateStore.Descriptor,
    override val created: Instant,
    override val updated: Instant
  ) extends Node {
    override val storageAllowed: Boolean = true
  }

  sealed trait Remote[A <: EndpointAddress] extends Node {
    def address: A
  }

  object Remote {
    final case class Http(
      override val id: Node.Id,
      override val address: HttpEndpointAddress,
      override val storageAllowed: Boolean,
      override val created: Instant,
      override val updated: Instant
    ) extends Remote[HttpEndpointAddress]

    final case class Grpc(
      override val id: Node.Id,
      override val address: GrpcEndpointAddress,
      override val storageAllowed: Boolean,
      override val created: Instant,
      override val updated: Instant
    ) extends Remote[GrpcEndpointAddress]
  }
}
