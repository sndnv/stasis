package stasis.core.persistence.nodes

import stasis.core.routing.Node

import scala.concurrent.Future

trait NodeStoreView {
  def get(node: Node.Id): Future[Option[Node]]
  def contains(node: Node.Id): Future[Boolean]
  def nodes: Future[Map[Node.Id, Node]]
}
