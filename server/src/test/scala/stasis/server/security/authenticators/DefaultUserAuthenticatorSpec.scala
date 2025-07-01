package stasis.server.security.authenticators

import java.security.Key

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.control.NonFatal

import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.apache.pekko.http.scaladsl.model.headers.BasicHttpCredentials
import org.apache.pekko.http.scaladsl.model.headers.OAuth2BearerToken
import org.jose4j.jws.AlgorithmIdentifiers

import io.github.sndnv.layers.security.exceptions.AuthenticationFailure
import io.github.sndnv.layers.security.jwt.DefaultJwtAuthenticator
import io.github.sndnv.layers.security.keys.KeyProvider
import io.github.sndnv.layers.security.mocks.MockJwksGenerator
import io.github.sndnv.layers.security.mocks.MockJwtGenerator
import io.github.sndnv.layers.telemetry.TelemetryContext
import stasis.server.persistence.users.UserStore
import stasis.server.security.authenticators.DefaultUserAuthenticator
import stasis.test.specs.unit.AsyncUnitSpec
import stasis.test.specs.unit.core.telemetry.MockTelemetryContext
import stasis.server.persistence.users.MockUserStore
import stasis.test.specs.unit.shared.model.Generators

class DefaultUserAuthenticatorSpec extends AsyncUnitSpec { test =>
  "A DefaultUserAuthenticator" should "authenticate users with valid JWTs" in {
    val store = createStore()

    val expectedUser = Generators.generateUser

    val authenticator = new DefaultUserAuthenticator(
      store = store.view(),
      underlying = new DefaultJwtAuthenticator(
        provider = provider,
        audience = audience,
        identityClaim = "sub",
        expirationTolerance = 10.seconds
      )
    )

    val token = generateToken(subject = expectedUser.id.toString)

    store.manage().put(expectedUser).await
    authenticator
      .authenticate(
        credentials = OAuth2BearerToken(token = token)
      )
      .map { actualUser =>
        actualUser.id should be(expectedUser.id)
      }
  }

  it should "fail to authenticate users with unexpected credentials" in {
    val store = createStore()

    val expectedUser = Generators.generateUser

    val authenticator = new DefaultUserAuthenticator(
      store = store.view(),
      underlying = new DefaultJwtAuthenticator(
        provider = provider,
        audience = audience,
        identityClaim = "sub",
        expirationTolerance = 10.seconds
      )
    )

    store.manage().put(expectedUser).await
    authenticator
      .authenticate(
        credentials = BasicHttpCredentials(username = "some-username", password = "some-password")
      )
      .map { response =>
        fail(s"Unexpected response received: [$response]")
      }
      .recover { case NonFatal(e) =>
        e shouldBe an[AuthenticationFailure]
        e.getMessage should be("Unsupported user credentials provided: [Basic]")
      }
  }

  it should "fail to authenticate inactive users" in {
    val store = createStore()

    val expectedUser = Generators.generateUser.copy(active = false)

    val authenticator = new DefaultUserAuthenticator(
      store = store.view(),
      underlying = new DefaultJwtAuthenticator(
        provider = provider,
        audience = audience,
        identityClaim = "sub",
        expirationTolerance = 10.seconds
      )
    )

    val token = generateToken(subject = expectedUser.id.toString)

    store.manage().put(expectedUser).await
    authenticator
      .authenticate(
        credentials = OAuth2BearerToken(token = token)
      )
      .map { response =>
        fail(s"Unexpected response received: [$response]")
      }
      .recover { case NonFatal(e) =>
        e shouldBe an[AuthenticationFailure]
        e.getMessage should be(s"User [${expectedUser.id}] is not active")
      }
  }

  it should "fail to authenticate users with invalid JWTs" in {
    val store = createStore()

    val expectedUser = Generators.generateUser

    val invalidAudience = "invalid-audience"

    val authenticator = new DefaultUserAuthenticator(
      store = store.view(),
      underlying = new DefaultJwtAuthenticator(
        provider = provider,
        audience = invalidAudience,
        identityClaim = "sub",
        expirationTolerance = 10.seconds
      )
    )

    val token = generateToken(subject = expectedUser.id.toString)

    store.manage().put(expectedUser).await
    authenticator
      .authenticate(
        credentials = OAuth2BearerToken(token = token)
      )
      .map { response =>
        fail(s"Unexpected response received: [$response]")
      }
      .recover { case NonFatal(e) =>
        e shouldBe an[AuthenticationFailure]
        e.getMessage.contains(s"Expected $invalidAudience as an aud value") should be(true)
      }
  }

  it should "fail to authenticate users with invalid user IDs" in {
    val store = createStore()

    val expectedUser = Generators.generateUser

    val invalidUserId = "invalid-user-id"

    val authenticator = new DefaultUserAuthenticator(
      store = store.view(),
      underlying = new DefaultJwtAuthenticator(
        provider = provider,
        audience = audience,
        identityClaim = "sub",
        expirationTolerance = 10.seconds
      )
    )

    val token = generateToken(subject = invalidUserId)

    store.manage().put(expectedUser).await
    authenticator
      .authenticate(
        credentials = OAuth2BearerToken(token = token)
      )
      .map { response =>
        fail(s"Unexpected response received: [$response]")
      }
      .recover { case NonFatal(e) =>
        e shouldBe an[IllegalArgumentException]
        e.getMessage should be(s"Invalid UUID string: $invalidUserId")
      }
  }

  it should "fail to authenticate missing users" in {
    val store = createStore()

    val expectedUser = Generators.generateUser.copy(active = false)

    val authenticator = new DefaultUserAuthenticator(
      store = store.view(),
      underlying = new DefaultJwtAuthenticator(
        provider = provider,
        audience = audience,
        identityClaim = "sub",
        expirationTolerance = 10.seconds
      )
    )

    val token = generateToken(subject = expectedUser.id.toString)

    authenticator
      .authenticate(
        credentials = OAuth2BearerToken(token = token)
      )
      .map { response =>
        fail(s"Unexpected response received: [$response]")
      }
      .recover { case NonFatal(e) =>
        e shouldBe an[AuthenticationFailure]
        e.getMessage should be(s"User [${expectedUser.id}] not found")
      }
  }

  private implicit val system: ActorSystem[Nothing] = ActorSystem(
    Behaviors.ignore,
    "DefaultUserAuthenticatorSpec"
  )

  private implicit val telemetry: TelemetryContext = MockTelemetryContext()

  private val issuer = "some-issuer"
  private val audience = "some-audience"

  private val jwk = MockJwksGenerator.generateRandomRsaKey(keyId = Some("some-key"))

  private val provider = new KeyProvider {
    override def key(id: Option[String]): Future[Key] = Future.successful(jwk.getKey)
    override def issuer: String = test.issuer
    override def allowedAlgorithms: Seq[String] =
      Seq(
        AlgorithmIdentifiers.RSA_USING_SHA256,
        AlgorithmIdentifiers.RSA_USING_SHA384,
        AlgorithmIdentifiers.RSA_USING_SHA512
      )
  }

  private def generateToken(subject: String): String =
    MockJwtGenerator.generateJwt(
      issuer = issuer,
      audience = audience,
      subject = subject,
      signatureKey = jwk
    )

  private def createStore(): UserStore = MockUserStore()
}
