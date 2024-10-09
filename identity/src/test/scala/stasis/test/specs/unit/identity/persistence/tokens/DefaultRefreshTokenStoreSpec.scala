package stasis.test.specs.unit.identity.persistence.tokens

import java.nio.charset.StandardCharsets
import java.time.Instant

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration._

import org.apache.pekko.Done
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.scalatest.Assertion
import org.scalatest.concurrent.Eventually

import stasis.identity.model.clients.Client
import stasis.identity.model.tokens.StoredRefreshToken
import stasis.identity.persistence.internal.LegacyKeyValueStore
import stasis.identity.persistence.tokens.DefaultRefreshTokenStore
import stasis.layers.UnitSpec
import stasis.layers.persistence.SlickTestDatabase
import stasis.layers.persistence.memory.MemoryStore
import stasis.layers.telemetry.MockTelemetryContext
import stasis.layers.telemetry.TelemetryContext
import stasis.test.specs.unit.identity.model.Generators

class DefaultRefreshTokenStoreSpec extends UnitSpec with Eventually with SlickTestDatabase {
  "A DefaultRefreshTokenStore" should "add, retrieve and delete refresh tokens" in withRetry {
    withStore { (profile, database) =>
      val store = new DefaultRefreshTokenStore(
        name = "TEST_TOKENS",
        profile = profile,
        database = database,
        expiration = 3.seconds,
        directory = MemoryStore(name = "token-directory")
      )

      val client = Client.generateId()
      val owner = Generators.generateResourceOwner
      val expectedToken = Generators.generateRefreshToken

      for {
        _ <- store.init()
        _ <- store.put(client, expectedToken, owner, scope = None)
        actualToken <- store.get(expectedToken)
        someTokens <- store.all
        tokenDeleted <- store.delete(expectedToken)
        missingToken <- store.get(expectedToken)
        tokenNotDeleted <- store.delete(expectedToken)
        noTokens <- store.all
        _ <- store.drop()
      } yield {
        val expectedStoredToken = StoredRefreshToken(
          token = expectedToken,
          client = client,
          owner = owner.username,
          scope = None,
          expiration = actualToken.map(_.expiration).getOrElse(Instant.MIN),
          created = Instant.now()
        )

        tokenDeleted should be(true)
        tokenNotDeleted should be(false)

        actualToken.map(_.copy(created = expectedStoredToken.created)) should be(Some(expectedStoredToken))
        someTokens.map(_.copy(created = expectedStoredToken.created)) should be(Seq(expectedStoredToken))
        missingToken should be(None)
        noTokens should be(Seq.empty)
      }
    }
  }

  it should "expire refresh tokens" in withRetry {
    withStore { (profile, database) =>
      val expiration = 50.millis
      val store = new DefaultRefreshTokenStore(
        name = "TEST_TOKENS",
        profile = profile,
        database = database,
        expiration = expiration,
        directory = MemoryStore(name = "token-directory")
      )

      val client = Client.generateId()
      val owner = Generators.generateResourceOwner
      val expectedToken = Generators.generateRefreshToken

      store.init().await
      store.put(client, expectedToken, owner, scope = None).await

      eventually[Assertion] {
        store.get(expectedToken).await should be(None)
        store.all.await should be(Seq.empty)
      }

      store.drop().await
      succeed
    }
  }

  it should "expire existing refresh tokens at startup" in withRetry {
    withStore { (profile, database) =>
      import slick.jdbc.H2Profile.api._

      val owner = Generators.generateResourceOwner

      {
        val store = new DefaultRefreshTokenStore(
          name = "TEST_TOKENS",
          profile = profile,
          database = database,
          expiration = 1.minute,
          directory = MemoryStore(name = "token-directory")
        )

        val nonExpiredClient = Client.generateId()

        val result = for {
          _ <- store.init()
          _ <- store.put(client = Client.generateId(), token = Generators.generateRefreshToken, owner = owner, scope = None)
          _ <- store.put(client = Client.generateId(), token = Generators.generateRefreshToken, owner = owner, scope = None)
          _ <- store.put(client = nonExpiredClient, token = Generators.generateRefreshToken, owner = owner, scope = None)
          updatedExpiration = Instant.now().minusSeconds(30).toString
          _ <- database.run(
            sqlu"""UPDATE TEST_TOKENS SET EXPIRATION = '#$updatedExpiration' WHERE CLIENT != '#${nonExpiredClient.toString}' """
          )
        } yield {
          Done
        }

        result.await
      }

      val store = new DefaultRefreshTokenStore(
        name = "TEST_TOKENS",
        profile = profile,
        database = database,
        expiration = 1.minute,
        directory = MemoryStore(name = "token-directory")
      )

      eventually[Assertion] {
        val remainingTokens = store.all.await
        remainingTokens.size should be(1)
      }

      store.drop().await
      succeed
    }
  }

