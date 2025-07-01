package stasis.identity.authentication.oauth

import scala.concurrent.duration._
import scala.util.control.NonFatal

import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.apache.pekko.http.scaladsl.model.headers.BasicHttpCredentials

import stasis.identity.model.Generators
import stasis.identity.model.secrets.Secret
import stasis.identity.persistence.mocks.MockResourceOwnerStore
import io.github.sndnv.layers.testing.UnitSpec
import io.github.sndnv.layers.security.exceptions.AuthenticationFailure

class DefaultResourceOwnerAuthenticatorSpec extends UnitSpec {
  "A DefaultResourceOwnerAuthenticator" should "successfully authenticate resource owners" in {
    val store = MockResourceOwnerStore()

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
    val store = MockResourceOwnerStore()

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
      .recoverWith { case NonFatal(e) =>
        e shouldBe an[AuthenticationFailure]
        e.getMessage.contains(s"Resource owner [${expectedOwner.username}] is not active") should be(true)
      }
  }

  it should "fail to authenticate missing resource owners" in {
    val store = MockResourceOwnerStore()

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
      .recoverWith { case NonFatal(e) =>
        e shouldBe an[AuthenticationFailure]
        e.getMessage.contains(s"Resource owner [${expectedOwner.username}] was not found") should be(true)
      }
  }

  private implicit val system: ActorSystem[Nothing] = ActorSystem(
    guardianBehavior = Behaviors.ignore,
    "DefaultResourceOwnerAuthenticatorSpec-oauth"
  )

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
