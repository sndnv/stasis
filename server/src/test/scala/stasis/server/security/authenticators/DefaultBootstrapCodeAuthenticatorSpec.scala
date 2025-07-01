package stasis.server.security.authenticators

import scala.util.control.NonFatal

import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.apache.pekko.http.scaladsl.model.headers.BasicHttpCredentials
import org.apache.pekko.http.scaladsl.model.headers.OAuth2BearerToken

import io.github.sndnv.layers.security.exceptions.AuthenticationFailure
import io.github.sndnv.layers.telemetry.TelemetryContext
import stasis.server.persistence.devices.MockDeviceBootstrapCodeStore
import stasis.server.security.authenticators.DefaultBootstrapCodeAuthenticator
import stasis.test.specs.unit.AsyncUnitSpec
import stasis.test.specs.unit.core.telemetry.MockTelemetryContext
import stasis.test.specs.unit.shared.model.Generators

class DefaultBootstrapCodeAuthenticatorSpec extends AsyncUnitSpec {
  "A DefaultBootstrapCodeAuthenticator" should "authenticate requests with valid bootstrap codes" in {
    val store = MockDeviceBootstrapCodeStore()

    val authenticator = new DefaultBootstrapCodeAuthenticator(store.manage())

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

    val authenticator = new DefaultBootstrapCodeAuthenticator(store.manage())

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

    val authenticator = new DefaultBootstrapCodeAuthenticator(store.manage())

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

  private implicit val system: ActorSystem[Nothing] = ActorSystem(
    Behaviors.ignore,
    "DefaultBootstrapCodeAuthenticatorSpec"
  )

  private implicit val telemetry: TelemetryContext = MockTelemetryContext()
}
