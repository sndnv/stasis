package stasis.server.persistence.users

import java.nio.charset.StandardCharsets

import scala.concurrent.Future
import scala.concurrent.duration._

import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.scaladsl.Behaviors

import stasis.core.persistence.backends.slick.LegacyKeyValueStore
import io.github.sndnv.layers.testing.UnitSpec
import io.github.sndnv.layers.testing.persistence.TestSlickDatabase
import io.github.sndnv.layers.telemetry.mocks.MockTelemetryContext
import stasis.shared.model.users.User
import stasis.test.specs.unit.shared.model.Generators

class DefaultUserStoreSpec extends UnitSpec with TestSlickDatabase {
  "A DefaultUserStore" should "add, retrieve and delete users" in withRetry {
    withStore { (profile, database) =>
      val store = new DefaultUserStore(name = "TEST_USERS", userSaltSize = 8, profile = profile, database = database)

      val expectedUser = Generators.generateUser.copy(
        limits = Some(
          User.Limits(
            maxDevices = 1,
            maxCrates = 2,
            maxStorage = 3,
            maxStoragePerCrate = 4,
            maxRetention = 5.seconds,
            minRetention = 6.seconds
          )
        )
      )

      for {
        _ <- store.init()
        _ <- store.put(expectedUser)
        actualUser <- store.get(expectedUser.id)
        someUsers <- store.list()
        _ <- store.delete(expectedUser.id)
        missingUser <- store.get(expectedUser.id)
        noUsers <- store.list()
        _ <- store.drop()
      } yield {
        actualUser should be(Some(expectedUser))
        someUsers should be(Seq(expectedUser))
        missingUser should be(None)
        noUsers should be(Seq.empty)
      }
    }
  }

  it should "provide migrations" in withRetry {
    withClue("from key-value store to version 1") {
      withStore { (profile, database) =>
        import play.api.libs.json._

        import stasis.shared.api.Formats._

        val name = "TEST_USERS"
        val legacy = LegacyKeyValueStore(name = name, profile = profile, database = database)
        val current = new DefaultUserStore(name = name, userSaltSize = 8, profile = profile, database = database)

        val users = Seq(
          Generators.generateUser,
          Generators.generateUser,
          Generators.generateUser
        )

        val jsonUsers = users.map { user =>
          user.id ->
            Json
              .obj(
                "id" -> user.id,
                "salt" -> user.salt,
                "active" -> user.active,
                "limits" -> user.limits,
                "permissions" -> user.permissions
              )
              .toString()
              .getBytes(StandardCharsets.UTF_8)
        }

        for {
          _ <- legacy.create()
          _ <- Future.sequence(jsonUsers.map(e => legacy.insert(e._1.toString, e._2)))
          migration = current.migrations.find(_.version == 1) match {
            case Some(migration) => migration
            case None            => fail("Expected migration with version == 1 but none was found")
          }
          currentResultBefore <- current.list().failed
          neededBefore <- migration.needed.run()
          _ <- migration.action.run()
          neededAfter <- migration.needed.run()
          currentResultAfter <- current.list()
          _ <- current.drop()
        } yield {
          currentResultBefore.getMessage should include("""Column "ID" not found""")
          neededBefore should be(true)

          neededAfter should be(false)
          currentResultAfter.map(_.id).sorted should be(users.map(_.id).sorted)
        }
      }
    }
  }

  private implicit val telemetry: MockTelemetryContext = MockTelemetryContext()

  private implicit val system: ActorSystem[Nothing] = ActorSystem(
    guardianBehavior = Behaviors.ignore,
    "DefaultUserStoreSpec"
  )
}
