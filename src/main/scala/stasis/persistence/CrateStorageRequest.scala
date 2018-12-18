package stasis.persistence

import stasis.packaging.{Crate, Manifest}
import stasis.routing.Node

import scala.concurrent.duration.FiniteDuration

final case class CrateStorageRequest(
  id: CrateStorageRequest.Id,
  crate: Crate.Id,
  size: Long,
  copies: Int,
  retention: FiniteDuration,
  origin: Node.Id,
  source: Node.Id
)

object CrateStorageRequest {
  def apply(
    crate: Crate.Id,
    size: Long,
    copies: Int,
    retention: FiniteDuration,
    origin: Node.Id,
    source: Node.Id
  ): CrateStorageRequest =
    new CrateStorageRequest(id = generateId(), crate, size, copies, retention, origin, source)

  def apply(manifest: Manifest): CrateStorageRequest = CrateStorageRequest(
    crate = manifest.crate,
    size = manifest.size,
    copies = manifest.copies,
    retention = manifest.retention,
    origin = manifest.origin,
    source = manifest.source
  )

  type Id = java.util.UUID

  def generateId(): Id = java.util.UUID.randomUUID()

}
