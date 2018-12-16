package stasis.persistence.crates

import akka.stream.scaladsl.{Sink, Source}
import akka.util.ByteString
import akka.{Done, NotUsed}
import stasis.packaging.Crate.Id
import stasis.packaging.{Crate, Manifest}
import stasis.persistence.{CrateStorageRequest, CrateStorageReservation}

import scala.concurrent.Future

trait CrateStore { store =>
  def persist(manifest: Manifest, content: Source[ByteString, NotUsed]): Future[Done]
  def sink(manifest: Manifest): Future[Sink[ByteString, Future[Done]]]
  def retrieve(crate: Crate.Id): Future[Option[Source[ByteString, NotUsed]]]
  def reserve(request: CrateStorageRequest): Future[Option[CrateStorageReservation]]

  def view: CrateStoreView = new CrateStoreView {
    override def retrieve(crate: Id): Future[Option[Source[ByteString, NotUsed]]] = store.retrieve(crate)
  }
}
