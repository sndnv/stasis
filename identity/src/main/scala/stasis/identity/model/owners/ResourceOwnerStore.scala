package stasis.identity.model.owners

import akka.Done
import stasis.core.persistence.backends.KeyValueBackend

import scala.concurrent.Future

trait ResourceOwnerStore { store =>
  def put(owner: ResourceOwner): Future[Done]
  def delete(owner: ResourceOwner.Id): Future[Boolean]
  def get(owner: ResourceOwner.Id): Future[Option[ResourceOwner]]
  def owners: Future[Map[ResourceOwner.Id, ResourceOwner]]
  def contains(owner: ResourceOwner.Id): Future[Boolean]

  def view: ResourceOwnerStoreView =
    new ResourceOwnerStoreView {
      override def get(owner: ResourceOwner.Id): Future[Option[ResourceOwner]] = store.get(owner)
      override def owners: Future[Map[ResourceOwner.Id, ResourceOwner]] = store.owners
      override def contains(owner: ResourceOwner.Id): Future[Boolean] = store.contains(owner)
    }
}

object ResourceOwnerStore {
  def apply(backend: KeyValueBackend[ResourceOwner.Id, ResourceOwner]): ResourceOwnerStore =
    new ResourceOwnerStore {
      override def put(owner: ResourceOwner): Future[Done] = backend.put(owner.username, owner)
      override def delete(owner: ResourceOwner.Id): Future[Boolean] = backend.delete(owner)
      override def get(owner: ResourceOwner.Id): Future[Option[ResourceOwner]] = backend.get(owner)
      override def owners: Future[Map[ResourceOwner.Id, ResourceOwner]] = backend.entries
      override def contains(owner: ResourceOwner.Id): Future[Boolean] = backend.contains(owner)
    }
}
