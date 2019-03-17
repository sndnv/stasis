package stasis.core.persistence.backends.ignite
import akka.Done
import akka.util.ByteString
import org.apache.ignite.IgniteCache
import org.apache.ignite.lang.{IgniteFuture, IgniteInClosure}
import stasis.core.persistence.backends.KeyValueBackend

import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.Try

class IgniteBackend[K, V](
  protected val cache: IgniteCache[String, Array[Byte]],
  protected val serdes: KeyValueBackend.Serdes[K, V]
)(implicit ec: ExecutionContext)
    extends KeyValueBackend[K, V] {
  import serdes._

  import scala.collection.JavaConverters._

  override def init(): Future[Done] =
    Future.successful(Done)

  override def drop(): Future[Done] =
    cache.clearAsync().asScala.map(_ => Done)

  override def put(key: K, value: V): Future[Done] =
    cache.putAsync(key.asIgniteKey, value.asIgniteValue).asScala.map(_ => Done)

  override def get(key: K): Future[Option[V]] =
    cache.getAsync(key.asIgniteKey).asScala.map { result =>
      Option(result).map { result =>
        ByteString.fromArray(result): V
      }
    }

  override def delete(key: K): Future[Boolean] =
    cache.removeAsync(key.asIgniteKey).asScala.map(_.booleanValue())

  override def contains(key: K): Future[Boolean] =
    cache.containsKeyAsync(key.asIgniteKey).asScala.map(_.booleanValue())

  override def entries: Future[Map[K, V]] =
    Future {
      cache
        .iterator()
        .asScala
        .map { entry =>
          (entry.getKey: K) -> (ByteString.fromArray(entry.getValue): V)
        }
        .toMap
    }

  private implicit class IgniteToScalaFuture[T](f: IgniteFuture[T]) {
    def asScala: Future[T] = {
      val promise = Promise[T]()

      f.listen(
        new IgniteInClosure[IgniteFuture[T]] {
          override def apply(completed: IgniteFuture[T]): Unit = {
            val _ = promise.complete(Try(completed.get))
          }
        }
      )

      promise.future
    }
  }

  private implicit class IgniteKey(key: K) {
    def asIgniteKey: String = key: String
  }

  private implicit class IgniteValue(value: V) {
    def asIgniteValue: Array[Byte] = (value: ByteString).toArray
  }
}
