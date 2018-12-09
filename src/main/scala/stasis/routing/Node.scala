package stasis.routing

import java.util.UUID

import stasis.networking.EndpointAddress
import stasis.persistence.CrateStore

import scala.reflect.ClassTag

sealed trait Node {
  val id: Node.Id
}

object Node {
  type Id = UUID

  def generateId(): Id = java.util.UUID.randomUUID()

  final case class Local(override val id: Node.Id, crateStore: CrateStore) extends Node
  final case class Remote[A <: EndpointAddress: ClassTag](override val id: Node.Id, address: A) extends Node
}
