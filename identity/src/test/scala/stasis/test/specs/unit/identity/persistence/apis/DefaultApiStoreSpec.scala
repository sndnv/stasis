package stasis.test.specs.unit.identity.persistence.apis

import java.nio.charset.StandardCharsets

import scala.concurrent.Future

import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.scaladsl.Behaviors

import stasis.identity.persistence.apis.ApiStore
import stasis.identity.persistence.apis.DefaultApiStore
import stasis.identity.persistence.internal.LegacyKeyValueStore
import stasis.layers.UnitSpec
import stasis.layers.persistence.SlickTestDatabase
import stasis.layers.telemetry.MockTelemetryContext
import stasis.test.specs.unit.identity.model.Generators

class DefaultApiStoreSpec extends UnitSpec with SlickTestDatabase {
  "A DefaultApiStore" should "add, retrieve and delete APIs" in withRetry {
    withStore { (profile, database) =>
      val store = new DefaultApiStore(name = "TEST_APIS", profile = profile, database = database)
      val expectedApi = Generators.generateApi

      for {
        _ <- store.init()
        _ <- store.put(expectedApi)
        actualApi <- store.get(expectedApi.id)
        someApis <- store.all
        containsApiAfterPut <- store.contains(expectedApi.id)
        _ <- store.delete(expectedApi.id)
        missingApi <- store.get(expectedApi.id)
        containsApiAfterDelete <- store.contains(expectedApi.id)
        noApis <- store.all
        _ <- store.drop()
      } yield {
        actualApi should be(Some(expectedApi))
        someApis should be(Seq(expectedApi))
        containsApiAfterPut should be(true)
        missingApi should be(None)
        noApis should be(Seq.empty)
        containsApiAfterDelete should be(false)
      }
    }
  }

  it should "provide a read-only view" in withRetry {
    withStore { (profile, database) =>
      val store = new DefaultApiStore(name = "TEST_APIS", profile = profile, database = database)
      val storeView = store.view

      val expectedApi = Generators.generateApi

      for {
        _ <- store.init()
        _ <- store.put(expectedApi)
        actualApi <- storeView.get(expectedApi.id)
        someApis <- storeView.all
        containsApiAfterPut <- storeView.contains(expectedApi.id)
        _ <- store.delete(expectedApi.id)
        missingApi <- storeView.get(expectedApi.id)
        containsApiAfterDelete <- storeView.contains(expectedApi.id)
        noApis <- storeView.all
        _ <- store.drop()
      } yield {
        actualApi should be(Some(expectedApi))
        someApis should be(Seq(expectedApi))
        containsApiAfterPut should be(true)
        missingApi should be(None)
        noApis should be(Seq.empty)
        containsApiAfterDelete should be(false)
        a[ClassCastException] should be thrownBy {
          val _ = storeView.asInstanceOf[ApiStore]
        }
      }
    }
  }

  it should "provide migrations" in withRetry {
    withClue("from key-value store to version 1") {
      withStore { (profile, database) =>
        import play.api.libs.json._

        val name = "TEST_APIS"
        val legacy = LegacyKeyValueStore(name = name, profile = profile, database = database)
        val current = new DefaultApiStore(name = name, profile = profile, database = database)

        val apis = Seq(
          Generators.generateApi,
          Generators.generateApi,
          Generators.generateApi
        )

        val jsonApis = apis.map { api => api.id -> Json.obj("id" -> api.id).toString().getBytes(StandardCharsets.UTF_8) }

        for {
          _ <- legacy.create()
          _ <- Future.sequence(jsonApis.map(e => legacy.insert(e._1, e._2)))
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
          currentResultBefore.getMessage should include("""Column "ID" not found""")
          neededBefore should be(true)

          neededAfter should be(false)
          currentResultAfter.map(_.id).sorted should be(apis.map(_.id).sorted)
        }
      }
    }
  }

  private implicit val telemetry: MockTelemetryContext = MockTelemetryContext()

  private implicit val system: ActorSystem[Nothing] = ActorSystem(
    guardianBehavior = Behaviors.ignore,
    "ApiStoreSpec"
  )
}
