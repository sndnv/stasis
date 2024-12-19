package stasis.core.packaging

import java.time.Instant

import stasis.core.persistence.CrateStorageReservation
import stasis.core.routing.Node

/**
  * Crate manifest.
  *
  * @param crate crate ID
  * @param size data size (in bytes)
  * @param copies data copies
  * @param origin original data owner (node from which the data originates)
  * @param source current source (node from which the current node received the data)
  * @param destinations destination nodes (nodes to which the current node has sent the data)
  * @param created creation timestamp
  */
final case class Manifest(
  crate: Crate.Id,
  size: Long,
  copies: Int,
  origin: Node.Id,
  source: Node.Id,
  destinations: Seq[Node.Id],
  created: Instant
) {
  override def toString: String =
    s"""
       |Manifest(
       |  crate=${crate.toString},
       |  size=${size.toString},
       |  copies=${copies.toString},
       |  origin=${origin.toString},
       |  source=${source.toString},
       |  destinations=[${destinations.mkString(",")}],
       |  created=${created.toString}
       |)
     """.stripMargin.replaceAll("\n", "").replaceAll(" ", "").trim
}

object Manifest {
  def create(
    crate: Crate.Id,
    size: Long,
    copies: Int,
    origin: Node.Id,
    source: Node.Id,
    destinations: Seq[Node.Id],
    created: Instant
  ): Manifest =
    Manifest(
      crate = crate,
      size = size,
      copies = copies,
      origin = origin,
      source = source,
      destinations = destinations,
      created = created
    )

  def apply(source: Node.Id, reservation: CrateStorageReservation): Manifest =
    Manifest(
      crate = reservation.crate,
      size = reservation.size,
      copies = reservation.copies,
      origin = reservation.origin,
      source = source,
      destinations = Seq.empty,
      created = Instant.now()
    )

  def apply(
    crate: Crate.Id,
    origin: Node.Id,
    source: Node.Id,
    size: Long,
    copies: Int
  ): Manifest =
    Manifest(
      crate = crate,
      size = size,
      copies = copies,
      origin = origin,
      source = source,
      destinations = Seq.empty,
      created = Instant.now()
    )
}
