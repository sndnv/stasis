package stasis.test.specs.unit.identity.model.codes

import akka.Done
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorSystem, Behavior, SpawnProtocol}
import stasis.core.persistence.backends.memory.MemoryBackend
import stasis.identity.model.clients.Client
import stasis.identity.model.codes.{AuthorizationCode, AuthorizationCodeStore, StoredAuthorizationCode}
import stasis.test.specs.unit.AsyncUnitSpec
import stasis.test.specs.unit.core.telemetry.MockTelemetryContext
import stasis.test.specs.unit.identity.model.Generators

import scala.concurrent.Future
import scala.concurrent.duration._

class AuthorizationCodeStoreSpec extends AsyncUnitSpec {
  "An AuthorizationCodeStore" should "add, retrieve and delete authorization codes" in {
    val store = createStore()

    val expectedCode = Generators.generateAuthorizationCode
    val client = Client.generateId()
    val owner = Generators.generateResourceOwner
    val expectedStoredCode = StoredAuthorizationCode(expectedCode, client, owner, scope = None)

    for {
      _ <- store.put(StoredAuthorizationCode(expectedCode, client, owner, scope = None))
      actualCode <- store.get(expectedCode)
      someCodes <- store.codes
      _ <- store.delete(expectedCode)
      missingCode <- store.get(expectedCode)
      noCodes <- store.codes
    } yield {
      actualCode should be(Some(expectedStoredCode))
      someCodes should be(Map(expectedCode -> expectedStoredCode))
      missingCode should be(None)
      noCodes should be(Map.empty)
    }
  }

  it should "expire authorization codes" in {
    val expiration = 50.millis
    val store = createStore(expiration)

    val expectedCode = Generators.generateAuthorizationCode
    val client = Client.generateId()
    val owner = Generators.generateResourceOwner
    val expectedStoredCode = StoredAuthorizationCode(expectedCode, client, owner, scope = None)

    for {
      _ <- store.put(StoredAuthorizationCode(expectedCode, client, owner, scope = None))
      actualCode <- store.get(expectedCode)
      someCodes <- store.codes
      _ <- after(expiration * 2, using = system)(Future.successful(Done))
      missingCode <- store.get(expectedCode)
      noCodes <- store.codes
    } yield {
      actualCode should be(Some(expectedStoredCode))
      someCodes should be(Map(expectedCode -> expectedStoredCode))
      missingCode should be(None)
      noCodes should be(Map.empty)
    }
  }

  private implicit val system: ActorSystem[SpawnProtocol.Command] = ActorSystem(
    Behaviors.setup(_ => SpawnProtocol()): Behavior[SpawnProtocol.Command],
    "AuthorizationCodeStoreSpec"
  )

  private implicit val telemetry: MockTelemetryContext = MockTelemetryContext()

  private def createStore(expiration: FiniteDuration = 3.seconds): AuthorizationCodeStore =
    AuthorizationCodeStore(
      expiration = expiration,
      MemoryBackend[AuthorizationCode, StoredAuthorizationCode](name = s"code-store-${java.util.UUID.randomUUID()}")
    )
}
