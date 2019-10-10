package stasis.server.model.nodes

import akka.Done
import stasis.core.persistence.nodes.NodeStore
import stasis.core.routing.Node
import stasis.server.security.Resource
import stasis.shared.security.Permission

import scala.concurrent.Future

trait ServerNodeStore { store =>
  protected def create(node: Node): Future[Done]
  protected def update(node: Node): Future[Done]
  protected def delete(node: Node.Id): Future[Boolean]
  protected def get(node: Node.Id): Future[Option[Node]]
  protected def list(): Future[Map[Node.Id, Node]]

  final def view(): ServerNodeStore.View.Service =
    new ServerNodeStore.View.Service {
      override def get(node: Node.Id): Future[Option[Node]] = store.get(node)
      override def list(): Future[Map[Node.Id, Node]] = store.list()
    }

  final def manage(): ServerNodeStore.Manage.Service =
    new ServerNodeStore.Manage.Service {
      override def create(node: Node): Future[Done] = store.create(node)
      override def update(node: Node): Future[Done] = store.update(node)
      override def delete(node: Node.Id): Future[Boolean] = store.delete(node)
    }
}

object ServerNodeStore {
  object View {
    sealed trait Service extends Resource {
      def get(node: Node.Id): Future[Option[Node]]
      def list(): Future[Map[Node.Id, Node]]
      override def requiredPermission: Permission = Permission.View.Service
    }
  }

  object Manage {
    sealed trait Service extends Resource {
      def create(node: Node): Future[Done]
      def update(node: Node): Future[Done]
      def delete(node: Node.Id): Future[Boolean]
      override def requiredPermission: Permission = Permission.Manage.Service
    }
  }

  def apply(store: NodeStore): ServerNodeStore =
    new ServerNodeStore {
      override protected def create(node: Node): Future[Done] = store.put(node)
      override protected def update(node: Node): Future[Done] = store.put(node)
      override protected def delete(node: Node.Id): Future[Boolean] = store.delete(node)
      override protected def get(node: Node.Id): Future[Option[Node]] = store.get(node)
      override protected def list(): Future[Map[Node.Id, Node]] = store.nodes
    }
}
