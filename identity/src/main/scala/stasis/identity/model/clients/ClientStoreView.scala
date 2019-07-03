package stasis.identity.model.clients

import scala.concurrent.Future

trait ClientStoreView {
  def get(client: Client.Id): Future[Option[Client]]
  def clients: Future[Map[Client.Id, Client]]
}
