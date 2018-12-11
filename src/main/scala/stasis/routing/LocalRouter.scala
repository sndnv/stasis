package stasis.routing

import akka.stream.scaladsl.Source
import akka.util.ByteString
import akka.{Done, NotUsed}
import stasis.packaging.{Crate, Manifest}
import stasis.persistence.{CrateStorageRequest, CrateStorageReservation, CrateStore}

import scala.concurrent.{ExecutionContext, Future}

class LocalRouter(store: CrateStore)(implicit ec: ExecutionContext) extends Router {
  override def push(
    manifest: Manifest,
    content: Source[ByteString, NotUsed]
  ): Future[Done] = store.persist(manifest, content)

  override def pull(
    crate: Crate.Id
  ): Future[Option[Source[ByteString, NotUsed]]] = store.retrieve(crate)

  override def reserve(request: CrateStorageRequest): Future[Option[CrateStorageReservation]] =
    store.reserve(request)
}
