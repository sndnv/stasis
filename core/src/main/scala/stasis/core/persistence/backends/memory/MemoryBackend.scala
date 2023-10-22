package stasis.core.persistence.backends.memory

import org.apache.pekko.Done
import org.apache.pekko.actor.typed._
import org.apache.pekko.actor.typed.scaladsl.AskPattern._
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.apache.pekko.util.Timeout
import stasis.core.persistence.Metrics
import stasis.core.persistence.backends.KeyValueBackend
import stasis.core.telemetry.TelemetryContext

import scala.concurrent.{ExecutionContext, Future}

class MemoryBackend[K, V] private (
  val name: String,
  storeRef: Future[ActorRef[MemoryBackend.Message[K, V]]]
)(implicit scheduler: Scheduler, ec: ExecutionContext, telemetry: TelemetryContext, timeout: Timeout)
    extends KeyValueBackend[K, V] {
  import MemoryBackend._

  private val metrics = telemetry.metrics[Metrics.KeyValueBackend]

  override def init(): Future[Done] = {
    metrics.recordInit(backend = name)
    Future.successful(Done)
  }

  override def drop(): Future[Done] =
    storeRef
      .flatMap(_ ? ((ref: ActorRef[Done]) => Reset(ref)))
      .map { result =>
        metrics.recordDrop(backend = name)
        result
      }

  override def put(key: K, value: V): Future[Done] =
    storeRef
      .flatMap(_ ? ((ref: ActorRef[Done]) => Put(key, value, ref)))
      .map { result =>
        metrics.recordPut(backend = name)
        result
      }

  override def delete(key: K): Future[Boolean] =
    storeRef
      .flatMap(_ ? ((ref: ActorRef[Boolean]) => Remove(key, ref)))
      .map { result =>
        metrics.recordDelete(backend = name)
        result
      }

  override def get(key: K): Future[Option[V]] =
    storeRef
      .flatMap(_ ? ((ref: ActorRef[Option[V]]) => Get(key, ref)))
      .map { result =>
        result.foreach(_ => metrics.recordGet(backend = name))
        result
      }

  override def contains(key: K): Future[Boolean] =
    storeRef.flatMap(_ ? ((ref: ActorRef[Option[V]]) => Get(key, ref))).map(_.isDefined)

  override def entries: Future[Map[K, V]] =
    storeRef
      .flatMap(_ ? ((ref: ActorRef[Map[K, V]]) => GetAll(ref)))
      .map { result =>
        if (result.nonEmpty) {
          metrics.recordGet(backend = name, entries = result.size)
        }
        result
      }
}

object MemoryBackend {
  def apply[K, V](
    name: String
  )(implicit s: ActorSystem[SpawnProtocol.Command], telemetry: TelemetryContext, t: Timeout): MemoryBackend[K, V] = {
    implicit val ec: ExecutionContext = s.executionContext

    new MemoryBackend[K, V](
      name = name,
      storeRef = s ? (SpawnProtocol.Spawn(store(Map.empty[K, V]), name, Props.empty, _))
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
