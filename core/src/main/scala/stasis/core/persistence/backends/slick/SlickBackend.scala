package stasis.core.persistence.backends.slick

import akka.Done
import akka.actor.typed.{ActorSystem, DispatcherSelector, SpawnProtocol}
import akka.util.ByteString
import slick.jdbc.JdbcProfile
import slick.lifted.ProvenShape
import stasis.core.persistence.backends.KeyValueBackend

import scala.concurrent.{ExecutionContext, Future}

class SlickBackend[K, V](
  protected val tableName: String,
  protected val profile: JdbcProfile,
  protected val database: JdbcProfile#Backend#DatabaseDef,
  protected val serdes: KeyValueBackend.Serdes[K, V]
)(implicit system: ActorSystem[SpawnProtocol.Command])
    extends KeyValueBackend[K, V] {
  import profile.api._
  import serdes._

  // this EC is only used for mapping ops after results are retrieved from the underlying DB;
  // Slick uses its own dispatcher(s) to handle blocking IO
  // - https://scala-slick.org/doc/3.3.1/database.html#database-thread-pool
  private implicit val ec: ExecutionContext = system.dispatchers.lookup(DispatcherSelector.default())

  private class KeyValueStore(tag: Tag) extends Table[(String, Array[Byte])](tag, tableName) {
    def key: Rep[String] = column[String]("KEY", O.PrimaryKey)
    def value: Rep[Array[Byte]] = column[Array[Byte]]("VALUE")
    def * : ProvenShape[(String, Array[Byte])] = (key, value)
  }

  private val store = TableQuery[KeyValueStore]

  override def init(): Future[Done] =
    database.run(store.schema.create).map(_ => Done)

  override def drop(): Future[Done] =
    database.run(store.schema.drop).map(_ => Done)

  override def put(key: K, value: V): Future[Done] = {
    val action = store
      .insertOrUpdate((key: String) -> (value: ByteString).toArray)
      .map(_ => Done)

    database.run(action)
  }

  override def delete(key: K): Future[Boolean] = {
    val action = store
      .filter(_.key === (key: String))
      .delete
      .map(_ == 1)

    database.run(action)
  }

  override def get(key: K): Future[Option[V]] = {
    val action = store
      .filter(_.key === (key: String))
      .map(_.value)
      .result
      .headOption
      .map(_.map(value => ByteString(value): V))

    database.run(action)
  }

  override def contains(key: K): Future[Boolean] = {
    val action = store.filter(_.key === (key: String)).exists.result
    database.run(action)
  }

  override def entries: Future[Map[K, V]] = {
    val action = store.result
      .map(_.map(entry => (entry._1: K) -> (ByteString(entry._2): V)).toMap)

    database.run(action)
  }
}

object SlickBackend {
  def apply[K, V](
    tableName: String,
    profile: JdbcProfile,
    database: JdbcProfile#Backend#DatabaseDef,
    serdes: KeyValueBackend.Serdes[K, V]
  )(implicit system: ActorSystem[SpawnProtocol.Command]): SlickBackend[K, V] =
    new SlickBackend[K, V](tableName, profile, database, serdes)
}
