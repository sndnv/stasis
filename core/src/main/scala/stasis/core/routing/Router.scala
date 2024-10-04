package stasis.core.routing

import scala.concurrent.Future

import org.apache.pekko.stream.scaladsl.Source
import org.apache.pekko.util.ByteString
import org.apache.pekko.Done
import org.apache.pekko.NotUsed

import stasis.core.packaging.Crate
import stasis.core.packaging.Manifest
import stasis.core.persistence.CrateStorageRequest
import stasis.core.persistence.CrateStorageReservation

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
