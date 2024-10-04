package stasis.test.specs.unit.identity.authentication.oauth

import scala.concurrent.duration._
import scala.util.control.NonFatal

import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.apache.pekko.http.scaladsl.model.headers.BasicHttpCredentials

import stasis.identity.authentication.oauth.DefaultClientAuthenticator
import stasis.identity.model.secrets.Secret
import stasis.layers.UnitSpec
import stasis.layers.security.exceptions.AuthenticationFailure
import stasis.test.specs.unit.identity.model.Generators
import stasis.test.specs.unit.identity.persistence.mocks.MockClientStore

class DefaultClientAuthenticatorSpec extends UnitSpec {
  "A DefaultClientAuthenticator" should "successfully authenticate clients" in {
    val store = MockClientStore()

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
    val store = MockClientStore()

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
      .recoverWith { case NonFatal(e) =>
        e shouldBe an[AuthenticationFailure]
        e.getMessage.contains(s"Client [${expectedClient.id}] is not active") should be(true)
      }
  }

  it should "fail to authenticate missing clients" in {
    val store = MockClientStore()

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
      .recoverWith { case NonFatal(e) =>
        e shouldBe an[AuthenticationFailure]
        e.getMessage.contains(s"Client [${expectedClient.id}] was not found") should be(true)
      }
  }

  it should "fail to authenticate clients with invalid IDs" in {
    val store = MockClientStore()

    val clientId = "some-client"

    val authenticator = new DefaultClientAuthenticator(
      store = store.view,
      secretConfig = secretConfig
    )

    authenticator
      .authenticate(BasicHttpCredentials(clientId, clientPassword))
      .map(result => fail(s"Unexpected result received: [$result]"))
      .recoverWith { case NonFatal(e) =>
        e shouldBe an[AuthenticationFailure]
        e.getMessage.contains(s"Invalid client identifier provided: [$clientId]") should be(true)
      }
  }

  private implicit val system: ActorSystem[Nothing] = ActorSystem(
    guardianBehavior = Behaviors.ignore,
    "DefaultClientAuthenticatorSpec"
  )

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
