package stasis.test.specs.unit.identity.persistence.clients

import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.Base64

import scala.concurrent.Future

import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.scaladsl.Behaviors

import stasis.identity.persistence.clients.ClientStore
import stasis.identity.persistence.clients.DefaultClientStore
import stasis.identity.persistence.internal.LegacyKeyValueStore
import stasis.layers.UnitSpec
import stasis.layers.persistence.SlickTestDatabase
import stasis.layers.telemetry.MockTelemetryContext
import stasis.test.specs.unit.identity.model.Generators

class DefaultClientStoreSpec extends UnitSpec with SlickTestDatabase {
  "A DefaultClientStore" should "add, retrieve and delete clients" in withRetry {
    withStore { (profile, database) =>
      val store = new DefaultClientStore(name = "TEST_CLIENTS", profile = profile, database = database)

      val expectedClient = Generators.generateClient

      for {
        _ <- store.init()
        _ <- store.put(expectedClient)
        actualClient <- store.get(expectedClient.id)
        someClients <- store.all
        _ <- store.delete(expectedClient.id)
        missingClient <- store.get(expectedClient.id)
        noClients <- store.all
        _ <- store.drop()
      } yield {
        actualClient should be(Some(expectedClient))
        someClients should be(Seq(expectedClient))
        missingClient should be(None)
        noClients should be(Seq.empty)
      }
    }
  }

  it should "provide a read-only view" in withRetry {
    withStore { (profile, database) =>
      val store = new DefaultClientStore(name = "TEST_CLIENTS", profile = profile, database = database)
      val storeView = store.view

      val expectedClient = Generators.generateClient

      for {
        _ <- store.init()
        _ <- store.put(expectedClient)
        actualClient <- storeView.get(expectedClient.id)
        someClients <- storeView.all
        _ <- store.delete(expectedClient.id)
        missingClient <- storeView.get(expectedClient.id)
        noClients <- storeView.all
        _ <- store.drop()
      } yield {
        actualClient should be(Some(expectedClient))
        someClients should be(Seq(expectedClient))
        missingClient should be(None)
        noClients should be(Seq.empty)
        a[ClassCastException] should be thrownBy {
          val _ = storeView.asInstanceOf[ClientStore]
        }
      }
    }
  }

  it should "provide migrations" in withRetry {
    withClue("from key-value store to version 1") {
      withStore { (profile, database) =>
        import play.api.libs.json._

        val name = "TEST_CLIENTS"
        val legacy = LegacyKeyValueStore(name = name, profile = profile, database = database)
        val current = new DefaultClientStore(name = name, profile = profile, database = database)

        val clients = Seq(
          Generators.generateClient,
          Generators.generateClient,
          Generators.generateClient
        )

        val jsonClients = clients.map { client =>
          client.id.toString -> Json
            .obj(
              "id" -> client.id,
              "redirect_uri" -> client.redirectUri,
              "token_expiration" -> client.tokenExpiration.value,
              "secret" -> Json.toJson(Base64.getUrlEncoder.encodeToString(client.secret.value.toArray)),
              "salt" -> client.salt,
              "active" -> client.active,
              "subject" -> client.subject
            )
            .toString()
            .getBytes(StandardCharsets.UTF_8)
        }

        for {
          _ <- legacy.create()
          _ <- Future.sequence(jsonClients.map(e => legacy.insert(e._1, e._2)))
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

          val now = Instant.now()
          currentResultAfter.map(_.copy(created = now, updated = now)).sortBy(_.id) should be(
            clients.map(_.copy(created = now, updated = now)).sortBy(_.id)
          )
        }
      }
    }
  }

  private implicit val telemetry: MockTelemetryContext = MockTelemetryContext()

  private implicit val system: ActorSystem[Nothing] = ActorSystem(
    guardianBehavior = Behaviors.ignore,
    name = "DefaultClientStoreSpec"
  )
}
