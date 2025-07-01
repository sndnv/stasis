package stasis.identity.persistence.owners

import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.Base64

import scala.concurrent.Future

import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.scaladsl.Behaviors

import stasis.identity.model.Generators
import stasis.identity.persistence.internal.LegacyKeyValueStore
import io.github.sndnv.layers.testing.UnitSpec
import io.github.sndnv.layers.testing.persistence.TestSlickDatabase
import io.github.sndnv.layers.telemetry.mocks.MockTelemetryContext

class DefaultResourceOwnerStoreSpec extends UnitSpec with TestSlickDatabase {
  "A DefaultResourceOwnerStore" should "add, retrieve and delete resource owners" in withRetry {
    withStore { (profile, database) =>
      val store = new DefaultResourceOwnerStore(name = "TEST_OWNERS", profile = profile, database = database)
      val expectedResourceOwner = Generators.generateResourceOwner

      for {
        _ <- store.init()
        _ <- store.put(expectedResourceOwner)
        actualResourceOwner <- store.get(expectedResourceOwner.username)
        someResourceOwners <- store.all
        containsOwnerAfterPut <- store.contains(expectedResourceOwner.username)
        _ <- store.delete(expectedResourceOwner.username)
        missingResourceOwner <- store.get(expectedResourceOwner.username)
        containsOwnerAfterDelete <- store.contains(expectedResourceOwner.username)
        noResourceOwners <- store.all
        _ <- store.drop()
      } yield {
        actualResourceOwner should be(Some(expectedResourceOwner))
        someResourceOwners should be(Seq(expectedResourceOwner))
        containsOwnerAfterPut should be(true)
        missingResourceOwner should be(None)
        noResourceOwners should be(Seq.empty)
        containsOwnerAfterDelete should be(false)
      }
    }
  }

  it should "provide a read-only view" in withRetry {
    withStore { (profile, database) =>
      val store = new DefaultResourceOwnerStore(name = "TEST_OWNERS", profile = profile, database = database)
      val storeView = store.view

      val expectedResourceOwner = Generators.generateResourceOwner

      for {
        _ <- store.init()
        _ <- store.put(expectedResourceOwner)
        actualResourceOwner <- storeView.get(expectedResourceOwner.username)
        someResourceOwners <- storeView.all
        containsOwnerAfterPut <- storeView.contains(expectedResourceOwner.username)
        _ <- store.delete(expectedResourceOwner.username)
        missingResourceOwner <- storeView.get(expectedResourceOwner.username)
        containsOwnerAfterDelete <- storeView.contains(expectedResourceOwner.username)
        noResourceOwners <- storeView.all
        _ <- store.drop()
      } yield {
        actualResourceOwner should be(Some(expectedResourceOwner))
        someResourceOwners should be(Seq(expectedResourceOwner))
        containsOwnerAfterPut should be(true)
        missingResourceOwner should be(None)
        noResourceOwners should be(Seq.empty)
        containsOwnerAfterDelete should be(false)
        a[ClassCastException] should be thrownBy {
          val _ = storeView.asInstanceOf[ResourceOwnerStore]
        }
      }
    }
  }

  it should "provide migrations" in withRetry {
    withClue("from key-value store to version 1") {
      withStore { (profile, database) =>
        import play.api.libs.json._

        val name = "TEST_OWNERS"
        val legacy = LegacyKeyValueStore(name = name, profile = profile, database = database)
        val current = new DefaultResourceOwnerStore(name = name, profile = profile, database = database)

        val owners = Seq(
          Generators.generateResourceOwner,
          Generators.generateResourceOwner,
          Generators.generateResourceOwner
        )

        val jsonOwners = owners.map { owner =>
          owner.username -> Json
            .obj(
              "username" -> owner.username,
              "password" -> Json.toJson(Base64.getUrlEncoder.encodeToString(owner.password.value.toArray)),
              "salt" -> owner.salt,
              "allowedScopes" -> owner.allowedScopes,
              "active" -> owner.active,
              "subject" -> owner.subject
            )
            .toString()
            .getBytes(StandardCharsets.UTF_8)
        }

        for {
          _ <- legacy.create()
          _ <- Future.sequence(jsonOwners.map(e => legacy.insert(e._1, e._2)))
          migration = current.migrations.find(_.version == 1) match {
            case Some(migration) => migration
            case None            => fail("Expected migration with version == 1 but none was found")
          }
          currentResultBefore <- current.all.failed
          neededBefore <- migration.needed.run()
          _ <- migration.action.run()
          neededAfter <- migration.needed.run()
          currentResultAfter <- current.all
          _ <- current.drop()
        } yield {
          currentResultBefore.getMessage should include("""Column "USERNAME" not found""")
          neededBefore should be(true)

          neededAfter should be(false)

          val now = Instant.now()
          currentResultAfter.map(_.copy(created = now, updated = now)).sortBy(_.username) should be(
            owners.map(_.copy(created = now, updated = now)).sortBy(_.username)
          )
        }
      }
    }
  }

  private implicit val telemetry: MockTelemetryContext = MockTelemetryContext()

  private implicit val system: ActorSystem[Nothing] = ActorSystem(
    guardianBehavior = Behaviors.ignore,
    "ResourceOwnerStoreSpec"
  )
}
