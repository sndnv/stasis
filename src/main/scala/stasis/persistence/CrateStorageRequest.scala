package stasis.persistence

import stasis.packaging.Manifest
import stasis.routing.Node

import scala.concurrent.duration.FiniteDuration

final case class CrateStorageRequest(
  id: CrateStorageRequest.Id,
  size: Long,
  copies: Int,
  retention: FiniteDuration,
  origin: Node.Id,
  source: Node.Id
)

object CrateStorageRequest {
  def apply(
    size: Long,
    copies: Int,
    retention: FiniteDuration,
    origin: Node.Id,
    source: Node.Id
  ): CrateStorageRequest =
    new CrateStorageRequest(id = generateId(), size, copies, retention, origin, source)

  def apply(manifest: Manifest): CrateStorageRequest = CrateStorageRequest(
    size = manifest.size,
    copies = manifest.copies,
    retention = manifest.retention,
    origin = manifest.origin,
    source = manifest.source
  )

  type Id = java.util.UUID

  def generateId(): Id = java.util.UUID.randomUUID()

}
