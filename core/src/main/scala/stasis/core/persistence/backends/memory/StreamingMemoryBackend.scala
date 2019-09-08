package stasis.core.persistence.backends.memory

import java.util.UUID

import scala.concurrent.{ExecutionContext, Future}
import akka.actor.typed.{ActorSystem, SpawnProtocol}
import akka.stream.scaladsl.{Flow, Keep, Sink, Source}
import akka.util.{ByteString, Timeout}
import akka.{Done, NotUsed}
import stasis.core.persistence.backends.StreamingBackend

@SuppressWarnings(Array("org.wartremover.warts.Any"))
class StreamingMemoryBackend private (
  backend: MemoryBackend[UUID, ByteString],
  val maxSize: Long,
  val name: String
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
        .toMat(Sink.ignore)(Keep.right)
    )

  override def source(key: UUID): Future[Option[Source[ByteString, NotUsed]]] =
    backend.get(key).map(_.map(Source.single))

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
    name: String
  )(implicit s: ActorSystem[SpawnProtocol], t: Timeout): StreamingMemoryBackend =
    typed(maxSize, name)

  def typed[K](
    maxSize: Long,
    name: String
  )(implicit s: ActorSystem[SpawnProtocol], t: Timeout): StreamingMemoryBackend = {
    implicit val ec: ExecutionContext = s.executionContext
    new StreamingMemoryBackend(backend = MemoryBackend.typed[UUID, ByteString](name), maxSize, name)
  }

  def untyped[K](
    maxSize: Long,
    name: String
  )(implicit s: akka.actor.ActorSystem, t: Timeout): StreamingMemoryBackend = {
    implicit val ec: ExecutionContext = s.dispatcher
    new StreamingMemoryBackend(MemoryBackend.untyped[UUID, ByteString](name), maxSize, name)
  }
}
