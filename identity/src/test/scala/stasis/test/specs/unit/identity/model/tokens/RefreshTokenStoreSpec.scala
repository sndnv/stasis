package stasis.test.specs.unit.identity.model.tokens

import java.time.Instant

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorSystem, Behavior, SpawnProtocol}
import org.scalatest.Assertion
import org.scalatest.concurrent.Eventually
import stasis.core.persistence.backends.memory.MemoryBackend
import stasis.identity.model.clients.Client
import stasis.identity.model.owners.ResourceOwner
import stasis.identity.model.tokens.{RefreshToken, RefreshTokenStore, StoredRefreshToken}
import stasis.test.specs.unit.AsyncUnitSpec
import stasis.test.specs.unit.identity.model.Generators

import scala.concurrent.duration._

class RefreshTokenStoreSpec extends AsyncUnitSpec with Eventually {
  "An RefreshTokenStore" should "add, retrieve and delete refresh tokens" in {
    val store = createStore()

    val client = Client.generateId()
    val owner = Generators.generateResourceOwner
    val expectedToken = Generators.generateRefreshToken

    for {
      _ <- store.put(client, expectedToken, owner, scope = None)
      actualToken <- store.get(expectedToken)
      someTokens <- store.tokens
      _ <- store.delete(expectedToken)
      missingToken <- store.get(expectedToken)
      noTokens <- store.tokens
    } yield {
      val expectedStoredToken = StoredRefreshToken(
        token = expectedToken,
        client = client,
        owner = owner,
        scope = None,
        expiration = actualToken.map(_.expiration).getOrElse(Instant.MIN)
      )

      actualToken should be(Some(expectedStoredToken))
      someTokens should be(Map(expectedToken -> expectedStoredToken))
      missingToken should be(None)
      noTokens should be(Map.empty)
    }
  }

  it should "expire refresh tokens" in {
    val expiration = 50.millis
    val store = createStore(expiration)

    val client = Client.generateId()
    val owner = Generators.generateResourceOwner
    val expectedToken = Generators.generateRefreshToken

    store.put(client, expectedToken, owner, scope = None).await

    eventually[Assertion] {
      store.get(expectedToken).await should be(None)
      store.tokens.await should be(Map.empty)
    }
  }

  it should "expire existing refresh tokens at startup" in {
    val backend = MemoryBackend[RefreshToken, StoredRefreshToken](
      name = s"token-store-${java.util.UUID.randomUUID()}"
    )
    val directory = MemoryBackend[(Client.Id, ResourceOwner.Id), RefreshToken](
      name = s"token-directory-${java.util.UUID.randomUUID()}"
    )

    val owner = Generators.generateResourceOwner

    val tokens = Seq(
      StoredRefreshToken(
        token = Generators.generateRefreshToken,
        client = Client.generateId(),
        owner = owner,
        scope = None,
        expiration = Instant.now().minusSeconds(1)
      ),
      StoredRefreshToken(
        token = Generators.generateRefreshToken,
        client = Client.generateId(),
        owner = owner,
        scope = None,
        expiration = Instant.now().minusSeconds(2)
      ),
      StoredRefreshToken(
        token = Generators.generateRefreshToken,
        client = Client.generateId(),
        owner = owner,
        scope = None,
        expiration = Instant.now().plusSeconds(42)
      )
    )

    tokens.foreach(token => backend.put(token.token, token).await)

    val store = RefreshTokenStore(expiration = 3.seconds, backend = backend, directory = directory)
    eventually[Assertion] {
      val remainingTokens = store.tokens.await
      remainingTokens.values.toSeq should be(tokens.drop(2))
    }
  }

  it should "not allow more than one refresh token for the same owner using the same client" in {
    val store = createStore()

    val client = Client.generateId()
    val owner = Generators.generateResourceOwner
    val existingToken = Generators.generateRefreshToken
    val newToken = Generators.generateRefreshToken

    for {
      _ <- store.put(client, existingToken, owner, scope = None)
      _ <- store.put(client, newToken, owner, scope = None)
      tokens <- store.tokens
    } yield {
      val expectedStoredToken = StoredRefreshToken(
        token = newToken,
        client = client,
        owner = owner,
        scope = None,
        expiration = Instant.MIN
      )

      tokens.values.map(_.copy(expiration = Instant.MIN)) should be(Seq(expectedStoredToken))
    }
  }

  override implicit val patienceConfig: PatienceConfig = PatienceConfig(5.seconds, 250.milliseconds)

  private implicit val system: ActorSystem[SpawnProtocol.Command] = ActorSystem(
    Behaviors.setup(_ => SpawnProtocol()): Behavior[SpawnProtocol.Command],
    "RefreshTokenStoreSpec"
  )

  private def createStore(expiration: FiniteDuration = 3.seconds): RefreshTokenStore =
    RefreshTokenStore(
      expiration = expiration,
      MemoryBackend[RefreshToken, StoredRefreshToken](name = s"token-store-${java.util.UUID.randomUUID()}"),
      MemoryBackend[(Client.Id, ResourceOwner.Id), RefreshToken](name = s"token-directory-${java.util.UUID.randomUUID()}")
    )
}
