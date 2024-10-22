package stasis.core.persistence.nodes

import scala.concurrent.Future

import org.apache.pekko.Done

import stasis.core.routing.Node
import stasis.layers.persistence.Store

trait NodeStore extends Store { store =>
  def put(node: Node): Future[Done]
  def delete(node: Node.Id): Future[Boolean]
  def get(node: Node.Id): Future[Option[Node]]
  def contains(node: Node.Id): Future[Boolean]
  def nodes: Future[Map[Node.Id, Node]]

  def view: NodeStore.View =
    new NodeStore.View {
      override def get(node: Node.Id): Future[Option[Node]] = store.get(node)
      override def contains(node: Node.Id): Future[Boolean] = store.contains(node)
      override def nodes: Future[Map[Node.Id, Node]] = store.nodes
    }
}

object NodeStore {
  trait View {
    def get(node: Node.Id): Future[Option[Node]]
    def contains(node: Node.Id): Future[Boolean]
    def nodes: Future[Map[Node.Id, Node]]
  }
}
