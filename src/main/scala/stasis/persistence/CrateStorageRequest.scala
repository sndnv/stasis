package stasis.persistence

import stasis.packaging.Manifest

import scala.concurrent.duration.FiniteDuration

final case class CrateStorageRequest(
  id: CrateStorageRequest.Id,
  size: Long,
  copies: Int,
  retention: FiniteDuration
)

object CrateStorageRequest {
  def apply(
    size: Long,
    copies: Int,
    retention: FiniteDuration
  ): CrateStorageRequest = new CrateStorageRequest(id = generateId(), size, copies, retention)

  def apply(manifest: Manifest): CrateStorageRequest = CrateStorageRequest(
    size = manifest.size,
    copies = manifest.copies,
    retention = manifest.retention
  )

  type Id = java.util.UUID

  def generateId(): Id = java.util.UUID.randomUUID()

}
