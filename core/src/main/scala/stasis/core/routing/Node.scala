package stasis.core.routing

import stasis.core.networking.EndpointAddress
import stasis.core.networking.grpc.GrpcEndpointAddress
import stasis.core.networking.http.HttpEndpointAddress
import stasis.core.persistence.crates.CrateStore

sealed trait Node {
  def id: Node.Id
}

object Node {
  type Id = java.util.UUID

  def generateId(): Id = java.util.UUID.randomUUID()

  final case class Local(override val id: Node.Id, crateStore: CrateStore) extends Node

  sealed trait Remote[A <: EndpointAddress] extends Node {
    def address: A
  }

  object Remote {
    final case class Http(override val id: Node.Id, override val address: HttpEndpointAddress)
        extends Remote[HttpEndpointAddress]

    final case class Grpc(override val id: Node.Id, override val address: GrpcEndpointAddress)
        extends Remote[GrpcEndpointAddress]
  }
}
