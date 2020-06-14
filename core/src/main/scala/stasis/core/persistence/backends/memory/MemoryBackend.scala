package stasis.core.persistence.backends.memory

import akka.Done
import akka.actor.typed._
import akka.actor.typed.scaladsl.AskPattern._
import akka.actor.typed.scaladsl.Behaviors
import akka.util.Timeout
import stasis.core.persistence.backends.KeyValueBackend

import scala.concurrent.{ExecutionContext, Future}

class MemoryBackend[K, V] private (
  storeRef: Future[ActorRef[MemoryBackend.Message[K, V]]]
)(implicit scheduler: Scheduler, ec: ExecutionContext, timeout: Timeout)
    extends KeyValueBackend[K, V] {
  import MemoryBackend._

  override def init(): Future[Done] = Future.successful(Done)

  override def drop(): Future[Done] = storeRef.flatMap(_ ? (ref => Reset(ref)))

  override def put(key: K, value: V): Future[Done] = storeRef.flatMap(_ ? (ref => Put(key, value, ref)))

  override def delete(key: K): Future[Boolean] = storeRef.flatMap(_ ? (ref => Remove(key, ref)))

  override def get(key: K): Future[Option[V]] = storeRef.flatMap(_ ? (ref => Get(key, ref)))

  override def contains(key: K): Future[Boolean] = {
    val result: Future[Option[V]] = storeRef.flatMap(_ ? (ref => Get(key, ref)))
    result.map(_.isDefined)
  }

  override def entries: Future[Map[K, V]] = storeRef.flatMap(_ ? (ref => GetAll(ref)))
}

object MemoryBackend {
  def apply[K, V](name: String)(implicit s: ActorSystem[SpawnProtocol.Command], t: Timeout): MemoryBackend[K, V] = {
    implicit val ec: ExecutionContext = s.executionContext

    new MemoryBackend[K, V](storeRef = s ? (SpawnProtocol.Spawn(store(Map.empty[K, V]), name, Props.empty, _)))
  }

  private sealed trait Message[K, V]
  private final case class Put[K, V](key: K, value: V, replyTo: ActorRef[Done]) extends Message[K, V]
  private final case class Remove[K, V](key: K, replyTo: ActorRef[Boolean]) extends Message[K, V]
  private final case class Get[K, V](key: K, replyTo: ActorRef[Option[V]]) extends Message[K, V]
  private final case class GetAll[K, V](replyTo: ActorRef[Map[K, V]]) extends Message[K, V]
  private final case class Reset[K, V](replyTo: ActorRef[Done]) extends Message[K, V]

  private def store[K, V](map: Map[K, V]): Behavior[Message[K, V]] = Behaviors.receive { (_, message) =>
    message match {
      case Put(key, value, replyTo) =>
        replyTo ! Done
        store(map = map + (key -> value))

      case Remove(key, replyTo) =>
        replyTo ! map.contains(key)
        store(map = map - key)

      case Get(key, replyTo) =>
        replyTo ! map.get(key)
        Behaviors.same

      case GetAll(replyTo) =>
        replyTo ! map
        Behaviors.same

      case Reset(replyTo) =>
        replyTo ! Done
        store(map = Map.empty[K, V])
    }
  }
}
