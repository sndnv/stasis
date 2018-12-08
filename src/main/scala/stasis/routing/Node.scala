package stasis.routing

import java.util.UUID

final case class Node(
  id: Node.Id
)

object Node {
  type Id = UUID

  def generateId(): Id = java.util.UUID.randomUUID()
}
