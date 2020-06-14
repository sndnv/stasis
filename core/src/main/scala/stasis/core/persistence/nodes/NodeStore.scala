package stasis.core.persistence.nodes

import akka.Done
import akka.actor.typed.{ActorSystem, SpawnProtocol}
import akka.util.Timeout
import stasis.core.persistence.StoreInitializationResult
import stasis.core.persistence.backends.KeyValueBackend
import stasis.core.persistence.backends.memory.MemoryBackend
import stasis.core.routing.Node

import scala.concurrent.{ExecutionContext, Future}

trait NodeStore { store =>
  def put(node: Node): Future[Done]
  def delete(node: Node.Id): Future[Boolean]
  def get(node: Node.Id): Future[Option[Node]]
  def contains(node: Node.Id): Future[Boolean]
  def nodes: Future[Map[Node.Id, Node]]

  def view: NodeStoreView = new NodeStoreView {
    override def get(node: Node.Id): Future[Option[Node]] = store.get(node)
    override def contains(node: Node.Id): Future[Boolean] = store.contains(node)
    override def nodes: Future[Map[Node.Id, Node]] = store.nodes
  }
}

object NodeStore {
  def apply(
    backend: KeyValueBackend[Node.Id, Node],
    cachingEnabled: Boolean
  )(implicit system: ActorSystem[SpawnProtocol.Command], timeout: Timeout): StoreInitializationResult[NodeStore] = {
    implicit val ec: ExecutionContext = system.executionContext

    val cacheOpt: Option[KeyValueBackend[Node.Id, Node]] =
      if (cachingEnabled) {
        Some(MemoryBackend[Node.Id, Node](name = s"nodes-cache-${java.util.UUID.randomUUID()}"))
      } else {
        None
      }

    def caching(): Future[Done] = cacheOpt match {
      case Some(cache) =>
        backend.entries.flatMap { entries =>
          Future
            .sequence(entries.map { case (id, node) => cache.put(id, node) })
            .map(_ => Done)
        }

      case None => Future.successful(Done)
    }

    val store: NodeStore = new NodeStore {
      override def put(node: Node): Future[Done] =
        cacheOpt match {
          case Some(cache) => backend.put(node.id, node).flatMap(_ => cache.put(node.id, node))
          case None        => backend.put(node.id, node)
        }

      override def delete(node: Node.Id): Future[Boolean] =
        cacheOpt match {
          case Some(cache) => backend.delete(node).flatMap(_ => cache.delete(node))
          case None        => backend.delete(node)
        }

      override def get(node: Node.Id): Future[Option[Node]] =
        cacheOpt match {
          case Some(cache) => cache.get(node)
          case None        => backend.get(node)
        }

      override def contains(node: Node.Id): Future[Boolean] =
        cacheOpt match {
          case Some(cache) => cache.contains(node)
          case None        => backend.contains(node)
        }

      override def nodes: Future[Map[Node.Id, Node]] =
        cacheOpt match {
          case Some(cache) => cache.entries
          case None        => backend.entries
        }
    }

    StoreInitializationResult(
      store = store,
      init = caching
    )
  }
}
