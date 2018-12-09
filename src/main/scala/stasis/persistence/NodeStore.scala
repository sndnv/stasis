package stasis.persistence

import akka.Done
import stasis.routing.Node

import scala.concurrent.Future

trait NodeStore {
  def put(node: Node): Future[Done]
  def get(node: Node.Id): Future[Option[Node]]
  def nodes: Future[Seq[Node]]
}
