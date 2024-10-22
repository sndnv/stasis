package stasis.core.persistence.backends.slick

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import org.apache.pekko.Done
import org.apache.pekko.actor.typed.ActorSystem
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

  private implicit val ec: ExecutionContext = system.executionContext

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

  override def put(key: K, value: V): Future[Done] = metrics.recordPut(store = name) {
    database.run(
      store
        .insertOrUpdate((key: String) -> (value: ByteString).toArray)
        .map(_ => Done)
    )
  }

  override def delete(key: K): Future[Boolean] = metrics.recordDelete(store = name) {
    database.run(
      store
        .filter(_.key === (key: String))
        .delete
        .map(_ == 1)
    )
  }

  override def get(key: K): Future[Option[V]] = metrics.recordGet(store = name) {
    database.run(
      store
        .filter(_.key === (key: String))
        .map(_.value)
        .result
        .headOption
        .map(_.map(value => ByteString(value): V))
    )
  }

  override def contains(key: K): Future[Boolean] = metrics.recordContains(store = name) {
    database.run(store.filter(_.key === (key: String)).exists.result)
  }

  override def entries: Future[Map[K, V]] = metrics.recordList(store = name) {
    database.run(
      store.result.map(_.map(entry => (entry._1: K) -> (ByteString(entry._2): V)).toMap)
    )
  }

  override def load(entries: Map[K, V]): Future[Done] =
    database.run(
      store
        .insertAll(entries.toSeq.map(e => (e._1: String) -> (e._2: ByteString).toArray))
        .map(_ => Done)
    )
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
