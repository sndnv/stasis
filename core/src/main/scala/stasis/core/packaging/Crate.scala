package stasis.core.packaging

object Crate {
  type Id = java.util.UUID

  def generateId(): Id = java.util.UUID.randomUUID()
}
