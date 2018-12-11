package stasis.persistence

import scala.concurrent.duration.FiniteDuration

final case class CrateStorageRequest(
  id: CrateStorageRequest.Id,
  size: Long,
  copies: Int,
  retention: FiniteDuration
)

object CrateStorageRequest {
  type Id = java.util.UUID

  def generateId(): Id = java.util.UUID.randomUUID()

}
