package stasis.identity.persistence.codes

import scala.concurrent.Future
import scala.concurrent.duration._

import org.apache.pekko.Done
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.scaladsl.Behaviors

import stasis.identity.model.Generators
import stasis.identity.model.clients.Client
import stasis.identity.model.codes.StoredAuthorizationCode
import stasis.layers.UnitSpec
import stasis.layers.persistence.memory.MemoryStore
import stasis.layers.telemetry.MockTelemetryContext
import stasis.layers.telemetry.TelemetryContext

class DefaultAuthorizationCodeStoreSpec extends UnitSpec {
  "A DefaultAuthorizationCodeStore" should "add, retrieve and delete authorization codes" in withRetry {
    val store = new DefaultAuthorizationCodeStore(
      name = "TEST_CODES",
      expiration = 3.seconds,
      backend = MemoryStore(name = "code-store")
    )

    val expectedCode = Generators.generateAuthorizationCode
    val client = Client.generateId()
    val owner = Generators.generateResourceOwner
    val expectedStoredCode = StoredAuthorizationCode(expectedCode, client, owner, scope = None)

    for {
      _ <- store.put(StoredAuthorizationCode(expectedCode, client, owner, scope = None))
      actualCode <- store.get(expectedCode)
      someCodes <- store.all
      _ <- store.delete(expectedCode)
      missingCode <- store.get(expectedCode)
      noCodes <- store.all
    } yield {
      actualCode.map(_.copy(created = expectedStoredCode.created)) should be(Some(expectedStoredCode))
      someCodes.map(_.copy(created = expectedStoredCode.created)) should be(Seq(expectedStoredCode))
      missingCode should be(None)
      noCodes should be(Seq.empty)
    }
  }

  it should "expire authorization codes" in withRetry {
    val expiration = 50.millis

    val store = new DefaultAuthorizationCodeStore(
      name = "TEST_CODES",
      expiration = expiration,
      backend = MemoryStore(name = "code-store")
    )

    val expectedCode = Generators.generateAuthorizationCode
    val client = Client.generateId()
    val owner = Generators.generateResourceOwner
    val expectedStoredCode = StoredAuthorizationCode(expectedCode, client, owner, scope = None)

    for {
      _ <- store.put(StoredAuthorizationCode(expectedCode, client, owner, scope = None))
      actualCode <- store.get(expectedCode)
      someCodes <- store.all
      _ <- after(expiration * 2, using = system)(Future.successful(Done))
      missingCode <- store.get(expectedCode)
      noCodes <- store.all
    } yield {
      actualCode.map(_.copy(created = expectedStoredCode.created)) should be(Some(expectedStoredCode))
      someCodes.map(_.copy(created = expectedStoredCode.created)) should be(Seq(expectedStoredCode))
      missingCode should be(None)
      noCodes should be(Seq.empty)
    }
  }

  private implicit val telemetry: TelemetryContext = MockTelemetryContext()

  private implicit val system: ActorSystem[Nothing] = ActorSystem(
    guardianBehavior = Behaviors.ignore,
    "AuthorizationCodeStoreSpec"
  )
}
