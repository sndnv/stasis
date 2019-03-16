package stasis.core.persistence

import stasis.core.packaging.{Crate, Manifest}
import stasis.core.routing.Node

import scala.concurrent.duration.FiniteDuration

final case class CrateStorageRequest(
  id: CrateStorageRequest.Id,
  crate: Crate.Id,
  size: Long,
  copies: Int,
  origin: Node.Id,
  source: Node.Id
)

object CrateStorageRequest {
  def apply(
    crate: Crate.Id,
    size: Long,
    copies: Int,
    origin: Node.Id,
    source: Node.Id
  ): CrateStorageRequest =
    new CrateStorageRequest(id = generateId(), crate, size, copies, origin, source)

  def apply(manifest: Manifest): CrateStorageRequest = CrateStorageRequest(
    crate = manifest.crate,
    size = manifest.size,
    copies = manifest.copies,
    origin = manifest.origin,
    source = manifest.source
  )

  type Id = java.util.UUID

  def generateId(): Id = java.util.UUID.randomUUID()

}
