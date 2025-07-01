package stasis.identity.persistence.clients

import scala.concurrent.Future

import org.apache.pekko.Done

import stasis.identity.model.clients.Client
import io.github.sndnv.layers.persistence.Store

trait ClientStore extends Store { store =>
  def put(client: Client): Future[Done]
  def delete(client: Client.Id): Future[Boolean]
  def get(client: Client.Id): Future[Option[Client]]
  def all: Future[Seq[Client]]

  def view: ClientStore.View =
    new ClientStore.View {
      override def get(client: Client.Id): Future[Option[Client]] = store.get(client)
      override def all: Future[Seq[Client]] = store.all
    }
}

object ClientStore {
  trait View {
    def get(client: Client.Id): Future[Option[Client]]
    def all: Future[Seq[Client]]
  }
}
