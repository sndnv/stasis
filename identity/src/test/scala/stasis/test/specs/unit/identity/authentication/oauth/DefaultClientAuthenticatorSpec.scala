package stasis.test.specs.unit.identity.authentication.oauth

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorSystem, Behavior, SpawnProtocol}
import akka.http.scaladsl.model.headers.BasicHttpCredentials
import stasis.core.persistence.backends.memory.MemoryBackend
import stasis.core.security.exceptions.AuthenticationFailure
import stasis.identity.authentication.oauth.DefaultClientAuthenticator
import stasis.identity.model.clients.{Client, ClientStore}
import stasis.identity.model.secrets.Secret
import stasis.test.specs.unit.AsyncUnitSpec
import stasis.test.specs.unit.identity.model.Generators

import scala.concurrent.duration._
import scala.util.control.NonFatal

class DefaultClientAuthenticatorSpec extends AsyncUnitSpec {
  "A DefaultClientAuthenticator" should "successfully authenticate clients" in {
    val store = ClientStore(
      MemoryBackend[Client.Id, Client](name = s"client-store-${java.util.UUID.randomUUID()}")
    )

    val expectedClient = Generators.generateClient.copy(
      secret = clientSecret,
      salt = clientSalt
    )

    val authenticator = new DefaultClientAuthenticator(
      store = store.view,
      secretConfig = secretConfig
    )

    for {
      _ <- store.put(expectedClient)
      actualClient <- authenticator.authenticate(BasicHttpCredentials(expectedClient.id.toString, clientPassword))
    } yield {
      actualClient should be(expectedClient)
    }
  }

  it should "fail to authenticate inactive clients" in {
    val store = ClientStore(
      MemoryBackend[Client.Id, Client](name = s"client-store-${java.util.UUID.randomUUID()}")
    )

    val expectedClient = Generators.generateClient.copy(
      secret = clientSecret,
      salt = clientSalt,
      active = false
    )

    val authenticator = new DefaultClientAuthenticator(
      store = store.view,
      secretConfig = secretConfig
    )

    store
      .put(expectedClient)
      .flatMap { _ =>
        authenticator.authenticate(BasicHttpCredentials(expectedClient.id.toString, clientPassword))
      }
      .map(result => fail(s"Unexpected result received: [$result]"))
      .recoverWith {
        case NonFatal(e) =>
          e shouldBe an[AuthenticationFailure]
          e.getMessage.contains(s"Client [${expectedClient.id}] is not active") should be(true)
      }
  }

  it should "fail to authenticate missing clients" in {
    val store = ClientStore(
      MemoryBackend[Client.Id, Client](name = s"client-store-${java.util.UUID.randomUUID()}")
    )

    val expectedClient = Generators.generateClient.copy(
      secret = clientSecret,
      salt = clientSalt
    )

    val authenticator = new DefaultClientAuthenticator(
      store = store.view,
      secretConfig = secretConfig
    )

    authenticator
      .authenticate(BasicHttpCredentials(expectedClient.id.toString, clientPassword))
      .map(result => fail(s"Unexpected result received: [$result]"))
      .recoverWith {
        case NonFatal(e) =>
          e shouldBe an[AuthenticationFailure]
          e.getMessage.contains(s"Client [${expectedClient.id}] was not found") should be(true)
      }
  }

  it should "fail to authenticate clients with invalid IDs" in {
    val store = ClientStore(
      MemoryBackend[Client.Id, Client](name = s"client-store-${java.util.UUID.randomUUID()}")
    )

    val clientId = "some-client"

    val authenticator = new DefaultClientAuthenticator(
      store = store.view,
      secretConfig = secretConfig
    )

    authenticator
      .authenticate(BasicHttpCredentials(clientId, clientPassword))
      .map(result => fail(s"Unexpected result received: [$result]"))
      .recoverWith {
        case NonFatal(e) =>
          e shouldBe an[AuthenticationFailure]
          e.getMessage.contains(s"Invalid client identifier provided: [$clientId]") should be(true)
      }
  }

  private implicit val system: ActorSystem[SpawnProtocol.Command] = ActorSystem(
    Behaviors.setup(_ => SpawnProtocol()): Behavior[SpawnProtocol.Command],
    "DefaultClientAuthenticatorSpec"
  )

  private implicit val untypedSystem: akka.actor.ActorSystem = system.classicSystem

  private implicit val secretConfig: Secret.ClientConfig = Secret.ClientConfig(
    algorithm = "PBKDF2WithHmacSHA512",
    iterations = 10000,
    derivedKeySize = 32,
    saltSize = 16,
    authenticationDelay = 50.millis
  )

  private val clientPassword = "some-password"
  private val clientSalt = "some-salt"
  private val clientSecret = Secret.derive(clientPassword, clientSalt)
}
