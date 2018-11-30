package stasis.packaging

import java.util.UUID

object Crate {
  type Id = UUID

  def generateId(): Id = java.util.UUID.randomUUID()
}
