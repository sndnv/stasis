package stasis.layers.persistence.memory

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import org.apache.pekko.Done
import org.apache.pekko.actor.typed._
import org.apache.pekko.actor.typed.scaladsl.AskPattern._
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.apache.pekko.util.Timeout

import stasis.layers.persistence.KeyValueStore
import stasis.layers.persistence.Metrics
import stasis.layers.persistence.migration.Migration
import stasis.layers.telemetry.TelemetryContext

class MemoryStore[K, V] private (
  val name: String,
  storeRef: ActorRef[MemoryStore.Message[K, V]]
)(implicit scheduler: Scheduler, ec: ExecutionContext, timeout: Timeout, telemetry: TelemetryContext)
    extends KeyValueStore[K, V] {
  import MemoryStore._

  override val migrations: Seq[Migration] = Seq.empty

  private val metrics = telemetry.metrics[Metrics.Store]

  override def init(): Future[Done] =
    Future.successful(Done)

  override def drop(): Future[Done] =
    (storeRef ? ((ref: ActorRef[Done]) => Reset(ref)))
      .map { result =>
        result
      }

  override def put(key: K, value: V): Future[Done] =
    (storeRef ? ((ref: ActorRef[Done]) => Put(key, value, ref)))
      .map { result =>
        metrics.recordPut(store = name)
        result
      }

  override def delete(key: K): Future[Boolean] =
    (storeRef ? ((ref: ActorRef[Boolean]) => Remove(key, ref)))
      .map { result =>
        metrics.recordDelete(store = name)
        result
      }

  override def get(key: K): Future[Option[V]] =
    (storeRef ? ((ref: ActorRef[Option[V]]) => Get(key, ref)))
      .map { result =>
        result.foreach(_ => metrics.recordGet(store = name))
        result
      }

  override def contains(key: K): Future[Boolean] =
    (storeRef ? ((ref: ActorRef[Option[V]]) => Get(key, ref))).map(_.isDefined)

  override def entries: Future[Map[K, V]] =
    (storeRef ? ((ref: ActorRef[Map[K, V]]) => GetAll(ref)))
      .map { result =>
        if (result.nonEmpty) {
          metrics.recordGet(store = name, entries = result.size)
        }
        result
      }
}

object MemoryStore {
  def apply[K, V](
    name: String
  )(implicit s: ActorSystem[Nothing], t: Timeout, telemetry: TelemetryContext): MemoryStore[K, V] = {
    implicit val ec: ExecutionContext = s.executionContext

    new MemoryStore[K, V](
      name = name,
      storeRef = s.systemActorOf(store(Map.empty[K, V]), name = s"$name-${java.util.UUID.randomUUID().toString}")
    )
  }

  private sealed trait Message[K, V]
  private final case class Put[K, V](key: K, value: V, replyTo: ActorRef[Done]) extends Message[K, V]
  private final case class Remove[K, V](key: K, replyTo: ActorRef[Boolean]) extends Message[K, V]
  private final case class Get[K, V](key: K, replyTo: ActorRef[Option[V]]) extends Message[K, V]
  private final case class GetAll[K, V](replyTo: ActorRef[Map[K, V]]) extends Message[K, V]
  private final case class Reset[K, V](replyTo: ActorRef[Done]) extends Message[K, V]

  private def store[K, V](map: Map[K, V]): Behavior[Message[K, V]] =
    Behaviors.receive { (_, message) =>
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
