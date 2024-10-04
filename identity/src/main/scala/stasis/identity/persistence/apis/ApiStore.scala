package stasis.identity.persistence.apis

import scala.concurrent.Future

import org.apache.pekko.Done

import stasis.identity.model.apis.Api
import stasis.layers.persistence.Store

trait ApiStore extends Store { store =>
  def put(api: Api): Future[Done]
  def delete(api: Api.Id): Future[Boolean]
  def get(api: Api.Id): Future[Option[Api]]
  def all: Future[Seq[Api]]
  def contains(api: Api.Id): Future[Boolean]

  def view: ApiStore.View =
    new ApiStore.View {
      override def get(api: Api.Id): Future[Option[Api]] = store.get(api)
      override def all: Future[Seq[Api]] = store.all
      override def contains(api: Api.Id): Future[Boolean] = store.contains(api)
    }
}

object ApiStore {
  trait View {
    def get(api: Api.Id): Future[Option[Api]]
    def all: Future[Seq[Api]]
    def contains(api: Api.Id): Future[Boolean]
  }
}
