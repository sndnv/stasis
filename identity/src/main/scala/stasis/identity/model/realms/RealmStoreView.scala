package stasis.identity.model.realms

import scala.concurrent.Future

trait RealmStoreView {
  def get(realm: Realm.Id): Future[Option[Realm]]
  def realms: Future[Map[Realm.Id, Realm]]
}
