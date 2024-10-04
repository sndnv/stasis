package stasis.core.persistence.backends.geode

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.jdk.CollectionConverters._

import org.apache.geode.cache.Region
import org.apache.pekko.Done
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.DispatcherSelector
import org.apache.pekko.util.ByteString

import stasis.core.persistence.backends.KeyValueBackend
import stasis.layers.persistence.KeyValueStore
import stasis.layers.persistence.Metrics
import stasis.layers.persistence.migration.Migration
import stasis.layers.telemetry.TelemetryContext

class GeodeBackend[K, V](
  protected val region: Region[String, Array[Byte]],
  protected val serdes: KeyValueBackend.Serdes[K, V]
)(implicit system: ActorSystem[Nothing], telemetry: TelemetryContext)
    extends KeyValueStore[K, V] {
  import serdes._

  override val name: String = region.getName

  override val migrations: Seq[Migration] = Seq.empty

  private val regionPath = region.getFullPath

  private val metrics = telemetry.metrics[Metrics.Store]

  private implicit val ec: ExecutionContext = system.dispatchers.lookup(DispatcherSelector.blocking())

  override def init(): Future[Done] =
    Future.successful(Done)

  override def drop(): Future[Done] =
    Future {
      region.clear()
      Done
    }

  override def put(key: K, value: V): Future[Done] =
    Future {
      val _ = region.put(key.asGeodeKey, value.asGeodeValue)
      metrics.recordPut(store = regionPath)
      Done
    }

  override def delete(key: K): Future[Boolean] =
    Future {
      val result = Option(region.remove(key.asGeodeKey)).isDefined
      metrics.recordDelete(store = regionPath)
      result
    }

  override def get(key: K): Future[Option[V]] =
    Future {
      Option(region.get(key.asGeodeKey)).map { result =>
        val value = ByteString.fromArray(result): V
        metrics.recordGet(store = regionPath)
        value
      }
    }

  override def contains(key: K): Future[Boolean] =
    Future.successful(region.containsKey(key.asGeodeKey))

  override def entries: Future[Map[K, V]] =
    Future {
      region
        .getAll(region.keySet())
        .asScala
        .toMap
        .map { case (k, v) =>
          metrics.recordGet(store = regionPath)
          (k: K) -> (ByteString.fromArray(v): V)
        }
    }

  private implicit class GeodeKey(key: K) {
    def asGeodeKey: String = key: String
  }

  private implicit class GeodeValue(value: V) {
    def asGeodeValue: Array[Byte] = (value: ByteString).toArray
  }
}
