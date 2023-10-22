package stasis.core.persistence.backends.memory

import org.apache.pekko.actor.typed.{ActorSystem, SpawnProtocol}
import org.apache.pekko.stream.scaladsl.{Flow, Keep, Sink, Source}
import org.apache.pekko.util.{ByteString, Timeout}
import org.apache.pekko.{Done, NotUsed}
import stasis.core.persistence.Metrics
import stasis.core.persistence.backends.StreamingBackend
import stasis.core.telemetry.TelemetryContext

import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}

class StreamingMemoryBackend private (
  backend: MemoryBackend[UUID, ByteString],
  maxSize: Long,
  maxChunkSize: Int
)(implicit ec: ExecutionContext, telemetry: TelemetryContext)
    extends StreamingBackend {

  private val metrics = telemetry.metrics[Metrics.StreamingBackend]

  override val info: String =
    s"StreamingMemoryBackend(maxSize=${maxSize.toString}, maxChunkSize=${maxChunkSize.toString})"

  override def init(): Future[Done] =
    backend
      .init()
      .map { result =>
        metrics.recordInit(backend = backend.name)
        result
      }

  override def drop(): Future[Done] = backend
    .drop()
    .map { result =>
      metrics.recordDrop(backend = backend.name)
      result
    }

  override def available(): Future[Boolean] = Future.successful(true)

  override def sink(key: UUID): Future[Sink[ByteString, Future[Done]]] =
    Future.successful(
      Flow[ByteString]
        .fold(ByteString.empty) { case (folded, chunk) =>
          folded.concat(chunk)
        }
        .mapAsyncUnordered(parallelism = 1) { data =>
          backend
            .put(key, data)
            .map { result =>
              metrics.recordWrite(backend = backend.name, bytes = data.length.toLong)
              result
            }
        }
        .toMat(Sink.ignore)(Keep.right[NotUsed, Future[Done]])
    )

  override def source(key: UUID): Future[Option[Source[ByteString, NotUsed]]] =
    backend
      .get(key)
      .map(_.map { value =>
        metrics.recordRead(backend = backend.name, bytes = value.length.toLong)
        Source(value.grouped(maxChunkSize).toList)
      })

  override def delete(key: UUID): Future[Boolean] = backend
    .delete(key)
    .map { result =>
      metrics.recordDiscard(backend = backend.name)
      result
    }

  override def contains(key: UUID): Future[Boolean] = backend.contains(key)

  override def canStore(bytes: Long): Future[Boolean] =
    backend.entries.map { entries =>
      val usedBytes = entries.map(_._2.length.toLong).foldLeft(0L)(_ + _)
      val availableBytes = maxSize - usedBytes

      availableBytes >= bytes
    }
}

object StreamingMemoryBackend {
  def apply[K](
    maxSize: Long,
    maxChunkSize: Int,
    name: String
  )(implicit
    s: ActorSystem[SpawnProtocol.Command],
    telemetry: TelemetryContext,
    t: Timeout
  ): StreamingMemoryBackend = {
    implicit val ec: ExecutionContext = s.executionContext
    new StreamingMemoryBackend(
      backend = MemoryBackend[UUID, ByteString](name),
      maxSize = maxSize,
      maxChunkSize = maxChunkSize
    )
  }
}
