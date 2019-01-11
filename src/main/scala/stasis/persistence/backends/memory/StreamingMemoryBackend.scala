package stasis.persistence.backends.memory

import scala.concurrent.{ExecutionContext, Future}

import akka.actor.typed.{ActorSystem, SpawnProtocol}
import akka.stream.scaladsl.{Flow, Keep, Sink, Source}
import akka.util.{ByteString, Timeout}
import akka.{Done, NotUsed}
import stasis.persistence.backends.StreamingBackend

@SuppressWarnings(Array("org.wartremover.warts.Any"))
class StreamingMemoryBackend[K] private (
  backend: MemoryBackend[K, ByteString],
  maxSize: Long
)(implicit ec: ExecutionContext)
    extends StreamingBackend[K] {
  override def init(): Future[Done] = backend.init()

  override def drop(): Future[Done] = backend.drop()

  override def sink(key: K): Future[Sink[ByteString, Future[Done]]] =
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

  override def source(key: K): Future[Option[Source[ByteString, NotUsed]]] =
    backend.get(key).map(_.map(Source.single))

  override def delete(key: K): Future[Boolean] = backend.delete(key)

  override def contains(key: K): Future[Boolean] = backend.contains(key)

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
  )(implicit s: ActorSystem[SpawnProtocol], t: Timeout): StreamingMemoryBackend[K] =
    typed(maxSize, name)

  def typed[K](
    maxSize: Long,
    name: String
  )(implicit s: ActorSystem[SpawnProtocol], t: Timeout): StreamingMemoryBackend[K] = {
    implicit val ec: ExecutionContext = s.executionContext
    new StreamingMemoryBackend[K](backend = MemoryBackend.typed[K, ByteString](name), maxSize)
  }

  def untyped[K](
    maxSize: Long,
    name: String
  )(implicit s: akka.actor.ActorSystem, t: Timeout): StreamingMemoryBackend[K] = {
    implicit val ec: ExecutionContext = s.dispatcher
    new StreamingMemoryBackend[K](MemoryBackend.untyped[K, ByteString](name), maxSize)
  }
}
