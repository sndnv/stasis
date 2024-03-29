package stasis.core.routing

import stasis.core.networking.EndpointAddress
import stasis.core.networking.grpc.GrpcEndpointAddress
import stasis.core.networking.http.HttpEndpointAddress
import stasis.core.persistence.crates.CrateStore

sealed trait Node {
  def id: Node.Id
  def storageAllowed: Boolean
}

object Node {
  type Id = java.util.UUID

  def generateId(): Id = java.util.UUID.randomUUID()

  final case class Local(
    override val id: Node.Id,
    storeDescriptor: CrateStore.Descriptor
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
      override val storageAllowed: Boolean
    ) extends Remote[HttpEndpointAddress]

    final case class Grpc(
      override val id: Node.Id,
      override val address: GrpcEndpointAddress,
      override val storageAllowed: Boolean
    ) extends Remote[GrpcEndpointAddress]
  }
}
