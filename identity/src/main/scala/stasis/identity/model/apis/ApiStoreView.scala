package stasis.identity.model.apis

import stasis.identity.model.realms.Realm

import scala.concurrent.Future

trait ApiStoreView {
  def get(realm: Realm.Id, api: Api.Id): Future[Option[Api]]
  def apis: Future[Map[(Realm.Id, Api.Id), Api]]
  def contains(realm: Realm.Id, api: Api.Id): Future[Boolean]
}
