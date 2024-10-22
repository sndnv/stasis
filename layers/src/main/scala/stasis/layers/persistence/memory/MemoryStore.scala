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

  override def put(key: K, value: V): Future[Done] = metrics.recordPut(store = name) {
    storeRef ? ((ref: ActorRef[Done]) => Put(key, value, ref))
  }

  override def delete(key: K): Future[Boolean] = metrics.recordDelete(store = name) {
    storeRef ? ((ref: ActorRef[Boolean]) => Remove(key, ref))
  }

  override def get(key: K): Future[Option[V]] = metrics.recordGet(store = name) {
    storeRef ? ((ref: ActorRef[Option[V]]) => Get(key, ref))
  }

  override def contains(key: K): Future[Boolean] = metrics.recordContains(store = name) {
    (storeRef ? ((ref: ActorRef[Option[V]]) => Get(key, ref))).map(_.isDefined)
  }

  override def entries: Future[Map[K, V]] = metrics.recordList(store = name) {
    storeRef ? ((ref: ActorRef[Map[K, V]]) => GetAll(ref))
  }

  override def load(entries: Map[K, V]): Future[Done] =
    storeRef ? ((ref: ActorRef[Done]) => Load(entries, ref))
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
  private final case class Load[K, V](entries: Map[K, V], replyTo: ActorRef[Done]) extends Message[K, V]

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

        case Load(entries, replyTo) =>
          replyTo ! Done
          store(map = map ++ entries)
      }
    }
}
