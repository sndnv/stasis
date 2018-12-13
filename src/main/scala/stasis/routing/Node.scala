package stasis.routing

import java.util.UUID

import stasis.networking.EndpointAddress
import stasis.networking.http.HttpEndpointAddress
import stasis.persistence.crates.CrateStore

sealed trait Node {
  val id: Node.Id
}

object Node {
  type Id = UUID

  def generateId(): Id = java.util.UUID.randomUUID()

  final case class Local(override val id: Node.Id, crateStore: CrateStore) extends Node

  sealed trait Remote[A <: EndpointAddress] extends Node {
    def address: A
  }

  object Remote {
    final case class Http(override val id: Node.Id, override val address: HttpEndpointAddress)
        extends Remote[HttpEndpointAddress]
  }
}
