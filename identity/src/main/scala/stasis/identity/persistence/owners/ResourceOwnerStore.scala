package stasis.identity.persistence.owners

import scala.concurrent.Future

import org.apache.pekko.Done

import stasis.identity.model.owners.ResourceOwner
import io.github.sndnv.layers.persistence.Store

trait ResourceOwnerStore extends Store { store =>
  def put(owner: ResourceOwner): Future[Done]
  def delete(owner: ResourceOwner.Id): Future[Boolean]
  def get(owner: ResourceOwner.Id): Future[Option[ResourceOwner]]
  def all: Future[Seq[ResourceOwner]]
  def contains(owner: ResourceOwner.Id): Future[Boolean]

  def view: ResourceOwnerStore.View =
    new ResourceOwnerStore.View {
      override def get(owner: ResourceOwner.Id): Future[Option[ResourceOwner]] = store.get(owner)
      override def all: Future[Seq[ResourceOwner]] = store.all
      override def contains(owner: ResourceOwner.Id): Future[Boolean] = store.contains(owner)
    }
}

object ResourceOwnerStore {
  trait View {
    def get(owner: ResourceOwner.Id): Future[Option[ResourceOwner]]
    def all: Future[Seq[ResourceOwner]]
    def contains(owner: ResourceOwner.Id): Future[Boolean]
  }
}
