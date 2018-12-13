package stasis.persistence.nodes

import stasis.routing.Node

import scala.concurrent.Future

trait NodeStoreView {
  def get(node: Node.Id): Future[Option[Node]]
  def nodes: Future[Map[Node.Id, Node]]
}
