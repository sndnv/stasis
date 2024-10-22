package stasis.core.persistence.backends.file

import java.nio.ByteOrder
import java.util.UUID

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import org.apache.pekko.Done
import org.apache.pekko.NotUsed
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.stream.scaladsl.Sink
import org.apache.pekko.stream.scaladsl.Source
import org.apache.pekko.util.ByteString

import stasis.core.persistence.Metrics
import stasis.core.persistence.backends.StreamingBackend
import stasis.core.persistence.backends.file.container.Container
import stasis.core.persistence.backends.file.container.ops.ConversionOps
import stasis.layers.telemetry.TelemetryContext

class ContainerBackend(
  val path: String,
  val maxChunkSize: Int,
  val maxChunks: Int
)(implicit system: ActorSystem[Nothing], telemetry: TelemetryContext)
    extends StreamingBackend {

  private implicit val byteOrder: ByteOrder = ConversionOps.DEFAULT_BYTE_ORDER

  // the underlying container already uses a blocking dispatcher
  private implicit val ec: ExecutionContext = system.executionContext

  private val metrics = telemetry.metrics[Metrics.StreamingBackend]

  private val container: Container = new Container(path, maxChunkSize, maxChunks)

  override val info: String =
    s"ContainerBackend(path=$path, maxChunkSize=${maxChunkSize.toString}, maxChunks=${maxChunks.toString})"

  override def init(): Future[Done] =
    container.create()

  override def drop(): Future[Done] =
    container.destroy()

  override def available(): Future[Boolean] =
    container.exists

  override def sink(key: UUID): Future[Sink[ByteString, Future[Done]]] =
    container.sink(key).map { sink =>
      sink.contramap { bytes =>
        metrics.recordWrite(backend = path, bytes = bytes.length.toLong)
        bytes
      }
    }

  override def source(key: UUID): Future[Option[Source[ByteString, NotUsed]]] =
    container
      .source(key)
      .map(_.map { source =>
        source
          .wireTap(bytes => metrics.recordRead(backend = path, bytes = bytes.length.toLong))
          .mapMaterializedValue(_ => NotUsed)
      })

  override def delete(key: UUID): Future[Boolean] =
    container.delete(key).map { result =>
      metrics.recordDiscard(backend = path)
      result
    }

  override def contains(key: UUID): Future[Boolean] =
    container.contains(key)

  override def canStore(bytes: Long): Future[Boolean] =
    container.canStore(bytes)
}
