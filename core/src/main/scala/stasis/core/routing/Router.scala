package stasis.core.routing

import akka.stream.scaladsl.Source
import akka.util.ByteString
import akka.{Done, NotUsed}
import stasis.core.packaging.{Crate, Manifest}
import stasis.core.persistence.{CrateStorageRequest, CrateStorageReservation}

import scala.concurrent.Future

trait Router {
  def push(
    manifest: Manifest,
    content: Source[ByteString, NotUsed]
  ): Future[Done]

  def pull(
    crate: Crate.Id
  ): Future[Option[Source[ByteString, NotUsed]]]

  def discard(
    crate: Crate.Id
  ): Future[Done]

  def reserve(
    request: CrateStorageRequest
  ): Future[Option[CrateStorageReservation]]
}
