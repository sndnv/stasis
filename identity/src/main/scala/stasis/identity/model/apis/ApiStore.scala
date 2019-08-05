package stasis.identity.model.apis

import akka.Done
import stasis.core.persistence.backends.KeyValueBackend

import scala.concurrent.Future

trait ApiStore { store =>
  def put(api: Api): Future[Done]
  def delete(api: Api.Id): Future[Boolean]
  def get(api: Api.Id): Future[Option[Api]]
  def apis: Future[Map[Api.Id, Api]]
  def contains(api: Api.Id): Future[Boolean]

  def view: ApiStoreView = new ApiStoreView {
    override def get(api: Api.Id): Future[Option[Api]] = store.get(api)
    override def apis: Future[Map[Api.Id, Api]] = store.apis
    override def contains(api: Api.Id): Future[Boolean] = store.contains(api)
  }
}

object ApiStore {
  def apply(backend: KeyValueBackend[Api.Id, Api]): ApiStore = new ApiStore {
    override def put(api: Api): Future[Done] = backend.put(api.id, api)
    override def delete(api: Api.Id): Future[Boolean] = backend.delete(api)
    override def get(api: Api.Id): Future[Option[Api]] = backend.get(api)
    override def apis: Future[Map[Api.Id, Api]] = backend.entries
    override def contains(api: Api.Id): Future[Boolean] = backend.contains(api)
  }
}
