package stasis.identity.model.owners

import scala.concurrent.Future

trait ResourceOwnerStoreView {
  def get(owner: ResourceOwner.Id): Future[Option[ResourceOwner]]
  def owners: Future[Map[ResourceOwner.Id, ResourceOwner]]
  def contains(owner: ResourceOwner.Id): Future[Boolean]
}
