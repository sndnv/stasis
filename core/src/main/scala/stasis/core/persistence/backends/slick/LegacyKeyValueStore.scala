package stasis.core.persistence.backends.slick

import scala.concurrent.Future

import org.apache.pekko.Done
import org.apache.pekko.actor.typed.ActorSystem
import play.api.libs.json.JsValue
import slick.jdbc.JdbcProfile
import slick.lifted.ProvenShape
import slick.relational.RelationalProfile

import io.github.sndnv.layers.persistence.migration.Migration

class LegacyKeyValueStore(
  val name: String,
  val profile: JdbcProfile,
  val database: JdbcProfile#Backend#Database
)(implicit val system: ActorSystem[Nothing]) {
  import profile.api._
  import system.executionContext

  private class TableDefinition(tag: Tag) extends Table[(String, Array[Byte])](tag, name) {
    def key: Rep[String] = column[String]("KEY", O.PrimaryKey)
    def value: Rep[Array[Byte]] = column[Array[Byte]]("VALUE")
    def * : ProvenShape[(String, Array[Byte])] = (key, value)
  }

  private val legacy = TableQuery[TableDefinition]

  def create(): Future[Done] =
    database.run(legacy.schema.create).map(_ => Done)

  def insert(key: String, value: Array[Byte]): Future[Done] =
    database.run(legacy.insertOrUpdate(value = (key, value))).map(_ => Done)

  def asMigration[T, R <: RelationalProfile#Table[T]](withVersion: Int, current: TableQuery[R])(f: JsValue => T): Migration =
    Migration(
      version = withVersion,
      needed = Migration.Action {
        import slick.jdbc.meta.MTable

        for {
          table <- database.run(
            MTable.getTables(cat = None, schemaPattern = None, namePattern = Some(name), types = None).map(_.headOption)
          )
          columns <- table.map(t => database.run(t.getColumns)).getOrElse(Future.successful(Seq.empty))
        } yield {
          columns.toList.sortBy(_.name) match {
            case keyCol :: valueCol :: Nil if keyCol.name.toLowerCase == "key" && valueCol.name.toLowerCase == "value" => true
            case _                                                                                                     => false
          }
        }
      },
      action = Migration.Action {
        import play.api.libs.json._

        for {
          entries <- database
            .run(legacy.result)
            .map(_.map(e => Json.parse(e._2)).map(f))
          _ <- database.run(legacy.schema.drop)
          _ <- database.run(current.schema.create)
          _ <- entries.grouped(size = 100).foldLeft(Future.successful(Done)) { case (latestResult, currentEntries) =>
            latestResult.flatMap(_ => database.run(current.insertAll(currentEntries)).map(_ => Done))
          }
        } yield {
          Done
        }
      }
    )
}

object LegacyKeyValueStore {
  def apply(
    name: String,
    profile: JdbcProfile,
    database: JdbcProfile#Backend#Database
  )(implicit system: ActorSystem[Nothing]): LegacyKeyValueStore =
    new LegacyKeyValueStore(name, profile, database)
}
