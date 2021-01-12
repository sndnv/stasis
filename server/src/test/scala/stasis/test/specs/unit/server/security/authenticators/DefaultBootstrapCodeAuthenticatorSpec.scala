package stasis.test.specs.unit.server.security.authenticators

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorSystem, Behavior, SpawnProtocol}
import akka.http.scaladsl.model.headers.{BasicHttpCredentials, OAuth2BearerToken}
import stasis.core.security.exceptions.AuthenticationFailure
import stasis.server.security.authenticators.DefaultBootstrapCodeAuthenticator
import stasis.test.specs.unit.AsyncUnitSpec
import stasis.test.specs.unit.server.model.mocks.MockDeviceBootstrapCodeStore
import stasis.test.specs.unit.shared.model.Generators

import scala.util.control.NonFatal

class DefaultBootstrapCodeAuthenticatorSpec extends AsyncUnitSpec {
  "A DefaultBootstrapCodeAuthenticator" should "authenticate requests with valid bootstrap codes" in {
    val store = MockDeviceBootstrapCodeStore()

    val authenticator = new DefaultBootstrapCodeAuthenticator(store.view())

    val expectedCode = Generators.generateDeviceBootstrapCode

    store.manage().put(expectedCode).await

    authenticator
      .authenticate(
        credentials = OAuth2BearerToken(token = expectedCode.value)
      )
      .map { case (actualCode, actualUser) =>
        actualCode should be(expectedCode)
        actualUser.id should be(expectedCode.owner)
      }
  }

  it should "fail to authenticate requests with unexpected credentials" in {
    val store = MockDeviceBootstrapCodeStore()

    val authenticator = new DefaultBootstrapCodeAuthenticator(store.view())

    authenticator
      .authenticate(
        credentials = BasicHttpCredentials(username = "some-username", password = "some-password")
      )
      .map { response =>
        fail(s"Unexpected response received: [$response]")
      }
      .recover { case NonFatal(e) =>
        e shouldBe an[AuthenticationFailure]
        e.getMessage should be("Unsupported bootstrap credentials provided: [Basic]")
      }
  }

  it should "fail to authenticate requests with invalid bootstrap codes" in {
    val store = MockDeviceBootstrapCodeStore()

    val authenticator = new DefaultBootstrapCodeAuthenticator(store.view())

    authenticator
      .authenticate(
        credentials = OAuth2BearerToken(token = "invalid-code")
      )
      .map { response =>
        fail(s"Unexpected response received: [$response]")
      }
      .recover { case NonFatal(e) =>
        e shouldBe an[AuthenticationFailure]
        e.getMessage should be("Invalid bootstrap code provided")
      }
  }

  private implicit val system: ActorSystem[SpawnProtocol.Command] = ActorSystem(
    Behaviors.setup(_ => SpawnProtocol()): Behavior[SpawnProtocol.Command],
    "DefaultBootstrapCodeAuthenticatorSpec"
  )
}
