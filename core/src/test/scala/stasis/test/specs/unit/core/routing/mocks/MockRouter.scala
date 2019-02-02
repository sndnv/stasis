package stasis.test.specs.unit.core.routing.mocks

import akka.stream.scaladsl.Source
import akka.util.ByteString
import akka.{Done, NotUsed}
import stasis.core.packaging.{Crate, Manifest}
import stasis.core.persistence.crates.CrateStore
import stasis.core.persistence.{CrateStorageRequest, CrateStorageReservation}
import stasis.core.routing.Router
import scala.concurrent.{ExecutionContext, Future}

import stasis.core.packaging.Crate.Id
import stasis.core.routing.exceptions.DiscardFailure

class MockRouter(store: CrateStore)(implicit ec: ExecutionContext) extends Router {
  override def push(
    manifest: Manifest,
    content: Source[ByteString, NotUsed]
  ): Future[Done] = store.persist(manifest, content)

  override def pull(
    crate: Crate.Id
  ): Future[Option[Source[ByteString, NotUsed]]] = store.retrieve(crate)

  override def discard(crate: Id): Future[Done] = store.discard(crate).flatMap {
    case true  => Future.successful(Done)
    case false => Future.failed(DiscardFailure(s"Backing store could not find crate [$crate]"))
  }

  override def reserve(request: CrateStorageRequest): Future[Option[CrateStorageReservation]] =
    store.reserve(request)
}
