package stasis.identity.model.realms

import akka.Done
import stasis.core.persistence.backends.KeyValueBackend

import scala.concurrent.Future

trait RealmStore { store =>
  def put(realm: Realm): Future[Done]
  def delete(realm: Realm.Id): Future[Boolean]
  def get(realm: Realm.Id): Future[Option[Realm]]
  def realms: Future[Map[Realm.Id, Realm]]
  def contains(realm: Realm.Id): Future[Boolean]

  def view: RealmStoreView = new RealmStoreView {
    override def get(realm: Realm.Id): Future[Option[Realm]] = store.get(realm)
    override def realms: Future[Map[Realm.Id, Realm]] = store.realms
    override def contains(realm: Realm.Id): Future[Boolean] = store.contains(realm)
  }
}

object RealmStore {
  def apply(backend: KeyValueBackend[Realm.Id, Realm]): RealmStore = new RealmStore {
    override def put(realm: Realm): Future[Done] = backend.put(realm.id, realm)
    override def delete(realm: Realm.Id): Future[Boolean] = backend.delete(realm)
    override def get(realm: Realm.Id): Future[Option[Realm]] = backend.get(realm)
    override def realms: Future[Map[Realm.Id, Realm]] = backend.entries
    override def contains(realm: Realm.Id): Future[Boolean] = backend.contains(realm)
  }
}
