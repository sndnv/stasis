package stasis.core.persistence.backends.slick

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import org.apache.pekko.Done
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.DispatcherSelector
import org.apache.pekko.util.ByteString
import slick.jdbc.JdbcProfile
import slick.lifted.ProvenShape

import stasis.core.persistence.backends.KeyValueBackend
import stasis.layers.persistence.KeyValueStore
import stasis.layers.persistence.Metrics
import stasis.layers.persistence.migration.Migration
import stasis.layers.telemetry.TelemetryContext

class SlickBackend[K, V](
  override val name: String,
  protected val profile: JdbcProfile,
  protected val database: JdbcProfile#Backend#Database,
  protected val serdes: KeyValueBackend.Serdes[K, V]
)(implicit system: ActorSystem[Nothing], telemetry: TelemetryContext)
    extends KeyValueStore[K, V] {
  import profile.api._
  import serdes._

  // this EC is only used for mapping ops after results are retrieved from the underlying DB;
  // Slick uses its own dispatcher(s) to handle blocking IO
  // - https://scala-slick.org/doc/3.3.1/database.html#database-thread-pool
  private implicit val ec: ExecutionContext = system.dispatchers.lookup(DispatcherSelector.default())

  private val metrics = telemetry.metrics[Metrics.Store]

  override val migrations: Seq[Migration] = Seq.empty

  private class KeyValueStore(tag: Tag) extends Table[(String, Array[Byte])](tag, name) {
    def key: Rep[String] = column[String]("KEY", O.PrimaryKey)
    def value: Rep[Array[Byte]] = column[Array[Byte]]("VALUE")
    def * : ProvenShape[(String, Array[Byte])] = (key, value)
  }

  private val store = TableQuery[KeyValueStore]

  override def init(): Future[Done] =
    database.run(store.schema.create).map { _ =>
      Done
    }

  override def drop(): Future[Done] =
    database.run(store.schema.drop).map { _ =>
      Done
    }

  override def put(key: K, value: V): Future[Done] = {
    val action = store
      .insertOrUpdate((key: String) -> (value: ByteString).toArray)
      .map(_ => Done)

    database
      .run(action)
      .map { result =>
        metrics.recordPut(store = name)
        result
      }
  }

  override def delete(key: K): Future[Boolean] = {
    val action = store
      .filter(_.key === (key: String))
      .delete
      .map(_ == 1)

    database
      .run(action)
      .map { result =>
        metrics.recordDelete(store = name)
        result
      }
  }

  override def get(key: K): Future[Option[V]] = {
    val action = store
      .filter(_.key === (key: String))
      .map(_.value)
      .result
      .headOption
      .map(_.map(value => ByteString(value): V))

    database
      .run(action)
      .map { result =>
        result.foreach(_ => metrics.recordGet(store = name))
        result
      }
  }

  override def contains(key: K): Future[Boolean] = {
    val action = store.filter(_.key === (key: String)).exists.result
    database.run(action)
  }

  override def entries: Future[Map[K, V]] = {
    val action = store.result
      .map(_.map(entry => (entry._1: K) -> (ByteString(entry._2): V)).toMap)

    database
      .run(action)
      .map { result =>
        if (result.nonEmpty) {
          metrics.recordGet(store = name, entries = result.size)
        }
        result
      }
  }
}

object SlickBackend {
  def apply[K, V](
    name: String,
    profile: JdbcProfile,
    database: JdbcProfile#Backend#Database,
    serdes: KeyValueBackend.Serdes[K, V]
  )(implicit system: ActorSystem[Nothing], telemetry: TelemetryContext): SlickBackend[K, V] =
    new SlickBackend[K, V](name, profile, database, serdes)
}
