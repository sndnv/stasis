package stasis.layers.persistence.migration

import scala.concurrent.Future

import org.apache.pekko.Done
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import slick.jdbc.H2Profile
import slick.jdbc.meta.MTable

import stasis.layers.UnitSpec
import stasis.layers.persistence.Store

class MigrationExecutorSpec extends UnitSpec {
  import H2Profile.api._

  "A Default MigrationExecutor" should "execute migrations for stores" in {
    val migrations = Seq(
      Migration(
        version = 2,
        needed = Migration.Action {
          h2db.run(sql"""SELECT COUNT(*) FROM TEST_TABLE""".as[Int].head.map(_ == 0))
        },
        action = Migration.Action {
          h2db.run(sqlu"""INSERT INTO TEST_TABLE VALUES('abc', 1)""")
        }
      ),
      Migration(
        version = 3,
        needed = Migration.Action {
          h2db.run(
            MTable.getTables(namePattern = "TEST_TABLE").head.flatMap(_.getColumns).map(!_.exists(_.name.toLowerCase == "c"))
          )
        },
        action = Migration.Action {
          h2db.run(sqlu"""ALTER TABLE TEST_TABLE ADD c varchar not null DEFAULT('some-default')""")
        }
      ),
      Migration(
        version = 1,
        needed = Migration.Action {
          h2db.run(MTable.getTables(namePattern = "TEST_TABLE").map(_.isEmpty))
        },
        action = Migration.Action {
          h2db.run(sqlu"""CREATE TABLE TEST_TABLE(a varchar not  null, b int not null)""")
        }
      )
    )

    val store = createStore(withMigrations = migrations)

    val executor = MigrationExecutor()

    for {
      existingTable <- h2db.run(MTable.getTables(namePattern = "TEST_TABLE").headOption)
      initialResult <- executor.execute(forStore = store)
      rerunResult <- executor.execute(forStore = store)
      columns <- h2db.run(MTable.getTables(namePattern = "TEST_TABLE").head.flatMap(_.getColumns))
      rows <- h2db.run(sql"""SELECT a,b,c FROM TEST_TABLE""".as[(String, Int, String)])
    } yield {
      existingTable should be(None)

      initialResult should be(MigrationResult(found = migrations.length, executed = migrations.length))
      rerunResult should be(MigrationResult(found = migrations.length, executed = 0))

      columns.toList match {
        case a :: b :: c :: Nil =>
          a.name should be("A")
          a.typeName should be("CHARACTER VARYING")
          a.nullable should be(Some(false))
          a.columnDef should be(None)

          b.name should be("B")
          b.typeName should be("INTEGER")
          b.nullable should be(Some(false))
          b.columnDef should be(None)

          c.name should be("C")
          c.typeName should be("CHARACTER VARYING")
          c.nullable should be(Some(false))
          c.columnDef should be(Some("'some-default'"))

        case other =>
          fail(s"Unexpected columns found: [$other]")
      }

      rows.toList match {
        case first :: Nil =>
          first should be(("abc", 1, "some-default"))

        case other =>
          fail(s"Unexpected rows found: [$other]")
      }
    }
  }

  private val h2db = H2Profile.api.Database.forURL(url = "jdbc:h2:mem:MigrationExecutorSpec", keepAliveConnection = true)

  private class TestStore(override val migrations: Seq[Migration]) extends Store {
    override val name: String = "test-store"
    override def init(): Future[Done] = Future.successful(Done)
    override def drop(): Future[Done] = Future.successful(Done)
  }

  private def createStore(withMigrations: Seq[Migration]): Store = new TestStore(withMigrations)

  private implicit val system: ActorSystem[Nothing] = ActorSystem(
    guardianBehavior = Behaviors.ignore,
    name = "MigrationExecutorSpec"
  )
}
