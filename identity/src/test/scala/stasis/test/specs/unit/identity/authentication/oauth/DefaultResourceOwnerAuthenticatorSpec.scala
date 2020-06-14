package stasis.test.specs.unit.identity.authentication.oauth

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorSystem, Behavior, SpawnProtocol}
import akka.http.scaladsl.model.headers.BasicHttpCredentials
import stasis.core.persistence.backends.memory.MemoryBackend
import stasis.core.security.exceptions.AuthenticationFailure
import stasis.identity.authentication.oauth.DefaultResourceOwnerAuthenticator
import stasis.identity.model.owners.{ResourceOwner, ResourceOwnerStore}
import stasis.identity.model.secrets.Secret
import stasis.test.specs.unit.AsyncUnitSpec
import stasis.test.specs.unit.identity.model.Generators

import scala.concurrent.duration._
import scala.util.control.NonFatal

class DefaultResourceOwnerAuthenticatorSpec extends AsyncUnitSpec {
  "A DefaultResourceOwnerAuthenticator" should "successfully authenticate resource owners" in {
    val store = ResourceOwnerStore(
      MemoryBackend[ResourceOwner.Id, ResourceOwner](name = s"owner-store-${java.util.UUID.randomUUID()}")
    )

    val expectedOwner = Generators.generateResourceOwner.copy(
      password = ownerSecret,
      salt = ownerSalt
    )

    val authenticator = new DefaultResourceOwnerAuthenticator(
      store = store.view,
      secretConfig = secretConfig
    )

    for {
      _ <- store.put(expectedOwner)
      actualClient <- authenticator.authenticate(BasicHttpCredentials(expectedOwner.username, ownerPassword))
    } yield {
      actualClient should be(expectedOwner)
    }
  }

  it should "fail to authenticate inactive resource owners" in {
    val store = ResourceOwnerStore(
      MemoryBackend[ResourceOwner.Id, ResourceOwner](name = s"owner-store-${java.util.UUID.randomUUID()}")
    )

    val expectedOwner = Generators.generateResourceOwner.copy(
      password = ownerSecret,
      salt = ownerSalt,
      active = false
    )

    val authenticator = new DefaultResourceOwnerAuthenticator(
      store = store.view,
      secretConfig = secretConfig
    )

    store
      .put(expectedOwner)
      .flatMap { _ =>
        authenticator.authenticate(BasicHttpCredentials(expectedOwner.username, ownerPassword))
      }
      .map(result => fail(s"Unexpected result received: [$result]"))
      .recoverWith {
        case NonFatal(e) =>
          e shouldBe an[AuthenticationFailure]
          e.getMessage.contains(s"Resource owner [${expectedOwner.username}] is not active") should be(true)
      }
  }

  it should "fail to authenticate missing resource owners" in {
    val store = ResourceOwnerStore(
      MemoryBackend[ResourceOwner.Id, ResourceOwner](name = s"owner-store-${java.util.UUID.randomUUID()}")
    )

    val expectedOwner = Generators.generateResourceOwner.copy(
      password = ownerSecret,
      salt = ownerSalt
    )

    val authenticator = new DefaultResourceOwnerAuthenticator(
      store = store.view,
      secretConfig = secretConfig
    )

    authenticator
      .authenticate(BasicHttpCredentials(expectedOwner.username, ownerPassword))
      .map(result => fail(s"Unexpected result received: [$result]"))
      .recoverWith {
        case NonFatal(e) =>
          e shouldBe an[AuthenticationFailure]
          e.getMessage.contains(s"Resource owner [${expectedOwner.username}] was not found") should be(true)
      }
  }

  private implicit val system: ActorSystem[SpawnProtocol.Command] = ActorSystem(
    Behaviors.setup(_ => SpawnProtocol()): Behavior[SpawnProtocol.Command],
    "DefaultResourceOwnerAuthenticatorSpec-oauth"
  )

  private implicit val untypedSystem: akka.actor.ActorSystem = system.classicSystem

  private implicit val secretConfig: Secret.ResourceOwnerConfig = Secret.ResourceOwnerConfig(
    algorithm = "PBKDF2WithHmacSHA512",
    iterations = 10000,
    derivedKeySize = 32,
    saltSize = 16,
    authenticationDelay = 50.millis
  )

  private val ownerPassword = "some-password"
  private val ownerSalt = "some-salt"
  private val ownerSecret = Secret.derive(ownerPassword, ownerSalt)
}
