package stasis.core.persistence

import stasis.core.packaging.Crate
import stasis.core.packaging.Manifest
import stasis.core.routing.Node

final case class CrateStorageRequest(
  id: CrateStorageRequest.Id,
  crate: Crate.Id,
  size: Long,
  copies: Int,
  origin: Node.Id,
  source: Node.Id
) {
  override def toString: String =
    s"""
       |CrateStorageRequest(
       |  id=${id.toString},
       |  crate=${crate.toString},
       |  size=${size.toString},
       |  copies=${copies.toString},
       |  origin=${origin.toString},
       |  source=${source.toString}
       |)
     """.stripMargin.replaceAll("\n", "").replaceAll(" ", "").trim
}

object CrateStorageRequest {
  def apply(
    crate: Crate.Id,
    size: Long,
    copies: Int,
    origin: Node.Id,
    source: Node.Id
  ): CrateStorageRequest =
    new CrateStorageRequest(id = generateId(), crate, size, copies, origin, source)

  def apply(manifest: Manifest): CrateStorageRequest =
    CrateStorageRequest(
      crate = manifest.crate,
      size = manifest.size,
      copies = manifest.copies,
      origin = manifest.origin,
      source = manifest.source
    )

  type Id = java.util.UUID

  def generateId(): Id = java.util.UUID.randomUUID()
}
