package stasis.core.persistence.backends.memory

import java.util.UUID

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import org.apache.pekko.Done
import org.apache.pekko.NotUsed
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.stream.scaladsl.Flow
import org.apache.pekko.stream.scaladsl.Keep
import org.apache.pekko.stream.scaladsl.Sink
import org.apache.pekko.stream.scaladsl.Source
import org.apache.pekko.util.ByteString
import org.apache.pekko.util.Timeout

import stasis.core.persistence.Metrics
import stasis.core.persistence.backends.StreamingBackend
import stasis.layers.persistence.memory.MemoryStore
import stasis.layers.telemetry.TelemetryContext

class StreamingMemoryBackend private (
  backend: MemoryStore[UUID, ByteString],
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
  def apply(
    maxSize: Long,
    maxChunkSize: Int,
    name: String
  )(implicit
    s: ActorSystem[Nothing],
    telemetry: TelemetryContext,
    t: Timeout
  ): StreamingMemoryBackend = {
    implicit val ec: ExecutionContext = s.executionContext
    new StreamingMemoryBackend(
      backend = MemoryStore[UUID, ByteString](name),
      maxSize = maxSize,
      maxChunkSize = maxChunkSize
    )
  }
}