  it should "not allow more than one refresh token for the same owner using the same client" in withRetry {
    withStore { (profile, database) =>
      val store = new DefaultRefreshTokenStore(
        name = "TEST_TOKENS",
        profile = profile,
        database = database,
        expiration = 3.seconds,
        directory = MemoryStore(name = "token-directory")
      )

      val client = Client.generateId()
      val owner = Generators.generateResourceOwner
      val existingToken = Generators.generateRefreshToken
      val newToken = Generators.generateRefreshToken

      for {
        _ <- store.init()
        _ <- store.put(client, existingToken, owner, scope = None)
        _ <- store.put(client, newToken, owner, scope = None)
        tokens <- store.all
        _ <- store.drop()
      } yield {
        val expectedStoredToken = StoredRefreshToken(
          token = newToken,
          client = client,
          owner = owner.username,
          scope = None,
          expiration = Instant.MIN,
          created = Instant.now()
        )

        tokens.map(_.copy(expiration = Instant.MIN, created = expectedStoredToken.created)) should be(Seq(expectedStoredToken))
      }
    }
  }

  it should "provide migrations" in withRetry {
    withClue("from key-value store to version 1") {
      withStore { (profile, database) =>
        import play.api.libs.json._

        val expiration = 3.seconds

        val name = "TEST_TOKENS"
        val legacy = LegacyKeyValueStore(name = name, profile = profile, database = database)

        val current = new DefaultRefreshTokenStore(
          name = name,
          profile = profile,
          database = database,
          expiration = expiration,
          directory = MemoryStore(name = "token-directory")
        )

        val client = Client.generateId()
        val owner = Generators.generateResourceOwner
        val now = Instant.now()

        val tokens = Seq(
          Generators.generateRefreshToken,
          Generators.generateRefreshToken,
          Generators.generateRefreshToken
        ).map { token =>
          StoredRefreshToken(
            token = token,
            client = client,
            owner = owner.username,
            scope = Some("test-scope"),
            expiration = now.plusSeconds(expiration.toSeconds),
            created = now
          )
        }

        val jsonTokens = tokens.map { token =>
          token.token.value -> Json
            .obj(
              "token" -> token.token.value,
              "client" -> token.client,
              "owner" -> token.owner,
              "scope" -> token.scope,
              "expiration" -> token.expiration
            )
            .toString()
            .getBytes(StandardCharsets.UTF_8)
        }

        for {
          _ <- legacy.create()
          _ <- Future.sequence(jsonTokens.map(e => legacy.insert(e._1, e._2)))
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
          currentResultBefore.getMessage should include("""Column "TOKEN" not found""")
          neededBefore should be(true)

          neededAfter should be(false)
          currentResultAfter.map(_.copy(created = now)).sortBy(_.token.value) should be(
            tokens.map(_.copy(created = now)).sortBy(_.token.value)
          )
        }
      }
    }
  }

  override implicit val patienceConfig: PatienceConfig = PatienceConfig(5.seconds, 250.milliseconds)

  private implicit val telemetry: TelemetryContext = MockTelemetryContext()

  private implicit val system: ActorSystem[Nothing] = ActorSystem(
    guardianBehavior = Behaviors.ignore,
    "RefreshTokenStoreSpec"
  )

  override implicit def executionContext: ExecutionContext = system.executionContext
}
