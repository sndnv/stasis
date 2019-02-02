package stasis.core.persistence.nodes

import scala.concurrent.Future

import akka.Done
import stasis.core.persistence.backends.KeyValueBackend
import stasis.core.routing.Node

trait NodeStore { store =>
  def put(node: Node): Future[Done]
  def delete(node: Node.Id): Future[Boolean]
  def get(node: Node.Id): Future[Option[Node]]
  def nodes: Future[Map[Node.Id, Node]]

  def view: NodeStoreView = new NodeStoreView {
    override def get(node: Node.Id): Future[Option[Node]] = store.get(node)
    override def nodes: Future[Map[Node.Id, Node]] = store.nodes
  }
}

object NodeStore {
  def apply(backend: KeyValueBackend[Node.Id, Node]): NodeStore = new NodeStore {
    override def put(node: Node): Future[Done] = backend.put(node.id, node)
    override def delete(node: Node.Id): Future[Boolean] = backend.delete(node)
    override def get(node: Node.Id): Future[Option[Node]] = backend.get(node)
    override def nodes: Future[Map[Node.Id, Node]] = backend.entries
  }
}
