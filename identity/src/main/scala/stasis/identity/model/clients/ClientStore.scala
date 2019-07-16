package stasis.identity.model.clients

import akka.Done
import stasis.core.persistence.backends.KeyValueBackend

import scala.concurrent.Future

trait ClientStore { store =>
  def put(client: Client): Future[Done]
  def delete(client: Client.Id): Future[Boolean]
  def get(client: Client.Id): Future[Option[Client]]
  def clients: Future[Map[Client.Id, Client]]

  def view: ClientStoreView = new ClientStoreView {
    override def get(client: Client.Id): Future[Option[Client]] = store.get(client)
    override def clients: Future[Map[Client.Id, Client]] = store.clients
  }
}

object ClientStore {
  def apply(backend: KeyValueBackend[Client.Id, Client]): ClientStore = new ClientStore {
    override def put(client: Client): Future[Done] = backend.put(client.id, client)
    override def delete(client: Client.Id): Future[Boolean] = backend.delete(client)
    override def get(client: Client.Id): Future[Option[Client]] = backend.get(client)
    override def clients: Future[Map[Client.Id, Client]] = backend.entries
  }
}
