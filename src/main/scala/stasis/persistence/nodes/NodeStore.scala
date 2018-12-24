package stasis.persistence.nodes

import akka.Done
import stasis.routing.Node
import stasis.routing.Node.Id

import scala.concurrent.Future

trait NodeStore { store =>
  def put(node: Node): Future[Done]
  def delete(node: Node.Id): Future[Boolean]
  def get(node: Node.Id): Future[Option[Node]]
  def nodes: Future[Map[Node.Id, Node]]

  def view: NodeStoreView = new NodeStoreView {
    override def get(node: Id): Future[Option[Node]] = store.get(node)
    override def nodes: Future[Map[Node.Id, Node]] = store.nodes
  }
}
