package stasis.core.persistence.nodes

import scala.concurrent.Future

import stasis.core.routing.Node

trait NodeStoreView {
  def get(node: Node.Id): Future[Option[Node]]
  def contains(node: Node.Id): Future[Boolean]
  def nodes: Future[Map[Node.Id, Node]]
}
