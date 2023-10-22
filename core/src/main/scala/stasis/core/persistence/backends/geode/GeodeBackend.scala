package stasis.core.persistence.backends.geode

import org.apache.pekko.Done
import org.apache.pekko.actor.typed.{ActorSystem, DispatcherSelector, SpawnProtocol}
import org.apache.pekko.util.ByteString
import org.apache.geode.cache.Region
import stasis.core.persistence.Metrics
import stasis.core.persistence.backends.KeyValueBackend
import stasis.core.telemetry.TelemetryContext

import scala.concurrent.{ExecutionContext, Future}
import scala.jdk.CollectionConverters._

class GeodeBackend[K, V](
  protected val region: Region[String, Array[Byte]],
  protected val serdes: KeyValueBackend.Serdes[K, V]
)(implicit system: ActorSystem[SpawnProtocol.Command], telemetry: TelemetryContext)
    extends KeyValueBackend[K, V] {
  import serdes._

  private val regionPath = region.getFullPath

  private val metrics = telemetry.metrics[Metrics.KeyValueBackend]

  private implicit val ec: ExecutionContext = system.dispatchers.lookup(DispatcherSelector.blocking())

  override def init(): Future[Done] = {
    metrics.recordInit(backend = regionPath)
    Future.successful(Done)
  }

  override def drop(): Future[Done] =
    Future {
      region.clear()
      metrics.recordDrop(backend = regionPath)
      Done
    }

  override def put(key: K, value: V): Future[Done] =
    Future {
      val _ = region.put(key.asGeodeKey, value.asGeodeValue)
      metrics.recordPut(backend = regionPath)
      Done
    }

  override def delete(key: K): Future[Boolean] =
    Future {
      val result = Option(region.remove(key.asGeodeKey)).isDefined
      metrics.recordDelete(backend = regionPath)
      result
    }

  override def get(key: K): Future[Option[V]] =
    Future {
      Option(region.get(key.asGeodeKey)).map { result =>
        val value = ByteString.fromArray(result): V
        metrics.recordGet(backend = regionPath)
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
          metrics.recordGet(backend = regionPath)
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
