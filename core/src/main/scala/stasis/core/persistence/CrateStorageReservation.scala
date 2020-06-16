package stasis.core.persistence

import stasis.core.packaging.Crate
import stasis.core.routing.Node

final case class CrateStorageReservation(
  id: CrateStorageReservation.Id,
  crate: Crate.Id,
  size: Long,
  copies: Int,
  origin: Node.Id,
  target: Node.Id
) {
  override def toString: String =
    s"""
       |CrateStorageReservation(
       |  id=${id.toString},
       |  crate=${crate.toString},
       |  size=${size.toString},
       |  copies=${copies.toString},
       |  origin=${origin.toString},
       |  target=${target.toString}
       |)
     """.stripMargin.replaceAll("\n", "").replaceAll(" ", "").trim
}

object CrateStorageReservation {
  def apply(
    request: CrateStorageRequest,
    target: Node.Id
  ): CrateStorageReservation =
    new CrateStorageReservation(
      id = generateId(),
      crate = request.crate,
      size = request.size,
      copies = request.copies,
      origin = request.origin,
      target = target
    )

  type Id = java.util.UUID

  def generateId(): Id = java.util.UUID.randomUUID()
}
