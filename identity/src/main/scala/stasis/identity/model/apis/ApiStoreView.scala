package stasis.identity.model.apis

import scala.concurrent.Future

trait ApiStoreView {
  def get(api: Api.Id): Future[Option[Api]]
  def apis: Future[Map[Api.Id, Api]]
  def contains(api: Api.Id): Future[Boolean]
}
