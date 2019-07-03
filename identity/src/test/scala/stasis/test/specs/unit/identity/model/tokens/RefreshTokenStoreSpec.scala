package stasis.test.specs.unit.identity.model.tokens

import akka.Done
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorSystem, Behavior, SpawnProtocol}
import stasis.core.persistence.backends.memory.MemoryBackend
import stasis.identity.model.clients.Client
import stasis.identity.model.tokens.{RefreshTokenStore, StoredRefreshToken}
import stasis.test.specs.unit.AsyncUnitSpec
import stasis.test.specs.unit.identity.model.Generators

import scala.concurrent.Future
import scala.concurrent.duration._

class RefreshTokenStoreSpec extends AsyncUnitSpec {
  "An RefreshTokenStore" should "add, retrieve and delete refresh tokens" in {
    val store = createStore()

    val client = Client.generateId()
    val owner = Generators.generateResourceOwner
    val expectedToken = Generators.generateRefreshToken
    val expectedStoredToken = StoredRefreshToken(expectedToken, owner, scope = None)

    for {
      _ <- store.put(client, expectedToken, owner, scope = None)
      actualToken <- store.get(client)
      someTokens <- store.tokens
      _ <- store.delete(client)
      missingToken <- store.get(client)
      noTokens <- store.tokens
    } yield {
      actualToken should be(Some(expectedStoredToken))
      someTokens should be(Map(client -> expectedStoredToken))
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
    val expectedStoredToken = StoredRefreshToken(expectedToken, owner, scope = None)

    for {
      _ <- store.put(client, expectedToken, owner, scope = None)
      actualToken <- store.get(client)
      someTokens <- store.tokens
      _ <- akka.pattern.after(expiration * 2, using = system.scheduler)(Future.successful(Done))
      missingToken <- store.get(client)
      noTokens <- store.tokens
    } yield {
      actualToken should be(Some(expectedStoredToken))
      someTokens should be(Map(client -> expectedStoredToken))
      missingToken should be(None)
      noTokens should be(Map.empty)
    }
  }

  private implicit val system: ActorSystem[SpawnProtocol] = ActorSystem(
    Behaviors.setup(_ => SpawnProtocol.behavior): Behavior[SpawnProtocol],
    "RefreshTokenStoreSpec"
  )

  private def createStore(expiration: FiniteDuration = 3.seconds): RefreshTokenStore = RefreshTokenStore(
    expiration = expiration,
    MemoryBackend[Client.Id, StoredRefreshToken](name = s"token-store-${java.util.UUID.randomUUID()}")
  )
}
