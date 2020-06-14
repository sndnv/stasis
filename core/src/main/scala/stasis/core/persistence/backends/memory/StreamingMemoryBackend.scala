package stasis.core.persistence.backends.memory

import java.util.UUID

import akka.actor.typed.{ActorSystem, SpawnProtocol}
import akka.stream.scaladsl.{Flow, Keep, Sink, Source}
import akka.util.{ByteString, Timeout}
import akka.{Done, NotUsed}
import stasis.core.persistence.backends.StreamingBackend

import scala.concurrent.{ExecutionContext, Future}

class StreamingMemoryBackend private (
  backend: MemoryBackend[UUID, ByteString],
  maxSize: Long,
  maxChunkSize: Int
)(implicit ec: ExecutionContext)
    extends StreamingBackend {
  override def init(): Future[Done] = backend.init()

  override def drop(): Future[Done] = backend.drop()

  override def sink(key: UUID): Future[Sink[ByteString, Future[Done]]] =
    Future.successful(
      Flow[ByteString]
        .fold(ByteString.empty) {
          case (folded, chunk) =>
            folded.concat(chunk)
        }
        .mapAsyncUnordered(parallelism = 1) { data =>
          backend.put(key, data)
        }
        .toMat(Sink.ignore)(Keep.right[NotUsed, Future[Done]])
    )

  override def source(key: UUID): Future[Option[Source[ByteString, NotUsed]]] =
    backend.get(key).map(_.map(value => Source(value.grouped(maxChunkSize).toList)))

  override def delete(key: UUID): Future[Boolean] = backend.delete(key)

  override def contains(key: UUID): Future[Boolean] = backend.contains(key)

  override def canStore(bytes: Long): Future[Boolean] =
    backend.entries.map { entries =>
      val usedBytes = entries.map(_._2.length).foldLeft(0L)(_ + _)
      val availableBytes = maxSize - usedBytes

      availableBytes >= bytes
    }
}

object StreamingMemoryBackend {
  def apply[K](
    maxSize: Long,
    maxChunkSize: Int,
    name: String
  )(implicit s: ActorSystem[SpawnProtocol.Command], t: Timeout): StreamingMemoryBackend = {
    implicit val ec: ExecutionContext = s.executionContext
    new StreamingMemoryBackend(
      backend = MemoryBackend[UUID, ByteString](name),
      maxSize = maxSize,
      maxChunkSize = maxChunkSize
    )
  }
}
