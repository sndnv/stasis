package stasis.routing

import java.util.UUID

object Node {
  type Id = UUID

  def generateId(): Id = java.util.UUID.randomUUID()
}
